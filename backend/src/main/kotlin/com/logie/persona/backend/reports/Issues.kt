package com.logie.persona.backend.reports

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A failure to send to the app's GitHub repository as an issue: its title (with the build, what makes two the same failure), its body, and
 * the long logs after it as comments (an issue holds 65,536 characters). [issue] and [done] are where a send got to, kept in the file, so a
 * retry goes on from there and nothing is posted twice.
 */
data class Report(val title: String, val body: String, val comments: List<String> = emptyList(), val build: Int = 0, val issue: Int = 0, val done: Int = 0) {
    /** The same failure in the same build: numbers in the title (seconds, statuses, addresses) don't make it another. */
    val key: String get() = sha("$build " + title.replace(Regex("\\d+"), "#"))

    fun toJson(): JSONObject = JSONObject().put("title", title).put("body", body).put("comments", JSONArray(comments))
        .put("build", build).put("issue", issue).put("done", done)

    companion object {
        fun fromJson(o: JSONObject) = Report(
            o.getString("title"), o.getString("body"),
            o.optJSONArray("comments")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            o.optInt("build", 0), o.optInt("issue", 0), o.optInt("done", 0),
        )

        private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}

/** What one try to send the waiting reports came to. */
data class SendResult(val sent: List<Int>, val waiting: Int, val problem: String? = null)

/** GitHub's issues API, or a test's stand-in: a POST of [json] to [path], answering the status and the body. */
fun interface Http {
    fun post(path: String, token: String, json: String): Pair<Int, String>
}

object UrlHttp : Http {
    override fun post(path: String, token: String, json: String): Pair<Int, String> {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 20_000; c.readTimeout = 30_000; c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(json.toByteArray()) }
            val code = c.responseCode
            val text = runCatching { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() }.getOrNull().orEmpty()
            return code to text
        } finally { c.disconnect() }
    }
}

/** The reports waiting on the phone (a folder of JSON files) and their sending. Plain Kotlin: unit-tested. */
object Issues {
    /** Kept well under GitHub's 65,536 characters for an issue's or a comment's text. */
    const val MAX_ISSUE = 62_000
    const val CHUNK = 58_000
    private const val ONCE_PER_MS = 24 * 60 * 60 * 1000L

    /** Writes [report] to [dir], unless the same failure of the same build went in the last day. */
    fun queue(dir: File, report: Report, now: Long = System.currentTimeMillis(), force: Boolean = false): Boolean {
        dir.mkdirs()
        val name = if (force) "${report.key}-f$now.json" else "${report.key}.json"
        if (!force) {
            val sent = File(dir, "sent.txt")
            val recent = runCatching { sent.readLines() }.getOrDefault(emptyList())
                .mapNotNull { line -> line.split(' ').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }
                .filter { (_, at) -> now - at < ONCE_PER_MS }
            if (recent.any { it.first == report.key } || File(dir, name).exists()) return false
            sent.writeText((recent.map { "${it.first} ${it.second}" } + "${report.key} $now").joinToString("\n") + "\n")
        }
        File(dir, name).writeText(report.toJson().toString())
        return true
    }

    fun waiting(dir: File): List<File> = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.lastModified() }.orEmpty()

    /**
     * [text] as comments that fit, split at line ends, each with a heading ([title], part k of n) and the text in a code block. Only the last
     * [maxParts] parts when there are more: a log's end is where it went wrong.
     */
    fun chunks(title: String, text: String, maxParts: Int = 4): List<String> {
        if (text.isBlank()) return emptyList()
        val parts = ArrayList<String>(); val cur = StringBuilder()
        for (line in text.lineSequence()) {
            val l = if (line.length > CHUNK) line.take(CHUNK) else line
            if (cur.length + l.length + 1 > CHUNK) { parts += cur.toString(); cur.setLength(0) }
            cur.append(l).append('\n')
        }
        if (cur.isNotEmpty()) parts += cur.toString()
        val kept = parts.takeLast(maxParts); val skipped = parts.size - kept.size
        return kept.mapIndexed { i, p ->
            val head = if (parts.size == 1) "### $title" else "### $title (part ${i + 1 + skipped} of ${parts.size}${if (skipped > 0 && i == 0) "; the first $skipped left out" else ""})"
            "$head\n\n````\n${p.replace("````", "'```")}````"
        }
    }

    /**
     * Sends what waits in [dir]: each report opened as an issue in [repo] (labels bug and auto-report), then its comments, one at a time,
     * the file updated after each step and removed when all went. Stops at the first refusal or failure (the rest go next time).
     */
    fun send(dir: File, repo: String, token: String, http: Http = UrlHttp, pause: (Long) -> Unit = { Thread.sleep(it) }): SendResult {
        if (token.isEmpty()) return SendResult(emptyList(), waiting(dir).size, "no token in this build")
        val sent = ArrayList<Int>()
        for (file in waiting(dir)) {
            var r = runCatching { Report.fromJson(JSONObject(file.readText())) }.getOrElse { file.delete(); continue }
            try {
                if (r.issue == 0) {
                    var body = r.body.take(MAX_ISSUE)
                    var (code, text) = http.post("/repos/$repo/issues", token, issueJson(r.title, body))
                    if (code == 422 && body.length > 20_000) { body = body.take(20_000); val again = http.post("/repos/$repo/issues", token, issueJson(r.title, body)); code = again.first; text = again.second }
                    if (code !in 200..299) return SendResult(sent, waiting(dir).size, "GitHub answered $code ${text.take(160)}")
                    r = r.copy(issue = JSONObject(text).getInt("number"))
                    file.writeText(r.toJson().toString())
                }
                while (r.done < r.comments.size) {
                    pause(1_500)
                    val (code, text) = http.post("/repos/$repo/issues/${r.issue}/comments", token, JSONObject().put("body", r.comments[r.done].take(MAX_ISSUE)).toString())
                    if (code !in 200..299 && code != 422) return SendResult(sent, waiting(dir).size, "GitHub answered $code ${text.take(160)}")
                    r = r.copy(done = r.done + 1)
                    file.writeText(r.toJson().toString())
                }
                file.delete(); sent += r.issue
            } catch (e: Exception) {
                return SendResult(sent, waiting(dir).size, e.message ?: e.javaClass.simpleName)
            }
            pause(3_000)
        }
        return SendResult(sent, waiting(dir).size)
    }

    private fun issueJson(title: String, body: String) = JSONObject().put("title", title.take(250)).put("body", body).put("labels", JSONArray(listOf("bug", "auto-report"))).toString()

    /** What a report never carries: the account's name (given) and the store's name (the rule: no report names it; its libraries read "store_api"). */
    fun redact(text: String, accountName: String?): String {
        var t = text
        if (!accountName.isNullOrBlank() && accountName.length >= 3) t = t.replace(accountName, "<account>", ignoreCase = true)
        return t.replace(Regex("(?i)steam")) { m -> if (m.value[0].isUpperCase()) "Store" else "store" }
    }
}
