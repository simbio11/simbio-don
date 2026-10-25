package com.simbio.don.app

import com.simbio.don.app.ai.AiModel
import com.simbio.don.app.ai.AiReply
import com.simbio.don.app.ai.AiTasks
import com.simbio.don.app.ai.ClaudeBackend
import com.simbio.don.budget.Category
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.time.LocalDate

/** 0.17: 공식 SDK가 실제로 보내는 요청 모양과 답 읽기 — 이 컴퓨터 안의 가짜 서버로 (인터넷·키 없이) */
@RunWith(RobolectricTestRunner::class)
class ClaudeBackendTest {
    private lateinit var server: ServerSocket
    private var status = 200
    private var reply = ""
    @Volatile private var lastBody = ""
    @Volatile private var lastHeaders = mapOf<String, String>()

    /** 아주 작은 HTTP 서버: 요청 하나 읽고 [reply]로 답한 뒤 연결을 닫는다 */
    @Before
    fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                sock.use { so ->
                    val input = so.getInputStream().buffered()
                    fun line(): String {
                        val sb = StringBuilder()
                        while (true) {
                            val c = input.read()
                            if (c < 0 || c == '\n'.code) break
                            if (c != '\r'.code) sb.append(c.toChar())
                        }
                        return sb.toString()
                    }
                    line() // 요청 줄
                    val headers = HashMap<String, String>()
                    while (true) {
                        val h = line()
                        if (h.isEmpty()) break
                        val i = h.indexOf(':')
                        if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                    }
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val n = input.read(body, read, len - read)
                        if (n < 0) break
                        read += n
                    }
                    lastHeaders = headers
                    lastBody = body.decodeToString()
                    val bytes = reply.toByteArray()
                    val out = so.getOutputStream()
                    out.write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private val url get() = "http://127.0.0.1:${server.localPort}"

    private fun message(stop: String, text: String, extra: String = "") =
        """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"text","text":${JSONObject.quote(text)}}],""" +
            """"stop_reason":"$stop","stop_sequence":null,"usage":{"input_tokens":812,"output_tokens":64}$extra}"""

    @Test
    fun `말로 적기 요청은 JSON 모양·짧게 생각·다른 모델로 다시 하기를 담아 보낸다`() = runBlocking {
        reply = message("end_turn", """{"date":"2026-10-24","amount":80000}""")
        val req = AiTasks.spokenRequest("어제 고깃집 8만원", LocalDate.of(2026, 10, 25), Category.spending + Category.incomes)
        val r = ClaudeBackend("sk-ant-test", url).send(AiModel.OPUS, req)
        assertTrue(r.toString(), r is AiReply.Ok)
        r as AiReply.Ok
        assertEquals("""{"date":"2026-10-24","amount":80000}""", r.text)
        assertEquals(812L, r.inputTokens)
        assertEquals(64L, r.outputTokens)

        val body = JSONObject(lastBody)
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        val format = body.getJSONObject("output_config").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertEquals(10, format.getJSONObject("schema").getJSONArray("required").length())
        val content = body.getJSONArray("messages").getJSONObject(0).get("content")
        val text = if (content is org.json.JSONArray) content.getJSONObject(0).getString("text") else content.toString()
        assertEquals("어제 고깃집 8만원", text)
        assertFalse(body.has("temperature"))
        assertFalse(body.has("thinking"))
        assertEquals("sk-ant-test", lastHeaders["x-api-key"])
        assertTrue(lastHeaders["anthropic-beta"].orEmpty().contains("server-side-fallback-2026-07-01"))
    }

    @Test
    fun `Haiku는 다른 모델로 다시 하기 없이, 글 답은 그대로`() = runBlocking {
        reply = message("end_turn", "하루 2만원씩 쓰면 돼요.")
        val r = ClaudeBackend("sk-ant-test", url).send(AiModel.HAIKU, AiTasks.askRequest("[이번 달] ...", "얼마 써도 돼요?"))
        assertEquals("하루 2만원씩 쓰면 돼요.", (r as AiReply.Ok).text)
        val body = JSONObject(lastBody)
        assertEquals("claude-haiku-5-5", body.getString("model"))
        assertFalse(body.has("fallbacks"))
        assertFalse(body.has("output_config"))
        assertFalse(lastHeaders["anthropic-beta"].orEmpty().contains("server-side-fallback"))
    }

    @Test
    fun `거절·잘림·키 오류를 알린다`() = runBlocking {
        reply = message("refusal", "", ""","stop_details":{"type":"refusal","category":"cyber","explanation":"답할 수 없어요"}""")
        val refused = ClaudeBackend("sk-ant-test", url).send(AiModel.OPUS, AiTasks.askRequest("s", "q"))
        assertTrue(refused.toString(), refused is AiReply.Refused)

        reply = message("max_tokens", """{"headline":"잘""")
        val cut = ClaudeBackend("sk-ant-test", url).send(AiModel.OPUS, AiTasks.coachRequest("s"))
        assertTrue(cut.toString(), cut is AiReply.Failed)

        status = 401
        reply = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        val bad = ClaudeBackend("sk-ant-wrong", url).send(AiModel.OPUS, AiTasks.askRequest("s", "q"))
        assertEquals("API 키가 맞지 않아요. 설정에서 다시 넣어 주세요", (bad as AiReply.Failed).message)
    }
}
