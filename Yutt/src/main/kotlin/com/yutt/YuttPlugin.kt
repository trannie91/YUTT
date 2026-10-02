package com.yutt

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YuttPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider Yu Thánh Thiện (yuthanhthien.top)
        registerMainAPI(YuttProvider())

        // Đăng ký các bộ bóc tách video trực tiếp 1080p FHD từ OK.ru và VKontakte
        registerExtractorAPI(OkRuExtractor())
        registerExtractorAPI(VkExtractor())
    }
}
