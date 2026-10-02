package com.yutt

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

class VkExtractor : ExtractorApi() {
    override var name = "VKontakte"
    override var mainUrl = "https://vk.com"
    override val requiresReferer = false

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
            val hlsMatch = Regex(""""hls"\s*:\s*"([^"]+)"""").find(response)
            if (hlsMatch != null) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name - 1080p FHD HLS Adaptive",
                        url = hlsMatch.groupValues[1].replace("\\/", "/"),
                        referer = "https://vk.com/",
                        quality = Qualities.P1080.value,
                        type = INFER_TYPE
                    )
                )
            }

            // 2. Lọc DUY NHẤT MP4 1080p Full HD (Loại bỏ toàn bộ 720p, 480p, 360p, 240p)
            val mp4_1080 = Regex(""""url1080"\s*:\s*"([^"]+)"""").find(response)
            if (mp4_1080 != null) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name - 1080p Full HD Direct",
                        url = mp4_1080.groupValues[1].replace("\\/", "/"),
                        referer = "https://vk.com/",
                        quality = Qualities.P1080.value,
                        type = INFER_TYPE
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
