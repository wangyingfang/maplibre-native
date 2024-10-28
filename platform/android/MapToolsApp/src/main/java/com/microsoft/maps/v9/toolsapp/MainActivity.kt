package com.microsoft.maps.v9.toolsapp

import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import timber.log.Timber
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var mapView: MapView
    private lateinit var optionsButton: ImageButton
    private lateinit var inputEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var outputText: TextView

    private var status = ""

    private val statusChanged = StatusChangedWatcher { status = it; updateOutput() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)

        mapView.addOnCameraWillChangeListener(statusChanged)
        mapView.addOnWillStartRenderingMapListener(statusChanged)
        mapView.addOnWillStartLoadingMapListener(statusChanged)
        mapView.addOnDidBecomeIdleListener(statusChanged)

        mapView.getMapAsync {
            it.setMinZoomPreference(2.0)
            it.setMaxZoomPreference(20.0)
            it.setStyle(Style.Builder().fromJson(loadStyle(darkMode = false)))
            it.cameraPosition =
                CameraPosition.Builder().target(LatLng(39.85911, 117.343539)).zoom(6.2).build()
        }
        optionsButton = findViewById(R.id.options_btn)
        optionsButton.setOnClickListener { v ->
            showPopupMenu(v);
        }
        inputEditText = findViewById(R.id.automation_input_text)
        submitButton = findViewById<Button?>(R.id.automation_submit_button).also {
            it.setOnClickListener {
                val commandString = inputEditText.text.toString()
                Timber.tag(TAG).i("onCommand(commandString: ${commandString})")
                try {
                    commands.evaluate(commandString)
                } catch (e: Exception) {
                    val msg = "error: " + (e.message ?: "Invalid input value")
                    // showAlertDialog(msg)
                    Timber.tag(TAG).w(">>>>>>>> command execution failed, $msg")
                }
                inputEditText.text.clear()
            }
        }
        outputText = findViewById(R.id.automation_output_text)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.removeOnCameraWillChangeListener(statusChanged)
        mapView.removeOnWillStartRenderingMapListener(statusChanged)
        mapView.removeOnWillStartLoadingMapListener(statusChanged)
        mapView.removeOnDidBecomeIdleListener(statusChanged)

        mapView.onDestroy()
        super.onDestroy()
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        val inflater: MenuInflater = popupMenu.menuInflater
        inflater.inflate(R.menu.menu_options, popupMenu.menu)
        val filtersSubMenu = popupMenu.menu.findItem(R.id.menu_action_filters).subMenu
        if (filtersSubMenu != null) {
            filtersSubMenu.clear()
            for ((i, preset) in presetFilters.withIndex()) {
                filtersSubMenu.add(preset.title).setOnMenuItemClickListener {
                    selectFilter(i)
                    true
                }
            }
        }


        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_action_road_light_style -> {
                    Timber.tag(TAG).i("Switch to road light style")
                    mapView.getMapAsync {
                        setStyle(false)
                    }
                    true
                }

                R.id.menu_action_road_dark_style -> {
                    Timber.tag(TAG).i("Switch to road dark style")
                    mapView.getMapAsync {
                        setStyle(true)
                    }
                    true
                }

                R.id.menu_action_test1 -> {
                    Timber.tag(TAG).i("menu_action_test1")
                    extractScreenLabels()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun selectFilter(i: Int) {
        Timber.tag(TAG).i("selectFilter #$i")
        val preset = presetFilters.getOrNull(i)
        if (preset == null) {
            Timber.tag(TAG).w("Selected filter index #$i not exists.")
            return
        }
        mapView.getMapAsync {
            preset.doFilter(it)
        }
    }

    private fun extractScreenLabels() {
        Timber.tag(TAG).i("extractScreenLabels")

        val calculateDistance = { x1: Float, y1: Float, x2: Float, y2: Float ->
            Float
            sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
        }
        mapView.getMapAsync { map ->
            val width = map.width
            val height = map.height
            val screenRect = RectF(0f, 0f, width, height)
            val screenCenter = PointF(width / 2 - 1, height / 2 - 1)
            val features = map.queryRenderedFeatures(screenRect)
            features.filter {
                it.geometry()?.type() == "Point"
                        && it.properties()?.get("name").isString()
                        && it.properties()?.get("textSize").isFloat()
                        && it.properties()?.get("textColor").isRgba()
            }.map {
                val geoPoint = it.geometry() as Point
                val pixels = map.projection.toScreenLocation(LatLng(geoPoint.latitude(), geoPoint.longitude()))
                val distanceInPixels = calculateDistance(screenCenter.x, screenCenter.y, pixels.x, pixels.y)

                val props = it.properties()!!
                val text = props.get("name").getStringOrDefault()
                val secondText = props.get("sec-name").getStringOrDefault(props.get("name2").getStringOrDefault())
                val textSize = props.get("textSize").getFloatOrDefault()
                val textColor = props.get("textColor").getRgbaOrDefault()
                val textHaloWidth = props.get("textHaloWidth").getFloatOrDefault()
                val textHaloColor = props.get("textHaloColor").getRgbaOrDefault()
                val iconSize = props.get("iconSize").getFloatOrDefault()
                val iconColor = props.get("iconColor").getRgbaOrDefault()
                val iconHaloWidth = props.get("iconHaloWidth").getFloatOrDefault()
                val iconHaloColor = props.get("iconHaloColor").getRgbaOrDefault()
                // TODO iconName???
                // TODO sourceLayer???
                true
            }
        }
    }

    private fun setStyle(darkMode: Boolean) {
        mapView.getMapAsync {
            it.setStyle(Style.Builder().fromJson(loadStyle(darkMode)))
            FilterChain.resetAfterStyleChanged()
        }
    }

    private fun loadStyle(darkMode: Boolean): String =
        try {
            val styleId = if (darkMode) R.raw.bing_maps_v9_china_dark_1 else R.raw.bing_maps_v9_china_1
            ResourceUtils.readRawResource(this, styleId)
        } catch (exception: Exception) {
            Timber.e(exception, "Can't load local file style")
            ""
        }

    private fun updateOutput() {
        mapView.getMapAsync {
            val cp = it.cameraPosition
            val zoom = cp.zoom
            val loc = cp.target

            val northWest = it.projection.fromScreenLocation(PointF(0f, 0f))
            val southEast = it.projection.fromScreenLocation(
                PointF(
                    mapView.width.toFloat(),
                    mapView.height.toFloat()
                )
            )
            val outputStr = "{" +
                    "\"status\":\"$status\"," +
                    "\"bounds\":[${northWest.latitude},${northWest.longitude},${southEast.latitude},${southEast.longitude}]," +
                    "\"center\":[${loc?.latitude},${loc?.longitude}]," +
                    "\"zoom\":${zoom}" +
                    "}"
            outputText.text = outputStr
            Timber.tag(TAG).i("outputStr=$outputStr")
        }
    }

    private val commands = CommandEvaluator(object : CommandExecutor {
        override fun setAutomationMode(enable: Boolean) {
            Timber.tag(TAG).i("setAutomationMode(enable: ${enable})")
            // nothing to do.
        }

        override fun setScene(scene: Scene) {
            Timber.tag(TAG).i("setScene(scene: ${scene})")
            mapView.getMapAsync {
                it.cameraPosition =
                    CameraPosition.Builder().target(LatLng(scene.lat, scene.lon)).zoom(scene.zoom)
                        .build()
            }
        }

        override fun setTheme(themeName: String) {
            Timber.tag(TAG).i("setTheme(themeName: ${themeName})")
            val isDarkStyle = themeName == "roadDark"
            mapView.getMapAsync {
                setStyle(isDarkStyle)
            }
        }

        override fun updateStyles(styles: List<com.microsoft.maps.v9.toolsapp.Style>) {
            Timber.tag(TAG).i("updateStyles(styles: ${styles})")
            // nothing to do.
        }

        override fun setStyle(index: Int) {
            val i = max(0, index - 1)
            selectFilter(i)
        }

        override fun extractLabels() {
            extractScreenLabels()
        }
    })
}
