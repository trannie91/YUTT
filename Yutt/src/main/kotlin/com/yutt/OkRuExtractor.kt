package com.yutt

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class OkRuExtractor : ExtractorApi() {
    override var name = "OK.ru"
    override var mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            var targetUrl = url.trim()
            if (targetUrl.contains("workers.dev/videoembed/")) {
                val id = targetUrl.substringAfter("/videoembed/").substringBefore("?").substringBefore("#")
                targetUrl = "https://ok.ru/videoembed/$id"
            }

            val doc = app.get(
                targetUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "https://www.yuthanhthien.top/"
                )
            ).document

            val dataOptions = doc.selectFirst("div[data-options]")?.attr("data-options") ?: return
            val decoded = dataOptions.replace("&quot;", """).replace("&amp;", "&")

            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(decoded)
            val metadataNode = root.get("flashvars")?.get("metadata") ?: return

            val metaJson = if (metadataNode.isTextual) {
                mapper.readTree(metadataNode.asText())
            } else {
                metadataNode
            }

            // 1. Luồng HLS Adaptive Master Playlist 1080p
            val hlsUrl = metaJson.get("hlsManifestUrl")?.asText()
            if (!hlsUrl.isNullOrEmpty()) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name - 1080p FHD HLS Adaptive",
                        url = hlsUrl,
                        referer = "https://ok.ru/",
                        quality = Qualities.P1080.value,
                        type = INFER_TYPE
                    )
                )
            }

            // 2. Lọc DUY NHẤT luồng MP4 1080p Full HD (Loại bỏ toàn bộ 720p, 480p, 360p, 240p, 144p)
            val videosNode = metaJson.get("videos")
            if (videosNode != null && videosNode.isArray) {
                val videoList = mutableListOf<Pair<String, String>>()
                for (v in videosNode) {
                    val qualityName = v.get("name")?.asText()?.lowercase() ?: ""
                    val videoUrl = v.get("url")?.asText() ?: continue
                    videoList.add(qualityName to videoUrl)
                }

                val fhd1080 = videoList.firstOrNull { it.first == "full" || it.first == "1080" }
                if (fhd1080 != null) {
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name - 1080p Full HD Direct",
                            url = fhd1080.second,
                            referer = "https://ok.ru/",
                            quality = Qualities.P1080.value,
                            type = INFER_TYPE
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
