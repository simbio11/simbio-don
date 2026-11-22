package com.simbio.don.app

import com.simbio.don.app.ai.AgentReply
import com.simbio.don.app.ai.AgentRequest
import com.simbio.don.app.ai.AiReply
import com.simbio.don.app.ai.AiTasks
import com.simbio.don.app.ai.GeminiBackend
import com.simbio.don.app.ai.OpenAiBackend
import com.simbio.don.app.ai.ToolOutcome
import com.simbio.don.app.ai.ToolSpec
import com.simbio.don.app.ai.Turn
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

/** 0.36: Gemini·OpenAI(호환) — 이 컴퓨터 안의 가짜 서버로 요청 모양과 답 읽기 (인터넷·키 없이) */
@RunWith(RobolectricTestRunner::class)
class OtherBackendsTest {
    private lateinit var server: ServerSocket
    private var status = 200
    private var reply = ""
    @Volatile private var lastPath = ""
    @Volatile private var lastBody = ""
    @Volatile private var lastHeaders = mapOf<String, String>()

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
                    val request = line()
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
                    lastPath = request.split(" ").getOrElse(1) { "" }
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
    private val tools = listOf(ToolSpec("search_entries", "기록 찾기", mapOf("text" to mapOf("type" to "string")), listOf("text")))

    // ── Gemini ──

    @Test
    fun `Gemini - JSON 답을 받고 키는 헤더로, 생각 글은 빼고 읽는다`() = runBlocking {
        reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"생각 중","thought":true},{"text":"```json\n{\"headline\":\"좋아요\"}\n```"}]},"finishReason":"STOP"}],""" +
            """"usageMetadata":{"promptTokenCount":50,"candidatesTokenCount":10}}"""
        val r = GeminiBackend("AIzaTest", url).send("gemini-2.5-flash", AiTasks.coachRequest("[이번 달] 수입 0원"))
        assertTrue(r.toString(), r is AiReply.Ok)
        assertEquals("""{"headline":"좋아요"}""", (r as AiReply.Ok).text)
        assertEquals(50L, r.inputTokens)
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", lastPath)
        assertEquals("AIzaTest", lastHeaders["x-goog-api-key"])
        val body = JSONObject(lastBody)
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        assertTrue(body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text").contains("JSON 스키마"))
        assertEquals("user", body.getJSONArray("contents").getJSONObject(0).getString("role"))
    }

    @Test
    fun `Gemini - 도구 부름을 읽고 받은 답(서명 포함)과 결과를 돌려 보낸다`() = runBlocking {
        reply = """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"search_entries","args":{"text":"김밥"}},"thoughtSignature":"SIG123"}]},"finishReason":"STOP"}]}"""
        val b = GeminiBackend("AIzaTest", url)
        val q = Turn.User("김밥 얼마?")
        val m = (b.turn("gemini-2.5-pro", AgentRequest("sys", listOf(q), tools)) as AgentReply.Ok).model
        val call = m.calls.single()
        assertEquals("search_entries", call.name)
        assertEquals("김밥", call.args.getString("text"))
        val decl = JSONObject(lastBody).getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals("search_entries", decl.getString("name"))
        assertEquals("object", decl.getJSONObject("parameters").getString("type"))

        reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"9,000원 썼어요"}]},"finishReason":"STOP"}]}"""
        val r2 = b.turn("gemini-2.5-pro", AgentRequest("sys", listOf(q, m, Turn.Results(listOf(ToolOutcome(call.id, call.name, """{"count":2}""")))), tools))
        assertEquals("9,000원 썼어요", (r2 as AgentReply.Ok).model.text)
        val contents = JSONObject(lastBody).getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("SIG123", contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"))
        val fr = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        assertEquals("search_entries", fr.getString("name"))
        assertEquals("""{"count":2}""", fr.getJSONObject("response").getString("result"))
    }

    @Test
    fun `Gemini - 막히면 거절, 키가 틀리면 키 안내`() = runBlocking {
        reply = """{"candidates":[{"finishReason":"SAFETY"}]}"""
        assertTrue(GeminiBackend("k", url).send("gemini-2.5-flash", AiTasks.askRequest("s", "q")) is AiReply.Refused)
        status = 400
        reply = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        val bad = GeminiBackend("k", url).send("gemini-2.5-flash", AiTasks.askRequest("s", "q"))
        assertEquals("API 키가 맞지 않아요. 설정에서 다시 넣어 주세요", (bad as AiReply.Failed).message)
    }

    // ── OpenAI · 호환 ──

    @Test
    fun `OpenAI - Bearer 키, 새 토큰 이름, JSON 답`() = runBlocking {
        reply = """{"choices":[{"message":{"role":"assistant","content":"{\"headline\":\"좋아요\"}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":30,"completion_tokens":5}}"""
        val r = OpenAiBackend("sk-test", url, official = true).send("gpt-5-mini", AiTasks.coachRequest("s"))
        assertEquals("""{"headline":"좋아요"}""", (r as AiReply.Ok).text)
        assertEquals(30L, r.inputTokens)
        assertEquals("/chat/completions", lastPath)
        assertEquals("Bearer sk-test", lastHeaders["authorization"])
        val body = JSONObject(lastBody)
        assertEquals("gpt-5-mini", body.getString("model"))
        assertTrue(body.has("max_completion_tokens"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `호환 서비스 - 주소 그대로, max_tokens, 도구 부름과 tool 결과`() = runBlocking {
        reply = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_9","type":"function","function":{"name":"search_entries","arguments":"{\"text\":\"김밥\"}"}}]},"finish_reason":"tool_calls"}]}"""
        val b = OpenAiBackend("sk-or", "$url/api/v1/", official = false)
        val q = Turn.User("김밥 얼마?")
        val m = (b.turn("deepseek/deepseek-chat", AgentRequest("sys", listOf(q), tools)) as AgentReply.Ok).model
        assertEquals("/api/v1/chat/completions", lastPath)
        val body1 = JSONObject(lastBody)
        assertTrue(body1.has("max_tokens"))
        assertFalse(body1.has("max_completion_tokens"))
        assertEquals("search_entries", body1.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("call_9", m.calls.single().id)
        assertEquals("김밥", m.calls.single().args.getString("text"))

        reply = """{"choices":[{"message":{"role":"assistant","content":"9,000원이에요"},"finish_reason":"stop"}]}"""
        b.turn("deepseek/deepseek-chat", AgentRequest("sys", listOf(q, m, Turn.Results(listOf(ToolOutcome("call_9", "search_entries", "{}")))), tools))
        val msgs = JSONObject(lastBody).getJSONArray("messages")
        assertEquals(4, msgs.length())
        assertEquals("call_9", msgs.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("tool", msgs.getJSONObject(3).getString("role"))
        assertEquals("call_9", msgs.getJSONObject(3).getString("tool_call_id"))
    }

    @Test
    fun `OpenAI - 거절과 키 오류`() = runBlocking {
        reply = """{"choices":[{"message":{"role":"assistant","content":null,"refusal":"도울 수 없어요"},"finish_reason":"stop"}]}"""
        assertTrue(OpenAiBackend("k", url, true).send("gpt-5", AiTasks.askRequest("s", "q")) is AiReply.Refused)
        status = 401
        reply = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""
        val bad = OpenAiBackend("k", url, true).send("gpt-5", AiTasks.askRequest("s", "q"))
        assertEquals("API 키가 맞지 않아요. 설정에서 다시 넣어 주세요", (bad as AiReply.Failed).message)
    }
}
