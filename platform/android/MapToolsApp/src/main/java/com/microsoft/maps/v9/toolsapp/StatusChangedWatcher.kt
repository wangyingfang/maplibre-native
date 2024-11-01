package com.microsoft.maps.v9.toolsapp

import org.maplibre.android.maps.MapView
import timber.log.Timber

class StatusChangedWatcher(private val callback: (String) -> Unit, private val debounce: Boolean = true) :
    MapView.OnCameraWillChangeListener,
    MapView.OnWillStartRenderingMapListener, MapView.OnWillStartLoadingMapListener,
    MapView.OnDidBecomeIdleListener {
    private val TAG = "MapEventListener"

    private var mapView: MapView? = null
    private var curStatus: String = ""

    fun attachToMapView(mapView: MapView) {
        this.mapView = mapView
        mapView.addOnCameraWillChangeListener(this)
        mapView.addOnWillStartRenderingMapListener(this)
        mapView.addOnWillStartLoadingMapListener(this)
        mapView.addOnDidBecomeIdleListener(this)
    }

    fun detachFromMapView() {
        mapView?.removeOnCameraWillChangeListener(this)
        mapView?.removeOnWillStartRenderingMapListener(this)
        mapView?.removeOnWillStartLoadingMapListener(this)
        mapView?.removeOnDidBecomeIdleListener(this)
    }

    override fun onCameraWillChange(animated: Boolean) {
        Timber.tag(TAG).d("onCameraWillChange(animated: $animated)")
        if (!debounce || curStatus != "busy") {
            curStatus = "busy"
            callback.invoke(curStatus)
        }
    }

    override fun onWillStartRenderingMap() {
        Timber.tag(TAG).d("onWillStartRenderingMap()")
        if (!debounce || curStatus != "busy") {
            curStatus = "busy"
            callback.invoke(curStatus)
        }
    }

    override fun onWillStartLoadingMap() {
        Timber.tag(TAG).d("onWillStartLoadingMap()")
        if (!debounce || curStatus != "busy") {
            curStatus = "busy"
            callback.invoke(curStatus)
        }
    }

    override fun onDidBecomeIdle() {
        Timber.tag(TAG).d("onDidBecomeIdle()")
        if (!debounce || curStatus != "idle") {
            curStatus = "idle"
            callback.invoke(curStatus)
        }
    }
}
