package com.citynettv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CityNetTVPlugin : Plugin() {

    override fun load(context: Context) {
        // Register settings button — opens login dialog
        openSettings = { ctx ->
            CityNetTVSettingsDialog.show(ctx)
        }

        val provider = CityNetTVProvider(context)
        provider.initApi(context)
        registerMainAPI(provider)
    }
}
