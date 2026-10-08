package com.yutt

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
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

    // Blog ID chính thức trên Google Blogger
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
    
    // Cấu hình ObjectMapper chống lỗi khi gặp trường dữ liệu mới
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    }

    // Khóa trường JSON của Blogger
    private val JSON_T_KEY = "$" + "t"
    private val JSON_THUMBNAIL_KEY = "media$" + "thumbnail"

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YtServer(
        val name: String? = null,
        val type: String? = null,
        val link: String? = null,
        val _id: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YtEpisode(
        val name: String? = null,
        val servers: List<YtServer>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YtPayload(
        val v: Int? = null,
        val type: String? = null,
        val note: String? = null,
        val ratingYu: Int? = null,
        val image: String? = null,
        val episodes: List<YtEpisode>? = null
    )

    private fun decodeBase64Payload(htmlContent: String): YtPayload? {
        if (htmlContent.isEmpty()) return null
        return try {
            val doc = Jsoup.parse(htmlContent)
            val ytDataDiv = doc.selectFirst("div.yt-data, div[class*=yt-data], .yt-data")
            var base64Str = ytDataDiv?.text()?.trim()

            if (base64Str.isNullOrEmpty()) {
                val match = Regex("""yt-data["'][^>]*>([^<]+)</div>""").find(htmlContent)
                if (match != null) {
                    base64Str = match.groupValues[1].trim()
                }
            }

            if (!base64Str.isNullOrEmpty()) {
                val decodedBytes = Base64.getDecoder().decode(base64Str)
                val jsonString = String(decodedBytes, Charsets.UTF_8)
                
                try {
                    mapper.readValue<YtPayload>(jsonString)
                } catch (e: Exception) {
                    // Dự phòng phân tích Tree JSON trực tiếp nếu Jackson ánh xạ thất bại
                    val root = mapper.readTree(jsonString)
                    val img = root.get("image")?.asText()
                    val noteText = root.get("note")?.asText()
                    val epsList = mutableListOf<YtEpisode>()
                    val epsNode = root.get("episodes")
                    if (epsNode != null && epsNode.isArray) {
                        for (epNode in epsNode) {
                            val epName = epNode.get("name")?.asText()
                            val sList = mutableListOf<YtServer>()
                            val sNode = epNode.get("servers")
                            if (sNode != null && sNode.isArray) {
                                for (srv in sNode) {
                                    sList.add(
                                        YtServer(
                                            name = srv.get("name")?.asText(),
                                            type = srv.get("type")?.asText(),
                                            link = srv.get("link")?.asText()
                                        )
                                    )
                                }
                            }
                            epsList.add(YtEpisode(name = epName, servers = sList))
                        }
                    }
                    YtPayload(image = img, note = noteText, episodes = epsList)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractPosterFromHtml(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val img = doc.selectFirst("img[src*=blogger.googleusercontent.com], img[src*=bp.blogspot.com], img")
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

                    val htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText()
                        ?: entry.get("summary")?.get(JSON_T_KEY)?.asText() ?: ""
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

                    val htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText()
                        ?: entry.get("summary")?.get(JSON_T_KEY)?.asText() ?: ""
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

        try {
            if (url.contains("blogger.com/feeds/") || url.endsWith("?alt=json")) {
                val jsonStr = fetchBloggerFeed(url)
                val root = mapper.readTree(jsonStr)
                val entry = if (root.has("entry")) root.get("entry") else root

                title = entry.get("title")?.get(JSON_T_KEY)?.asText()?.trim() ?: "Phim BL"
                htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText()
                    ?: entry.get("summary")?.get(JSON_T_KEY)?.asText() ?: ""
            } else {
                val slug = url.substringAfterLast("/").substringBefore(".html")
                val searchUrl = "$bloggerApiBase?q=" + URLEncoder.encode(slug, "UTF-8") + "&alt=json&max-results=1"
                val jsonStr = fetchBloggerFeed(searchUrl)
                val root = mapper.readTree(jsonStr)
                val entry = root.get("feed")?.get("entry")?.firstOrNull()

                if (entry != null) {
                    title = entry.get("title")?.get(JSON_T_KEY)?.asText()?.trim() ?: "Phim BL"
                    htmlContent = entry.get("content")?.get(JSON_T_KEY)?.asText()
                        ?: entry.get("summary")?.get(JSON_T_KEY)?.asText() ?: ""
                } else {
                    val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
                    val rawTitle = doc.selectFirst("h1.post-title, h1, title")?.text()?.trim() ?: "Phim BL"
                    title = rawTitle.substringBefore(" - Yu Thánh Thiện").substringBefore(" - Yu Gềi").trim()
                    htmlContent = doc.html()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        title = title.substringBefore(" - Yu Thánh Thiện").substringBefore(" - Yu Gềi").trim()

        val ytData = decodeBase64Payload(htmlContent)
        poster = ytData?.image ?: extractPosterFromHtml(htmlContent)
        val plot = ytData?.note?.ifEmpty { null } ?: title

        val episodes = mutableListOf<Episode>()

        if (ytData?.episodes != null && ytData.episodes.isNotEmpty()) {
            var epCounter = 1
            for (ep in ytData.episodes) {
                val epName = ep.name?.trim()?.ifEmpty { "Tập $epCounter" } ?: "Tập $epCounter"
                val servers = ep.servers ?: emptyList()

                if (servers.isEmpty()) {
                    epCounter++
                    continue
                }

                val isMultiPart = servers.size > 1 && servers.any { s ->
                    val sName = s.name ?: ""
                    sName.contains("/") || sName.contains("phần", ignoreCase = true) || sName.contains("P", ignoreCase = true)
                }

                if (isMultiPart) {
                    servers.forEachIndexed { partIdx, server ->
                        val link = server.link?.trim() ?: ""
                        if (link.isNotEmpty()) {
                            val partName = server.name?.trim()?.ifEmpty { "${partIdx + 1}" } ?: "${partIdx + 1}"
                            val fullEpTitle = if (epName.contains(partName)) epName else "$epName ($partName)"
                            episodes.add(
                                newEpisode(link) {
                                    this.name = fullEpTitle
                                    this.episode = epCounter
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
                                this.name = epName
                                this.episode = epCounter
                            }
                        )
                    }
                }
                epCounter++
            }
        }

        if (episodes.isEmpty() && htmlContent.isNotEmpty()) {
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

        return newTvSeriesLoadResponse(
            name = title,
            url = url,
            type = TvType.AsianDrama,
            episodes = distinctEpisodes
        ) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = listOf("BL", "Đam Mỹ", "Vietsub")
        }
    }

    private fun emit1080pOnly(
        link: ExtractorLink,
        deliveredUrls: MutableSet<String>,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val q = link.quality
        val nameLower = link.name.lowercase()

        val isLowQuality = nameLower.contains("720p") ||
                           nameLower.contains("480p") ||
                           nameLower.contains("360p") ||
                           nameLower.contains("240p") ||
                           nameLower.contains("144p") ||
                           nameLower.contains("sd") ||
                           (q in 1 until Qualities.P1080.value)

        if (!isLowQuality) {
            if (deliveredUrls.add(link.url)) {
                val finalName = when {
                    link.name.contains("1080", ignoreCase = true) || link.name.contains("FHD", ignoreCase = true) -> link.name
                    else -> "${link.name} - 1080p FHD"
                }
                callback(
                    ExtractorLink(
                        source = link.source,
                        name = finalName,
                        url = link.url,
                        referer = link.referer,
                        quality = Qualities.P1080.value,
                        type = link.type,
                        headers = link.headers
                    )
                )
                return true
            }
        }
        return false
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var hasLoadedAny = false
        val serverUrls = data.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        val deliveredUrls = mutableSetOf<String>()

        val okExtractor = OkRuExtractor()
        val vkExtractor = VkExtractor()

        val safeCallback: (ExtractorLink) -> Unit = { rawLink ->
            if (emit1080pOnly(rawLink, deliveredUrls, callback)) {
                hasLoadedAny = true
            }
        }

        for (rawUrl in serverUrls) {
            if (hasLoadedAny) break
            var url = rawUrl

            // 1. Chuyển đổi proxy Cloudflare Worker sang domain OK.ru gốc
            if (url.contains("workers.dev/videoembed/")) {
                val id = url.substringAfter("/videoembed/").substringBefore("?").substringBefore("#")
                url = "https://ok.ru/videoembed/$id"
            }

            // 2. Xử lý link phụ đề Captionfy YouTube
            if (url.contains("captionfy.com/video/youtube/") || url.contains("captionfy[.]com/video/youtube/")) {
                val match = Regex("""captionfy[.]com/video/youtube/([a-zA-Z0-9_-]+)""").find(url)
                if (match != null) {
                    val ytId = match.groupValues[1]
                    val youtubeUrl = "https://www.youtube.com/watch?v=$ytId"
                    
                    try {
                        subtitleCallback(
                            SubtitleFile(
                                "Tiếng Việt (Captionfy)",
                                "https://www.captionfy.com/api/caption?id=$ytId&lang=vi"
                            )
                        )
                    } catch (e: Exception) {
                        // ignore
                    }

                    try {
                        loadExtractor(youtubeUrl, "$mainUrl/", subtitleCallback, safeCallback)
                        if (hasLoadedAny) break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try {
                when {
                    url.contains("ok.ru") || url.contains("odnoklassniki") -> {
                        okExtractor.getUrl(url, "$mainUrl/", subtitleCallback, safeCallback)
                        if (hasLoadedAny) break
                    }
                    url.contains("vk.com") || url.contains("vkvideo.ru") || url.contains("vkontakte") -> {
                        vkExtractor.getUrl(url, "$mainUrl/", subtitleCallback, safeCallback)
                        if (hasLoadedAny) break
                    }
                    url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains(".mp4?") || url.contains(".m3u8?") -> {
                        safeCallback(
                            ExtractorLink(
                                source = name,
                                name = "Direct Stream - 1080p FHD",
                                url = url,
                                referer = "$mainUrl/",
                                quality = Qualities.P1080.value,
                                type = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
                        )
                        if (hasLoadedAny) break
                    }
                    else -> {
                        loadExtractor(url, "$mainUrl/", subtitleCallback, safeCallback)
                        if (hasLoadedAny) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return hasLoadedAny
    }
}
