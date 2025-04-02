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
data class LayerFilterState(val id: String, val visible: Boolean, val expression: String?)

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
            val expression = layer.getFilterExpression()
            val exp = expression?.toString() ?: ""
            dlf = LayerFilterState(fl.id, visible, exp)
            dlfs[layer.id] = dlf
        }
        if (layer.isVectorLayer()) {
            // Some layers supports filter, so all conditions need to be applied.
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
            var expression: Expression? = null
            if (positives.isNotEmpty()) {
                expression = if (positives.size == 1) positives[0] else Expression.any(*positives.toTypedArray())
            }
            if (negatives.isNotEmpty()) {
                if (expression != null) {
                    negatives.add(expression)
                }
                expression = if (negatives.size == 1) negatives[0] else Expression.all(*negatives.toTypedArray())
            }
            if (!dlf.expression.isNullOrEmpty()) {
                val defaultExpression = Expression.Converter.convert(dlf.expression!!)
                expression = if (expression != null) Expression.all(defaultExpression, expression) else defaultExpression
            }
            layer.setFilterExpression(expression ?: Expression.literal(true))
        } else {
            // Some layers don't support filter and can only be set to visible or hidden, so only the last condition needs to be applied.
            val lastCondition = conditions.last()
            layer.setProperties(
                PropertyValue("visibility", if (lastCondition.negative) "none" else "visible")
            )
        }
        return true
    }
}

class FilterChain(condition: FilterCondition? = null, negative: Boolean? = null) {
    companion object {
        val defaultLayerFilters = mutableMapOf<String, LayerFilterState>()
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

    @OptIn(ExperimentalStdlibApi::class)
    private fun getDefaultVisibleLayers(): List<FilterLayer> {
        val map = this.map ?: throw RuntimeException("map instance not been set")

        if (defaultVisibleLayers.isEmpty()) {
            val backgroundLayerId = "microsoft.bing.maps.base.land"

            // Saves the IDs of layers that are displayed by default, which is used to restore the default state of
            // layers that should not be affected when switching filters.
            defaultVisibleLayers = map.style?.layers
                ?.filter { it.id != backgroundLayerId && it.visibility.getValue() != "none" }
                ?.map { FilterLayer(it.id, it.javaClass.simpleName, it.getSourceLayer()) }
                ?: listOf()
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
            if (layer.isVectorLayer()) {
                val exp = if (dlf.expression.isNullOrEmpty()) Expression.literal(true)
                else Expression.Converter.convert(dlf.expression)
                layer.setFilterExpression(exp)
            } else {
                layer.setProperties(
                    PropertyValue("visibility", if (!dlf.visible) "none" else "visible")
                )
            }
        }
    }
}

val filterConditions = mapOf(
    "default" to listOf(FilterCondition("microsoft\\.bing.maps\\..*")),
    "land" to listOf(FilterCondition("microsoft.bing.maps.baseFeature.vector_land")),
    "base" to listOf(FilterCondition("microsoft\\.bing.maps\\.baseFeature\\.[\\w\\d\\-_]+;")),
    // "reserve" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.(baseFeature|labels)\\.[\\w\\d\\-_]+;reserve|golf_course")),
    "reserve" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.baseFeature\\.[\\w\\d\\-_]+;reserve|golf_course")),
    "buildings" to listOf(FilterCondition("microsoft.bing.maps.buildings.buildings")),
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

    "island" to listOf(FilterCondition("microsoft\\.bing\\.maps\\.baseFeature\\.[\\w\\d\\-_]+;island")),
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
    "capitalCityName" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.generic_(sov_capital|beijing)_(labelonly|iconlabel);"),
        // 其它增补城市（已确定：加德满都、伊斯兰堡）
        FilterCondition(
            "microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)",
            "[\"==\",[\"get\",\"cn-ppl\"],\"sovcap\"]"
        ),
    ),
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
    "railway" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;railway[\\w\\d\\-_]*"),
    ),
    "roadName" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"),
    ),
    "railwayName" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;railway[\\w\\d\\-_]*"),
    ),
    "roadShield" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\..+_road_shield.*;road"),
    ),
    "landmark" to listOf(
        FilterCondition("microsoft\\.bing\\.maps\\.labels\\..+_landmark(_\\w+)?"),
    ),
)
