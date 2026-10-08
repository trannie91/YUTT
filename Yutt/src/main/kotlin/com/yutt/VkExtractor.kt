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

    private val ESCAPED_SLASH = charArrayOf(92.toChar(), 47.toChar()).concatToString()

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

            // 1. Tìm DUY NHẤT MP4 1080p Full HD trước - Loại bỏ toàn bộ chất lượng thấp
            val mp4_1080 = Regex("""["']?url1080["']?s*[:=]s*["']?([^"',s>]+)""").find(response)
            if (mp4_1080 != null) {
                val mp4Url = mp4_1080.groupValues[1].replace(ESCAPED_SLASH, "/")
                callback(
                    ExtractorLink(
                        source = name,
                        name = "VKontakte - 1080p Full HD",
                        url = mp4Url,
                        referer = "https://vk.com/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
                return
            }

            // 2. Nếu không có direct MP4 1080p, lấy luồng HLS FHD 1080p Adaptive
            val hlsMatch = Regex("""["']?hls["']?s*[:=]s*["']?([^"',s>]+)""").find(response)
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
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
