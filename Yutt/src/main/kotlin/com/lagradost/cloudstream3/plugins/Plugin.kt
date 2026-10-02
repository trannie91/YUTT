package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

open class Plugin {
    var filename: String? = null
    var openSettings: ((Context) -> Unit)? = null

    open fun load(context: Context) {}
    open fun reload(context: Context) {}
    open fun beforeUnload() {}
    open fun unload(context: Context) {}

    fun registerMainAPI(api: MainAPI) {}
    fun registerExtractorAPI(api: ExtractorApi) {}
}
