package com.citynettv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CityNetTVPlugin : Plugin() {

    override fun load(context: Context) {
        // Register settings page (login screen)
        settingsPage = CityNetTVSettingsFragment()

        val provider = CityNetTVProvider(context)
        provider.initApi(context)
        registerMainAPI(provider)
    }
}
