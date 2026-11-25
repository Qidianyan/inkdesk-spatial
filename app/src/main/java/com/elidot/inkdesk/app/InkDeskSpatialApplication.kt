package com.elidot.inkdesk.app

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch

/**
 * Entry Application for InkDesk Spatial (砚境空间) on PICO OS 6 Spatial SDK.
 */
class InkDeskSpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::inkDeskMainApp)
    }
}
