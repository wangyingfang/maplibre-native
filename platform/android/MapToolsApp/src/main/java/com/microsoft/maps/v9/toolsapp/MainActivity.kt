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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private var lastRequestedLabels = "[]"

    private val statusChanged = StatusChangedWatcher({ status = it; updateOutput() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync {
            it.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
            }
            it.setMinZoomPreference(2.0)
            it.setMaxZoomPreference(20.0)
            it.setStyle(Style.Builder().fromJson(loadStyle(darkMode = false)))
            it.cameraPosition =
                CameraPosition.Builder().target(LatLng(39.85911, 117.343539)).zoom(6.2).build()
        }
        statusChanged.attachToMapView(mapView)
        optionsButton = findViewById(R.id.options_btn)
        optionsButton.setOnClickListener { v ->
            showPopupMenu(v)
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

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars.
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar.
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
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
        statusChanged.detachFromMapView()
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
            val labels = features.filter {
                it.geometry()?.type() == "Point"
                        && it.properties()?.get("name").isString()
                        && it.properties()?.get("textSize").isFloat()
                        && it.properties()?.get("textColor").isRgba()
            }.map {
                val geoPoint = it.geometry() as Point
                val pixels = map.projection.toScreenLocation(LatLng(geoPoint.latitude(), geoPoint.longitude()))
                val distanceInPixels = calculateDistance(screenCenter.x, screenCenter.y, pixels.x, pixels.y)
                val props = it.properties()!!
                val textHaloWidth = props.get("textHaloWidth").getFloatOrDefault(0f)!!
                val iconHaloWidth = props.get("iconHaloWidth").getFloatOrDefault(0f)!!
                mapOf(
                    "l" to arrayOf(geoPoint.latitude(), geoPoint.longitude()),
                    "d" to distanceInPixels,
                    "lid" to props.get("layerId").getStringOrDefault(),
                    "sl" to props.get("sourceLayer").getStringOrDefault(),
                    // "t" to props.get("name").getStringOrDefault(),
                    "ft" to props.get("formatedText").getStringOrDefault(),
                    "ts" to props.get("textSize").getFloatOrDefault(),
                    "tc" to props.get("textColor").getRgbaOrDefault()?.toIntArray(),
                    "thw" to if (textHaloWidth > 0.5f) textHaloWidth else null,
                    "thc" to if (textHaloWidth > 0.5f) props.get("textHaloColor").getRgbaOrDefault()?.toIntArray() else null,
                    "iid" to props.get("iconId").getStringOrDefault(),
                    "is" to props.get("iconSize").getFloatOrDefault(),
                    "ic" to props.get("iconColor").getRgbaOrDefault()?.toIntArray(),
                    "ihw" to if (iconHaloWidth > 0.5) iconHaloWidth else null,
                    "ihc" to if (iconHaloWidth > 0.5) props.get("iconHaloColor").getRgbaOrDefault()?.toIntArray() else null,
                )
            }

            val mapper = jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
            val labelsJson = mapper.writeValueAsString(labels)
            lastRequestedLabels = labelsJson

            updateOutput()
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
            val styleId = if (darkMode) R.raw.bing_maps_v9_china_dark else R.raw.bing_maps_v9_china
            Utils.readRawResource(this, styleId)
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
                    "\"labels\":$lastRequestedLabels," +
                    "\"zoom\":${zoom + 1}" +
                    "}"
            outputText.text = outputStr
            Timber.tag(TAG).i("outputStr=$outputStr")
        }
    }

    private val commands = CommandEvaluator(object : CommandExecutor {
        override fun beginCommands() {
            super.beginCommands()
            lastRequestedLabels = "[]"
        }

        override fun setAutomationMode(enable: Boolean) {
            Timber.tag(TAG).i("setAutomationMode(enable: ${enable})")
            // nothing to do.
        }

        override fun setScene(scene: Scene) {
            Timber.tag(TAG).i("setScene(scene: ${scene})")
            mapView.getMapAsync {
                it.cameraPosition =
                    CameraPosition.Builder().target(LatLng(scene.lat, scene.lon)).zoom(scene.zoom - 1)
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
            val i = max(0, index)
            selectFilter(i)
        }

        override fun extractLabels() {
            extractScreenLabels()
        }
    })
}
