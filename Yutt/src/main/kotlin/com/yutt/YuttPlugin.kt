package com.yutt

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.app
import android.content.Context

@CloudstreamPlugin
class YuttPlugin: Plugin() {
    override fun load(context: Context) {
        try {
            // Tích hợp SafeDns vào OkHttp để loại bỏ lỗi chặn DNS [::1] trên Android TV Box
            app.baseClient = app.baseClient.newBuilder()
                .dns(SafeDns())
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Đăng ký Provider Yu Thánh Thiện (yuthanhthien.top)
        registerMainAPI(YuttProvider())

        // Đăng ký các bộ bóc tách video trực tiếp 1080p FHD từ OK.ru và VKontakte
        registerExtractorAPI(OkRuExtractor())
        registerExtractorAPI(VkExtractor())
    }
}
