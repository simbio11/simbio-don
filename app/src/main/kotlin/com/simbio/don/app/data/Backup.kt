package com.simbio.don.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * 백업 파일 (JSON). 이 폰 안에서 사용자가 고른 곳에 저장하고, 어디로도 보내지 않는다.
 * 확인함(정리 안 한 결제 알림)도 담아 복원해도 잃지 않게 한다. 입금 기록·알림 보낸 앱 목록은 담지 않는다.
 */
object Backup {
    const val FORMAT = "dont-backup"
    const val VERSION = 1

    data class Data(
        val settings: List<SettingsEntity>,
        val fixed: List<FixedExpenseEntity>,
        val cycles: List<CycleEntity>,
        val entries: List<EntryEntity>,
        val reviews: List<ReviewItemEntity> = emptyList(),
        val rules: List<AppRuleEntity>,
        val settled: List<SettledWeekEntity>,
        val categoryRules: List<CategoryRuleEntity>,
        val mocks: List<MockPurchaseEntity>,
        val houses: List<HouseEntity>,
        val categoryBudgets: List<CategoryBudgetEntity>,
        val goals: List<SavingsGoalEntity>,
        val accounts: List<AccountEntity>,
    )

    class BadBackup(message: String) : Exception(message)

    fun encode(d: Data, now: LocalDateTime): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", now.toString())
        put("settings", arr(d.settings) { o, x ->
            o.put("payDay", x.payDay); o.put("settlementDay", x.settlementDay); o.put("savings", x.savings)
            o.putOpt("pendingSettlementDay", x.pendingSettlementDay)
        })
        put("fixed", arr(d.fixed) { o, x ->
            o.put("id", x.id); o.put("name", x.name); o.put("amount", x.amount); o.putOpt("day", x.day)
            o.putOpt("accountId", x.accountId); o.put("category", x.category); o.putOpt("lastDueEpochDay", x.lastDueEpochDay)
        })
        put("cycles", arr(d.cycles) { o, x ->
            o.put("nominal", x.nominal); o.put("salary", x.salary); o.put("startEpochDay", x.startEpochDay)
            o.put("settlementDay", x.settlementDay); o.put("savings", x.savings); o.put("fixedTotal", x.fixedTotal)
            o.putOpt("budgetOverride", x.budgetOverride); o.put("salarySource", x.salarySource); o.put("noticeShown", x.noticeShown)
        })
        put("entries", arr(d.entries) { o, x ->
            o.put("id", x.id); o.put("epochDay", x.epochDay); o.put("amount", x.amount); o.put("label", x.label)
            o.put("type", x.type); o.put("source", x.source); o.putOpt("packageName", x.packageName)
            o.putOpt("postedAtEpochSecond", x.postedAtEpochSecond); o.put("category", x.category); o.put("memo", x.memo)
            o.putOpt("accountId", x.accountId); o.putOpt("toAccountId", x.toAccountId); o.putOpt("minuteOfDay", x.minuteOfDay)
            o.putOpt("fixedId", x.fixedId)
        })
        put("reviews", arr(d.reviews) { o, x ->
            o.put("id", x.id); o.put("packageName", x.packageName); o.put("action", x.action); o.put("amount", x.amount)
            o.put("label", x.label); o.put("postedAtEpochSecond", x.postedAtEpochSecond); o.put("rawText", x.rawText)
            o.put("reason", x.reason); o.putOpt("relatedEntryId", x.relatedEntryId)
        })
        put("rules", arr(d.rules) { o, x ->
            o.put("packageName", x.packageName); o.put("displayName", x.displayName); o.put("enabled", x.enabled)
            o.put("keywordRules", x.keywordRules)
        })
        put("settled", arr(d.settled) { o, x ->
            o.put("nominal", x.nominal); o.put("weekIndex", x.weekIndex); o.put("settledAtEpochSecond", x.settledAtEpochSecond)
            o.put("auto", x.auto); o.put("budget", x.budget); o.put("spent", x.spent); o.put("savedBps", x.savedBps)
            o.put("overspent", x.overspent)
        })
        put("categoryRules", arr(d.categoryRules) { o, x -> o.put("labelKey", x.labelKey); o.put("category", x.category) })
        put("mocks", arr(d.mocks) { o, x ->
            o.put("id", x.id); o.put("name", x.name); o.put("price", x.price); o.put("kind", x.kind); o.putOpt("store", x.store)
            o.put("epochDay", x.epochDay); o.put("status", x.status); o.put("createdAtEpochSecond", x.createdAtEpochSecond)
        })
        put("houses", arr(d.houses) { o, x -> o.put("level", x.level); o.put("characterName", x.characterName); o.put("look", x.look) })
        put("categoryBudgets", arr(d.categoryBudgets) { o, x -> o.put("category", x.category); o.put("amount", x.amount) })
        put("goals", arr(d.goals) { o, x ->
            o.put("id", x.id); o.put("name", x.name); o.put("target", x.target); o.put("icon", x.icon)
            o.put("createdAtEpochDay", x.createdAtEpochDay); o.put("sortOrder", x.sortOrder)
            o.putOpt("achievedAtEpochDay", x.achievedAtEpochDay); o.put("used", x.used)
        })
        put("accounts", arr(d.accounts) { o, x ->
            o.put("id", x.id); o.put("name", x.name); o.put("type", x.type); o.put("opening", x.opening)
            o.put("sortOrder", x.sortOrder); o.put("hidden", x.hidden); o.putOpt("packageName", x.packageName)
        })
    }.toString()

    fun decode(json: String): Data {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BadBackup("백업 파일이 아니에요")
        }
        if (root.optString("format") != FORMAT) throw BadBackup("돈't 백업 파일이 아니에요")
        if (root.optInt("version") > VERSION) throw BadBackup("더 새 버전 앱에서 만든 백업이에요. 앱을 업데이트해 주세요")
        return try {
            Data(
                settings = list(root, "settings") { o ->
                    SettingsEntity(payDay = o.getInt("payDay"), settlementDay = o.getInt("settlementDay"), savings = o.getLong("savings"),
                        pendingSettlementDay = o.intOrNull("pendingSettlementDay"))
                },
                fixed = list(root, "fixed") { o ->
                    FixedExpenseEntity(o.getLong("id"), o.getString("name"), o.getLong("amount"), o.intOrNull("day"),
                        o.longOrNull("accountId"), o.optString("category", "BILLS"), o.longOrNull("lastDueEpochDay"))
                },
                cycles = list(root, "cycles") { o ->
                    CycleEntity(o.getString("nominal"), o.getLong("salary"), o.getLong("startEpochDay"), o.getInt("settlementDay"),
                        o.getLong("savings"), o.getLong("fixedTotal"), o.longOrNull("budgetOverride"),
                        o.optString("salarySource", SalarySource.MANUAL.name), o.optBoolean("noticeShown", true))
                },
                entries = list(root, "entries") { o ->
                    EntryEntity(o.getLong("id"), o.getLong("epochDay"), o.getLong("amount"), o.getString("label"), o.getString("type"),
                        o.getString("source"), o.strOrNull("packageName"), o.longOrNull("postedAtEpochSecond"),
                        o.optString("category", "ETC"), o.optString("memo", ""), o.longOrNull("accountId"), o.longOrNull("toAccountId"),
                        o.intOrNull("minuteOfDay"), o.longOrNull("fixedId"))
                },
                reviews = list(root, "reviews") { o ->
                    ReviewItemEntity(o.getLong("id"), o.getString("packageName"), o.getString("action"), o.getLong("amount"),
                        o.getString("label"), o.getLong("postedAtEpochSecond"), o.getString("rawText"), o.getString("reason"),
                        o.longOrNull("relatedEntryId"))
                },
                rules = list(root, "rules") { o ->
                    AppRuleEntity(o.getString("packageName"), o.getString("displayName"), o.getBoolean("enabled"), o.getString("keywordRules"))
                },
                settled = list(root, "settled") { o ->
                    SettledWeekEntity(o.getString("nominal"), o.getInt("weekIndex"), o.getLong("settledAtEpochSecond"), o.getBoolean("auto"),
                        o.optLong("budget"), o.optLong("spent"), o.optInt("savedBps", -1), o.optLong("overspent"))
                },
                categoryRules = list(root, "categoryRules") { o -> CategoryRuleEntity(o.getString("labelKey"), o.getString("category")) },
                mocks = list(root, "mocks") { o ->
                    MockPurchaseEntity(o.getLong("id"), o.getString("name"), o.getLong("price"), o.getString("kind"), o.strOrNull("store"),
                        o.getLong("epochDay"), o.getString("status"), o.getLong("createdAtEpochSecond"))
                },
                houses = list(root, "houses") { o -> HouseEntity(level = o.getString("level"), characterName = o.getString("characterName"), look = o.optString("look", "")) },
                categoryBudgets = list(root, "categoryBudgets") { o -> CategoryBudgetEntity(o.getString("category"), o.getLong("amount")) },
                goals = list(root, "goals") { o ->
                    SavingsGoalEntity(o.getLong("id"), o.getString("name"), o.getLong("target"), o.getString("icon"),
                        o.getLong("createdAtEpochDay"), o.getInt("sortOrder"), o.longOrNull("achievedAtEpochDay"), o.getBoolean("used"))
                },
                accounts = list(root, "accounts") { o ->
                    AccountEntity(o.getLong("id"), o.getString("name"), o.getString("type"), o.getLong("opening"), o.getInt("sortOrder"),
                        o.getBoolean("hidden"), o.strOrNull("packageName"))
                },
            ).also {
                if (it.settings.isEmpty() || it.cycles.isEmpty()) throw BadBackup("백업에 설정이 없어요")
            }
        } catch (e: BadBackup) {
            throw e
        } catch (e: Exception) {
            throw BadBackup("백업 파일이 망가졌어요")
        }
    }

    private fun <T> arr(items: List<T>, fill: (JSONObject, T) -> Unit): JSONArray =
        JSONArray().apply { items.forEach { put(JSONObject().also { o -> fill(o, it) }) } }

    private fun <T> list(root: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { read(a.getJSONObject(it)) }
    }

    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.strOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
}
