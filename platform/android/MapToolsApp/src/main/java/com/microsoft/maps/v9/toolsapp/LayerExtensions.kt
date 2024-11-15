package com.microsoft.maps.v9.toolsapp

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer

fun Layer.isVectorLayer(): Boolean {
    return when (this) {
        is CircleLayer -> true
        is FillExtrusionLayer -> true
        is FillLayer -> true
        is HeatmapLayer -> true
        is LineLayer -> true
        is SymbolLayer -> true
        else -> false
    }
}

fun Layer.getFilterExpression(): Expression? {
    return when (this) {
        is CircleLayer -> filter
        is FillExtrusionLayer -> filter
        is FillLayer -> filter
        is HeatmapLayer -> filter
        is LineLayer -> filter
        is SymbolLayer -> filter
        else -> null
    }
}

fun Layer.setFilterExpression(expression: Expression) {
    when (this) {
        is CircleLayer -> setFilter(expression)
        is FillExtrusionLayer -> setFilter(expression)
        is FillLayer -> setFilter(expression)
        is HeatmapLayer -> setFilter(expression)
        is LineLayer -> setFilter(expression)
        is SymbolLayer -> setFilter(expression)
        else -> throw IllegalStateException("Only vector layer can be set filter expression.")
    }
}

fun Layer.getSourceLayer(): String {
    return when (this) {
        is CircleLayer -> sourceLayer
        is FillExtrusionLayer -> sourceLayer
        is FillLayer -> sourceLayer
        is HeatmapLayer -> sourceLayer
        is HillshadeLayer -> sourceLayer
        is LineLayer -> sourceLayer
        is RasterLayer -> sourceLayer
        is SymbolLayer -> sourceLayer
        else -> ""
    }
}
