package com.yutt

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

class VkExtractor : ExtractorApi() {
    override var name = "VKontakte"
    override var mainUrl = "https://vk.com"
    override val requiresReferer = false

    private val ESCAPED_SLASH = "\" + "/"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(
                url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "https://www.yuthanhthien.top/"
                )
            ).text

            // 1. Luồng HLS Multi-Quality Adaptive FHD 1080p
            val hlsMatch = Regex(""""hls"s*:s*"([^"]+)"""").find(response)
                ?: Regex("""hlss*=s*([^s&"']+)""").find(response)

            if (hlsMatch != null) {
                val hlsUrl = hlsMatch.groupValues[1].replace(ESCAPED_SLASH, "/")
                callback(
                    ExtractorLink(
                        source = name,
                        name = "VKontakte - 1080p FHD HLS Adaptive",
                        url = hlsUrl,
                        referer = "https://vk.com/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }

            // 2. Lọc DUY NHẤT MP4 1080p Full HD (Loại bỏ toàn bộ 720p, 480p, 360p, 240p)
            val mp4_1080 = Regex(""""url1080"s*:s*"([^"]+)"""").find(response)
                ?: Regex("""url1080s*=s*([^s&"']+)""").find(response)

            if (mp4_1080 != null) {
                val mp4Url = mp4_1080.groupValues[1].replace(ESCAPED_SLASH, "/")
                callback(
                    ExtractorLink(
                        source = name,
                        name = "VKontakte - 1080p Full HD Direct",
                        url = mp4Url,
                        referer = "https://vk.com/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            } else if (hlsMatch == null) {
                val mp4_720 = Regex(""""url720"s*:s*"([^"]+)"""").find(response)
                    ?: Regex("""url720s*=s*([^s&"']+)""").find(response)
                if (mp4_720 != null) {
                    val mp4Url = mp4_720.groupValues[1].replace(ESCAPED_SLASH, "/")
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "VKontakte - 720p HD Direct",
                            url = mp4Url,
                            referer = "https://vk.com/",
                            quality = Qualities.P720.value,
                            type = ExtractorLinkType.VIDEO
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
