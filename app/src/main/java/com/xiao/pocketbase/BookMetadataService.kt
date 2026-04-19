package com.xiao.pocketbase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipFile

enum class SearchSource(val label: String) {
    AUTO("AUTO"),
    BANGUMI("BANGUMI"),
    DOUBAN("DOUBAN"),
    BAIDU("BAIDU"),
    GOOGLE_BOOKS("GOOGLE_BOOKS"),
    OPEN_LIBRARY("OPEN_LIBRARY")
}

data class BookMetadata(
    val title: String,
    val author: String = I18n.t("label_unknown_author"),
    val summary: String = I18n.t("label_no_summary"),
    val coverUrl: String? = null,
    val isMatched: Boolean = false
)

object BookMetadataService {

    private val userAgents = listOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    private const val NETWORK_TIMEOUT = 5000 
    // 垃圾词库，用于在搜索前过滤污染源
    private val garbageWords = listOf("未知", "未知作者", "佚名", "None", "Unknown")

    suspend fun getIntegratedMetadata(
        file: File, 
        source: SearchSource = SearchSource.AUTO,
        onProgress: (String) -> Unit = {}
    ): BookMetadata = withContext(Dispatchers.IO) {
        
        onProgress(I18n.t("log_anatomizing", file.name))
        
        var internalTitle: String? = null
        var internalAuthor: String? = null
        var isOmnibus = false

        // 1. 深度采样：分析文件头或 EPUB 内部
        if (file.extension.lowercase() == "epub") {
            val epubRes = extractEpubInternalWithStats(file)
            internalTitle = epubRes?.first?.first
            internalAuthor = epubRes?.first?.second
            isOmnibus = epubRes?.second ?: false
        } else if (file.extension.lowercase() == "txt") {
            // TXT 采样：读取前 500 字符寻找《书名》或 作者：
            val sample = readTxtHeader(file)
            internalTitle = extractFromText(sample, Regex("《(.*?)》"))
            internalAuthor = extractFromText(sample, Regex("(?:作者|By)[:：]\\s*(.*)"))
        }

        // 2. 初始数据提纯
        val rawTitle = internalTitle ?: file.nameWithoutExtension
        val pureInternalTitle = TitleCleaner.clean(rawTitle)
        val sourceAuthor = internalAuthor ?: extractAuthorFromFileName(file.name) ?: I18n.t("label_unknown_author")
        
        // 3. 搜索预处理（脱水策略）
        // 过滤“未知作者”等词条，防止干扰搜索引擎
        val searchAuthor = if (garbageWords.any { sourceAuthor.contains(it) }) "" else sourceAuthor
        // 核心：搜索词去掉括号内容（如卷数 (7) ），大幅提升 Bangumi 等命中率
        val searchQuery = pureInternalTitle.replace(Regex("[\\(（\\[【].*?[\\)）\\] 】]"), "").trim().take(30)

        val sourceLabel = I18n.t("source_${source.name.lowercase()}")
        onProgress(I18n.t("log_searching", sourceLabel, searchQuery))

        // 4. 执行多源检索
        val isWestern = Locale.getDefault().language == "en"
        val result: BookMetadata? = when (source) {
            SearchSource.BANGUMI -> fetchFromBangumi(searchQuery, searchAuthor)
            SearchSource.DOUBAN -> fetchFromDouban(searchQuery, searchAuthor)
            SearchSource.GOOGLE_BOOKS -> fetchFromGoogleBooks(searchQuery, searchAuthor)
            SearchSource.OPEN_LIBRARY -> fetchFromOpenLibrary(searchQuery, searchAuthor)
            SearchSource.BAIDU -> fetchFromBackupSource(searchQuery, searchAuthor)
            SearchSource.AUTO -> {
                if (isWestern) {
                    fetchFromGoogleBooks(searchQuery, searchAuthor) ?: fetchFromOpenLibrary(searchQuery, searchAuthor)
                } else {
                    fetchFromBangumi(searchQuery, searchAuthor) ?: fetchFromDouban(searchQuery, searchAuthor)
                }
            }
        }

        // 5. 覆盖策略：只有真正匹配到（isMatched）才覆盖标题，否则保持原样
        var finalTitle = if (result != null && result.isMatched) result.title else pureInternalTitle
        
        val omnibusTag = I18n.t("msg_omnibus")
        if (isOmnibus && !finalTitle.contains(omnibusTag)) {
            finalTitle += " $omnibusTag"
        }
        
        onProgress(I18n.t("log_complete"))
        
        return@withContext BookMetadata(
            title = finalTitle,
            // 匹配成功则更正作者，否则沿用本地发现的名字（哪怕是“未知”）
            author = if (result?.isMatched == true) result.author else sourceAuthor,
            summary = result?.summary ?: I18n.t("label_no_summary"),
            coverUrl = result?.coverUrl,
            isMatched = result?.isMatched == true
        )
    }

    // --- TXT 采样逻辑 ---
    private fun readTxtHeader(file: File): String {
        return try {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val charArray = CharArray(500)
                val len = reader.read(charArray)
                if (len > 0) String(charArray, 0, len) else ""
            }
        } catch (e: Exception) { "" }
    }

    private fun extractFromText(text: String, regex: Regex): String? {
        return regex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..30 }
    }

    // --- 网络抓取与内部解剖逻辑 ---

    private fun extractEpubInternalWithStats(file: File): Pair<Pair<String, String>, Boolean>? {
        return try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerDoc = Jsoup.parse(zip.getInputStream(containerEntry).bufferedReader().readText(), "", Parser.xmlParser())
                val opfPath = containerDoc.select("rootfile").attr("full-path")
                if (opfPath.isEmpty()) return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfDoc = Jsoup.parse(zip.getInputStream(opfEntry).bufferedReader().readText(), "", Parser.xmlParser())
                val isOmnibus = opfDoc.select("spine itemref").size > 50
                var title = opfDoc.select("dc|title").text().ifBlank { opfDoc.select("title").text() }
                var author = opfDoc.select("dc|creator").text().ifBlank { opfDoc.select("creator").text() }
                Pair(Pair(title.trim(), author.trim().ifBlank { I18n.t("label_unknown_author") }), isOmnibus)
            }
        } catch (e: Exception) { null }
    }

    private suspend fun fetchFromGoogleBooks(query: String, authorHint: String): BookMetadata? {
        return try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=intitle:${URLEncoder.encode(query, "UTF-8")}" + 
                      if (authorHint.isNotEmpty()) "+inauthor:${URLEncoder.encode(authorHint, "UTF-8")}" else ""
            val jsonResponse = Jsoup.connect(url).ignoreContentType(true).timeout(NETWORK_TIMEOUT).execute().body()
            val items = JSONObject(jsonResponse).optJSONArray("items") ?: return null
            val vol = items.getJSONObject(0).getJSONObject("volumeInfo")
            val coverUrl = if (vol.has("imageLinks")) {
                val thumb = vol.getJSONObject("imageLinks").optString("thumbnail", "")
                if (thumb.isNotEmpty()) thumb.replace("http://", "https://") else null
            } else null
            BookMetadata(vol.optString("title", query), if (vol.has("authors")) vol.getJSONArray("authors").getString(0) else authorHint, 
                vol.optString("description", "").take(300), coverUrl, true)
        } catch (e: Exception) { null }
    }

    private suspend fun fetchFromBangumi(query: String, authorHint: String): BookMetadata? {
        return try {
            val url = "https://bgm.tv/subject_search/${URLEncoder.encode(query, "UTF-8")}?cat=1"
            val doc = Jsoup.connect(url).userAgent(userAgents.random()).timeout(NETWORK_TIMEOUT).get()
            val target = doc.select("#browserItemList li").firstOrNull() ?: return null
            val detailDoc = Jsoup.connect("https://bgm.tv" + target.select("h3 a").attr("href")).get()
            BookMetadata(
                title = detailDoc.select("h1.nameSingle a").text().trim(),
                author = detailDoc.select(".infobox li:contains(作者)").text().replace("作者:", "").trim().ifBlank { authorHint },
                summary = detailDoc.select("#subject_summary").text().trim().take(300),
                coverUrl = detailDoc.select(".infobox img.cover").attr("src").let { if (it.startsWith("//")) "https:$it" else it },
                isMatched = true
            )
        } catch (e: Exception) { null }
    }

    private suspend fun fetchFromDouban(query: String, author: String): BookMetadata? {
        return try {
            val url = "https://www.douban.com/search?q=${URLEncoder.encode(query, "UTF-8")}" + 
                      if (author.isNotEmpty()) "+${URLEncoder.encode(author, "UTF-8")}" else ""
            val doc = Jsoup.connect(url).userAgent(userAgents.random()).timeout(NETWORK_TIMEOUT).get()
            val target = doc.select("div.result").firstOrNull { it.text().contains("[图书]") } ?: return null
            val detailDoc = Jsoup.connect(target.select("a").attr("href")).get()
            BookMetadata(
                title = detailDoc.select("h1 span").firstOrNull()?.text() ?: query,
                author = detailDoc.select("a[href*=author]").firstOrNull()?.text() ?: author,
                summary = detailDoc.select("div#link-report .all").text().take(300),
                isMatched = true
            )
        } catch (e: Exception) { null }
    }

    private suspend fun fetchFromOpenLibrary(query: String, authorHint: String): BookMetadata? {
        return try {
            val url = "https://openlibrary.org/search.json?q=${URLEncoder.encode(query, "UTF-8")}"
            val docs = JSONObject(Jsoup.connect(url).ignoreContentType(true).execute().body()).optJSONArray("docs") ?: return null
            val first = docs.getJSONObject(0)
            val coverI = first.optInt("cover_i", -1)
            BookMetadata(first.optString("title", query), if (first.has("author_name")) first.getJSONArray("author_name").getString(0) else authorHint, 
                I18n.t("label_no_summary"), if (coverI != -1) "https://covers.openlibrary.org/b/id/$coverI-L.jpg" else null, true)
        } catch (e: Exception) { null }
    }

    private suspend fun fetchFromBackupSource(keyword: String, author: String): BookMetadata? {
        return try {
            val doc = Jsoup.connect("https://www.baidu.com/s?wd=${URLEncoder.encode(keyword, "UTF-8")}").timeout(NETWORK_TIMEOUT).get()
            val text = doc.select("div.c-abstract").firstOrNull()?.text() ?: ""
            if (text.isNotEmpty()) BookMetadata(title = keyword, author = author, summary = text.take(150), isMatched = true) else null
        } catch (e: Exception) { null }
    }

    private fun extractAuthorFromFileName(name: String): String? {
        val tagNoise = listOf("角川", "文库", "epub", "txt", "完结", "全集", "合集", "精校", "校对", "重排", "修复", "附件", "下载", "漫画", "SF", "轻之国度")
        val candidates = Regex("[\\[【(（](.*?)[\\] 】）)]").findAll(name).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        return candidates.firstOrNull { candidate ->
            val isNotNoise = tagNoise.none { noise -> candidate.contains(noise, ignoreCase = true) }
            val isProperLength = candidate.length in 2..20 
            val isNotPureNumber = candidate.toIntOrNull() == null
            isNotNoise && isProperLength && isNotPureNumber
        }
    }
}