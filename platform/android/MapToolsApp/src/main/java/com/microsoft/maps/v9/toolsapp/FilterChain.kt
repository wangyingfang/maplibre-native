package com.microsoft.maps.v9.toolsapp

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer

class FilterCondition(val pattern: String, val expression: String? = null) {}
data class FilterExpression(val expression: String, val negative: Boolean)
data class FilterLayer(val id: String, val type: String, val sourceLayer: String)
data class DefaultLayerFilter(val id: String, val visible: Boolean, val expression: String?)

class Filter(val chain: FilterChain, condition: FilterCondition, val negative: Boolean?) {

    val pattern: String = condition.pattern
    val conditions = mutableListOf<FilterExpression>()

    init {
        conditions.add(FilterExpression(condition.expression.orEmpty(), negative ?: false))
    }

    fun combine(condition: FilterCondition, negative: Boolean?) {
        if (pattern != condition.pattern) {
            throw RuntimeException("condition pattern mismatched")
        }
        conditions.add(FilterExpression(condition.expression.orEmpty(), negative ?: false))
    }

    private fun isLayerMatched(lf: FilterLayer): Boolean {
        val test: (String, String) -> Boolean =
            { v, p -> p.isEmpty() || v == p || Regex(p).matches(v) }
        val (layerIdPattern, sourceLayerPattern) = "$pattern;".split(";").take(2)
        return test(lf.id, layerIdPattern) && test(lf.sourceLayer, sourceLayerPattern)
    }

    fun doFilter(fl: FilterLayer): Boolean {
        val getFilterExpression: (Layer) -> Expression? = {
            when (it) {
                is CircleLayer -> it.filter
                is FillExtrusionLayer -> it.filter
                is FillLayer -> it.filter
                is HeatmapLayer -> it.filter
                is LineLayer -> it.filter
                is SymbolLayer -> it.filter
                else -> null
            }
        }

        val map = chain.map ?: throw RuntimeException("map instance not been set")
        if (!isLayerMatched(fl)) {
            return false
        }
        val layer = map.style?.getLayer(fl.id)
            ?: throw RuntimeException("The specified layer#${fl.id} could not found.")
        val dlfs = FilterChain.defaultLayerFilters
        var dlf = dlfs[fl.id]
        if (dlf == null) {
            val visible = layer.visibility.getValue() != "none"
            val expression = getFilterExpression(layer)
            val exp = expression?.toString() ?: ""
            dlf = DefaultLayerFilter(fl.id, visible, exp)
            dlfs[layer.id] = dlf
        }
        try {
            // Some layers supports filter, so all conditions need to be applied.
            val setFilterMethod = layer.javaClass.getMethod("setFilter", Expression::class.java)
            val positives = mutableListOf<Expression>()
            val negatives = mutableListOf<Expression>()
            for (condition in conditions) {
                val exp =
                    if (condition.expression.isNotEmpty()) Expression.Converter.convert(condition.expression) else null
                if (condition.negative) {
                    negatives.add(if (exp != null) Expression.not(exp) else Expression.literal(false))
                } else {
                    positives.add(exp ?: Expression.literal(true))
                }
            }
            var combinedExpression =
                if (positives.isEmpty()) Expression.literal(true) else Expression.any(*positives.toTypedArray())
            if (negatives.isNotEmpty()) {
                combinedExpression =
                    Expression.all(*positives.toTypedArray(), *negatives.toTypedArray())
            }
            if (!dlf.expression.isNullOrEmpty()) {
                combinedExpression = Expression.all(
                    Expression.Converter.convert(dlf.expression!!),
                    combinedExpression
                )
            }
            setFilterMethod.invoke(layer, combinedExpression)
        } catch (ex: NoSuchMethodException) {
            // Some layers don't support filter and can only be set to visible or hidden, so only the last condition needs to be applied.
            val lastCondition = conditions.last()
            layer.setProperties(
                PropertyValue(
                    "visibility",
                    if (lastCondition.negative) "none" else "visible"
                )
            )
        }
        return true
    }
}

class FilterChain(condition: FilterCondition? = null, negative: Boolean? = null) {
    companion object {
        val defaultLayerFilters = mutableMapOf<String, DefaultLayerFilter>()
        var defaultVisibleLayers = listOf<FilterLayer>()

        fun resetAfterStyleChanged() {
            defaultVisibleLayers = listOf<FilterLayer>()
            defaultLayerFilters.clear()
        }
    }

    private val filters = ArrayList<Filter>()
    var map: MapLibreMap? = null

    init {
        if (condition != null) {
            filters.add(Filter(this, condition, negative))
        }
    }

    fun addCondition(condition: FilterCondition, negative: Boolean?): FilterChain {
        filters.find { it.pattern == condition.pattern }?.combine(condition, negative)
            ?: filters.add(Filter(this, condition, negative))
        return this
    }

    fun addConditions(conditions: List<FilterCondition>, negative: Boolean?): FilterChain {
        for (condition in conditions) {
            addCondition(condition, negative)
        }
        return this
    }

    private fun getDefaultVisibleLayers(): List<FilterLayer> {
        val map = this.map ?: throw RuntimeException("map instance not been set")

        val getSourceLayer: (Layer) -> String = {
            when (it) {
                is CircleLayer -> it.sourceLayer
                is FillExtrusionLayer -> it.sourceLayer
                is FillLayer -> it.sourceLayer
                is HeatmapLayer -> it.sourceLayer
                is HillshadeLayer -> it.sourceLayer
                is LineLayer -> it.sourceLayer
                is RasterLayer -> it.sourceLayer
                is SymbolLayer -> it.sourceLayer
                else -> ""
            }
        }

        if (defaultVisibleLayers.isEmpty()) {
            defaultVisibleLayers = map.style?.layers
                ?.filter { it.javaClass.simpleName != "BackgroundLayer" && it.visibility.getValue() != "none" /* NOTE bing maps custom metadata 'delayLoad' */ }
                ?.map {
                    FilterLayer(
                        it.id,
                        it.javaClass.simpleName,
                        getSourceLayer(it)
                    )
                } ?: listOf()
        }
        return defaultVisibleLayers
    }

    fun doFilter(map: MapLibreMap) {
        this.map = map
        val layers = getDefaultVisibleLayers()

        // Apply the filters to the layers.
        val layerIdsUpdated = mutableListOf<String>()
        for (filter in filters) {
            layers.filter { lf -> filter.doFilter(lf) }.forEach { layerIdsUpdated.add(it.id) }
        }
        // Reset the layers those are not effected.
        val dlfs = defaultLayerFilters
        for (fl in layers.filter { !layerIdsUpdated.contains(it.id) }) {
            val dlf = dlfs[fl.id] ?: continue
            val layer = map.style?.layers?.find { it.id == fl.id } ?: throw RuntimeException("The specified layer#${fl.id} could not found.")
            try {
                val setFilterMethod = layer.javaClass.getMethod("setFilter", Expression::class.java)
                val exp = if (dlf.expression.isNullOrEmpty()) Expression.literal(true)
                else Expression.Converter.convert(dlf.expression)
                setFilterMethod.invoke(layer, exp)
            } catch (ex: NoSuchMethodException) {
                layer.setProperties(
                    PropertyValue(
                        "visibility",
                        if (!dlf.visible) "none" else "visible"
                    )
                )
            }
        }
    }
}

val filterConditions = mapOf(
    "default" to listOf(FilterCondition("microsoft\\.bing.maps\\..*")),
    "base" to listOf(FilterCondition("microsoft\\.bing.maps\\.baseFeature\\.[\\w\\d\\-_]+_fill;")),
    "raster" to listOf(FilterCondition("<type:raster>")),
    "hillShading" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.hillShading\\.hillShading;")),

    "water" to listOf(FilterCondition("microsoft.bing.maps.baseFeature.generic_water_feature_fill")),
    "sea" to listOf(FilterCondition("microsoft.bing.maps.baseFeature.generic_water_feature_fill", "[\"!\",[\"has\",\"st-et\"]]")),
    "lake" to listOf(FilterCondition("microsoft.bing.maps.baseFeature.generic_water_feature_fill", "[\"==\",[\"get\",\"st-et\"],\"lake\"]")),
    "river" to listOf(FilterCondition("microsoft.bing.maps.baseFeature.generic_water_feature_fill", "[\"==\",[\"get\",\"st-et\"],\"river\"]")),
    "waterName" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_water_feature_(labelonly|polygonlabel);")),
    "seaName" to listOf(FilterCondition("microsoft.bing.maps.labels.generic_water_feature_labelonly;")),
    "lakeName" to listOf(FilterCondition("microsoft.bing.maps.labels.generic_water_feature_polygonlabel", "[\"==\",[\"get\",\"st-et\"],\"lake\"]")),
    "riverName" to listOf(FilterCondition("microsoft.bing.maps.labels.generic_water_feature_polygonlabel", "[\"==\",[\"get\",\"st-et\"],\"river\"]")),

    "continentName" to listOf(FilterCondition("microsoft.bing.maps.labels.entity_override_continents_for_cn_region_symbol_label")),

    "countryRegion" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;country_region")),
    "countryRegionName" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;country_region")),

    "islandName" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;island")),

    "cityName" to listOf(
        // 首都
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_(sov_capital|beijing)_(labelonly|iconlabel);"),
        // 一般地名
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)"),
        // 乡镇
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_neighborhood_labelonly;"),
        // 台北
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_taipei_(labelonly|iconlabel)"),
        // 桃园
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_taoyuan_(labelonly|iconlabel)"),
        // 新北
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_new_taipei_(labelonly|iconlabel)"),
        // 其它增补城市（已确定：丹东、东港市、二连浩特）
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)"),
    ),
    "capitalCityName" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_(sov_capital|beijing)_(labelonly|iconlabel);")),
    "admin1CityName" to listOf(
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"admin1cap\"]"
        ),
        // 台北市
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_taipei_(labelonly|iconlabel)"),
    ),
    "admin2CityName" to listOf(
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"admin2cap\"]"
        ),
        // 桃园
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_taoyuan_(labelonly|iconlabel)"),
        // 新北
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_new_taipei_(labelonly|iconlabel)"),
        // 其它增补城市（已确定：丹东）
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"admin2cap\"]"
        ),

        ),
    // 一般城市（境外的直辖市、大城市等. NOTE: 国内无这个分类!）
    "majorCityName" to listOf(
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"city\"]"
        ),
        // 其它增补城市（已确定：东港市、二连浩特）
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"city\"]"
        ),
    ),
    "minorCityName" to listOf(
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"minorcity\"]"
        ),
        // 其它增补城市（已确定：东港市、二连浩特）
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"minorcity\"]"
        ),
    ),
    "townName" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_neighborhood_labelonly;"),
    ),
    "villageName" to listOf(
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"village\"]"
        ),
    ),
    "road" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"),
    ),
    "roadName" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\..+_line_.+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"),
    ),
    "roadShield" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\..+_road_shield.*;road"),
    ),
    "landmark" to  listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\..+_landmark(_\\w+)?"),
    ),
)
