package com.yutt

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class OkRuExtractor : ExtractorApi() {
    override var name = "OK.ru"
    override var mainUrl = "https://ok.ru"
    override val requiresReferer = false

    private val DOUBLE_QUOTE = 34.toChar().toString()

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
            } else if (!targetUrl.contains("/videoembed/") && targetUrl.contains("/video/")) {
                val id = targetUrl.substringAfter("/video/").substringBefore("?").substringBefore("#")
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
            val decoded = dataOptions.replace("&quot;", DOUBLE_QUOTE).replace("&amp;", "&")

            val mapper = jacksonObjectMapper()
            val root = mapper.readTree(decoded)
            val metadataNode = root.get("flashvars")?.get("metadata") ?: return

            val metaJson = if (metadataNode.isTextual) {
                mapper.readTree(metadataNode.asText())
            } else {
                metadataNode
            }

            // 1. Luồng MP4 trực tiếp 1080p Full HD
            val videosNode = metaJson.get("videos")
            if (videosNode != null && videosNode.isArray) {
                for (v in videosNode) {
                    val qualityName = v.get("name")?.asText()?.lowercase() ?: ""
                    val videoUrl = v.get("url")?.asText() ?: continue
                    if (qualityName == "full" || qualityName == "1080" || qualityName == "ultra" || qualityName == "quad") {
                        callback(
                            ExtractorLink(
                                source = name,
                                name = "OK.ru - 1080p Full HD",
                                url = videoUrl,
                                referer = "https://ok.ru/",
                                quality = Qualities.P1080.value,
                                type = ExtractorLinkType.VIDEO
                            )
                        )
                        break
                    }
                }
            }

            // 2. Luồng HLS Adaptive Master Playlist 1080p
            val hlsUrl = metaJson.get("hlsManifestUrl")?.asText()
            if (!hlsUrl.isNullOrEmpty()) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "OK.ru - 1080p FHD HLS Adaptive",
                        url = hlsUrl,
                        referer = "https://ok.ru/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
