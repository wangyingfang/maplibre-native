package com.microsoft.maps.v9.toolsapp

import org.maplibre.android.maps.MapView
import timber.log.Timber

class StatusChangedWatcher(private val callback: (String) -> Unit) :
    MapView.OnCameraWillChangeListener,
    MapView.OnWillStartRenderingMapListener, MapView.OnWillStartLoadingMapListener,
    MapView.OnDidBecomeIdleListener {
    private val TAG = "MapEventListener"

    override fun onCameraWillChange(animated: Boolean) {
        Timber.tag(TAG).i("onCameraWillChange(animated: $animated)")
        callback.invoke("busy")
    }

    override fun onWillStartRenderingMap() {
        Timber.tag(TAG).i("onWillStartRenderingMap()")
        callback.invoke("busy")
    }

    override fun onWillStartLoadingMap() {
        Timber.tag(TAG).i("onWillStartLoadingMap()")
        callback.invoke("busy")
    }

    override fun onDidBecomeIdle() {
        Timber.tag(TAG).i("onDidBecomeIdle()")
        callback.invoke("idle")
    }
}