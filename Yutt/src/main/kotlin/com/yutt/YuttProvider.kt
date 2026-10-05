package com.yutt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Base64

class YuttProvider : MainAPI() {
    override var mainUrl = "https://www.yuthanhthien.top"
    override var name = "yutt"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama
    )

    // Blog ID chính thức trên Google Blogger để kết nối trực tiếp không bao giờ bị chặn hay Connection Reset
    private val BLOG_ID = "6970200487036183744"
    private val bloggerApiBase = "https://www.blogger.com/feeds/$BLOG_ID/posts/default"

    override val mainPage = mainPageOf(
        "" to "Yu Thánh Thiện - Mới Cập Nhật",
        "movie" to "Tất Cả Phim BL",
        "thai-lan" to "BL Thái Lan",
        "trung-quoc" to "BL Trung Quốc",
        "dai-loan" to "BL Đài Loan",
        "nhat-ban" to "BL Nhật Bản",
        "completed" to "Phim Đã Hoàn Tất"
    )

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val mapper = jacksonObjectMapper()

    // Khóa trường JSON của Blogger
    private val JSON_T_KEY = "$" + "t"
    private val JSON_THUMBNAIL_KEY = "media$" + "thumbnail"

    data class YtServer(
        val name: String? = null,
        val type: String? = null,
        val link: String? = null,
        val _id: String? = null
    )

    data class YtEpisode(
        val name: String? = null,
        val servers: List<YtServer>? = null
    )

    data class YtPayload(
        val v: Int? = null,
        val type: String? = null,
        val note: String? = null,
        val ratingYu: Int? = null,
        val image: String? = null,
        val episodes: List<YtEpisode>? = null
    )

    private fun decodeBase64Payload(htmlContent: String): YtPayload? {
        return try {
            val match = Regex("""class=["']?yt-data["']?[^>]*>([^<]+)</div>""").find(htmlContent)
            if (match != null) {
                val base64Str = match.groupValues[1].trim()
                val decodedBytes = Base64.getDecoder().decode(base64Str)
                val jsonString = String(decodedBytes, Charsets.UTF_8)
                mapper.readValue<YtPayload>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPosterFromHtml(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val img = doc.selectFirst("img")
            var poster = ""
            if (img != null) {
                val candidates = listOf(
                    img.attr("src"),
                    img.attr("data-src"),
                    img.attr("data-original")
                )
                for (cand in candidates) {
                    val trimmed = cand.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("data:image")) {
                        poster = trimmed
                        break
                    }
                }
            }
            fixUrl(poster)
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun fetchBloggerFeed(endpointUrl: String): String {
        return try {
            app.get(
                endpointUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Accept" to "application/json"
                )
            ).text
        } catch (e: Exception) {
            // Nếu có sự cố, thử fallback sang domain yuthanhthien.top
            val fallbackUrl = endpointUrl.replace("https://www.blogger.com/feeds/$BLOG_ID/posts/default", "$mainUrl/feeds/posts/default")
            app.get(
                fallbackUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "$mainUrl/"
                )
            ).text
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val startIndex = (page - 1) * 20 + 1
        val feedUrl = if (request.data.isEmpty()) {
            "$bloggerApiBase?alt=json&start-index=$startIndex&max-results=20"
        } else {
            val encTag = URLEncoder.encode(request.data, "UTF-8")
            "$bloggerApiBase/-/$encTag?alt=json&start-index=$startIndex&max-results=20"
        }

        val jsonStr = fetchBloggerFeed(feedUrl)
        val searchList = mutableListOf<SearchResponse>()

        try {
            val root = mapper.readTree(jsonStr)
            val entries = root.get("feed")?.get("entry")
            if (entries != null && entries.isArray) {
                for (entry in entries) {
                    val title = entry.get("title")?.get(JSON_T_KEY)?.asText()?.trim() ?: continue
                    val rawId = entry.get("id")?.get(JSON_T_KEY)?.asText() ?: ""
                    val postId = if (rawId.contains("post-")) rawId.substringAfter("post-") else ""

                    val postApiUrl = if (postId.isNotEmpty()) {
                        "$bloggerApiBase/$postId?alt=json"
                    } else {
                        var altLink = ""
                        val links = entry.get("link")
                        if (links != null && links.isArray) {
                            for (l in links) {
                                if (l.get("rel")?.asText() == "alternate") {
                                    altLink = l.get("href")?.asText() ?: ""
                                    break
                                }
                            }
                        }
                        altLink
                    }

                    if (postApiUrl.isEmpty()) continue

                    val htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText() ?: ""
                    val ytData = decodeBase64Payload(htmlContent)

                    val thumbnail = entry.get(JSON_THUMBNAIL_KEY)?.get("url")?.asText()
                        ?.replace("/s72-c/", "/s600/")
                    val poster = ytData?.image ?: thumbnail ?: extractPosterFromHtml(htmlContent)

                    searchList.add(
                        newTvSeriesSearchResponse(title, postApiUrl, TvType.AsianDrama) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = searchList,
                    isHorizontalImages = false
                )
            ),
            hasNext = searchList.isNotEmpty()
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.trim().isEmpty()) return emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = "$bloggerApiBase?q=$encodedQuery&alt=json&max-results=30"

        val jsonStr = fetchBloggerFeed(searchUrl)
        val results = mutableListOf<SearchResponse>()

        try {
            val root = mapper.readTree(jsonStr)
            val entries = root.get("feed")?.get("entry")
            if (entries != null && entries.isArray) {
                for (entry in entries) {
                    val title = entry.get("title")?.get(JSON_T_KEY)?.asText()?.trim() ?: continue
                    val rawId = entry.get("id")?.get(JSON_T_KEY)?.asText() ?: ""
                    val postId = if (rawId.contains("post-")) rawId.substringAfter("post-") else ""

                    val postApiUrl = if (postId.isNotEmpty()) {
                        "$bloggerApiBase/$postId?alt=json"
                    } else {
                        var altLink = ""
                        val links = entry.get("link")
                        if (links != null && links.isArray) {
                            for (l in links) {
                                if (l.get("rel")?.asText() == "alternate") {
                                    altLink = l.get("href")?.asText() ?: ""
                                    break
                                }
                            }
                        }
                        altLink
                    }

                    if (postApiUrl.isEmpty()) continue

                    val htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText() ?: ""
                    val ytData = decodeBase64Payload(htmlContent)

                    val thumbnail = entry.get(JSON_THUMBNAIL_KEY)?.get("url")?.asText()
                        ?.replace("/s72-c/", "/s600/")
                    val poster = ytData?.image ?: thumbnail ?: extractPosterFromHtml(htmlContent)

                    results.add(
                        newTvSeriesSearchResponse(title, postApiUrl, TvType.AsianDrama) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        var title = "Phim BL"
        var htmlContent = ""
        var poster = ""
        var originalUrl = url

        if (url.contains("blogger.com/feeds/") || url.endsWith("?alt=json")) {
            val jsonStr = app.get(url, headers = mapOf("User-Agent" to userAgent)).text
            val root = mapper.readTree(jsonStr)
            val entry = if (root.has("entry")) root.get("entry") else root

            title = entry.get("title")?.get(JSON_T_KEY)?.asText()?.trim() ?: "Phim BL"
            htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText() ?: ""

            val links = entry.get("link")
            if (links != null && links.isArray) {
                for (l in links) {
                    if (l.get("rel")?.asText() == "alternate") {
                        originalUrl = l.get("href")?.asText() ?: url
                        break
                    }
                }
            }
        } else {
            val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
            val rawTitle = doc.selectFirst("h1.post-title, h1, title")?.text()?.trim() ?: "Phim BL"
            title = rawTitle.substringBefore(" - Yu Thánh Thiện").substringBefore(" - Yu Gềi").trim()
            htmlContent = doc.html()
        }

        title = title.substringBefore(" - Yu Thánh Thiện").substringBefore(" - Yu Gềi").trim()

        val ytData = decodeBase64Payload(htmlContent)
        poster = ytData?.image ?: extractPosterFromHtml(htmlContent)
        val plot = ytData?.note?.ifEmpty { null } ?: title

        val episodes = mutableListOf<Episode>()

        if (ytData?.episodes != null && ytData.episodes.isNotEmpty()) {
            ytData.episodes.forEachIndexed { epIndex, ep ->
                val epNum = epIndex + 1
                val epTitle = ep.name?.ifEmpty { "Tập $epNum" } ?: "Tập $epNum"
                val servers = ep.servers ?: emptyList()

                val isMultiPart = servers.any { s ->
                    val sName = s.name ?: ""
                    sName.contains("/") || sName.contains("phần", ignoreCase = true)
                }

                if (isMultiPart) {
                    servers.forEachIndexed { partIdx, server ->
                        val link = server.link?.trim() ?: ""
                        if (link.isNotEmpty()) {
                            val partName = server.name ?: "P${partIdx + 1}"
                            episodes.add(
                                newEpisode(link) {
                                    this.name = "$epTitle ($partName)"
                                    this.episode = epNum
                                }
                            )
                        }
                    }
                } else {
                    val validLinks = servers.mapNotNull { it.link?.trim() }.filter { it.isNotEmpty() }
                    if (validLinks.isNotEmpty()) {
                        val bundledData = validLinks.joinToString("|||")
                        episodes.add(
                            newEpisode(bundledData) {
                                this.name = epTitle
                                this.episode = epNum
                            }
                        )
                    }
                }
            }
        } else {
            val doc = Jsoup.parse(htmlContent)
            val iframes = doc.select("iframe")
            iframes.forEachIndexed { idx, iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotEmpty()) {
                    val epNumber = idx + 1
                    episodes.add(
                        newEpisode(src) {
                            this.name = "Tập $epNumber"
                            this.episode = epNumber
                        }
                    )
                }
            }
        }

        val distinctEpisodes = episodes.distinctBy { it.data }

        return newTvSeriesLoadResponse(title, originalUrl, TvType.AsianDrama, distinctEpisodes) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = listOf("BL", "Đam Mỹ", "Vietsub")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var hasLoadedAny = false
        val serverUrls = data.split("|||").map { it.trim() }.filter { it.isNotEmpty() }

        val okExtractor = OkRuExtractor()
        val vkExtractor = VkExtractor()

        for (rawUrl in serverUrls) {
            var url = rawUrl

            if (url.contains("workers.dev/videoembed/")) {
                val id = url.substringAfter("/videoembed/").substringBefore("?").substringBefore("#")
                url = "https://ok.ru/videoembed/$id"
            } else if (url.contains("captionfy[.]com/video/youtube/") || url.contains("captionfy.com/video/youtube/")) {
                val match = Regex("""captionfy[.]com/video/youtube/([a-zA-Z0-9_-]+)""").find(url)
                if (match != null) {
                    url = "https://www.youtube.com/watch?v=" + match.groupValues[1]
                }
            }

            try {
                when {
                    url.contains("ok.ru") || url.contains("odnoklassniki") -> {
                        okExtractor.getUrl(url, "$mainUrl/", subtitleCallback, callback)
                        hasLoadedAny = true
                    }
                    url.contains("vk.com") || url.contains("vkvideo.ru") || url.contains("vkontakte") -> {
                        vkExtractor.getUrl(url, "$mainUrl/", subtitleCallback, callback)
                        hasLoadedAny = true
                    }
                    else -> {
                        val success = loadExtractor(url, "$mainUrl/", subtitleCallback, callback)
                        if (success) hasLoadedAny = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return hasLoadedAny
    }
}
