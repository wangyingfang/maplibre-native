package com.microsoft.maps.v9.toolsapp

import org.maplibre.android.maps.MapLibreMap

class PresetFilter(val title: String, val query: String) {
    fun doFilter(map: MapLibreMap) {
        val filters = FilterChain()
        for (it in query.split(",")) {
            val exp = it.trim()
            if (exp.isEmpty()) {
                continue
            }
            val negative = exp.startsWith('!')
            val conditionName = if (negative) exp.substring(1) else exp
            val condition = filterConditions[conditionName]
                ?: throw RuntimeException("Invalid condition name: $conditionName")
            filters.addConditions(condition, negative)
        }
        filters.doFilter(map)
    }
}

val presetFilters = listOf(
    PresetFilter("0. 默认样式", ""),
    PresetFilter("1. 只显示未包含地势的底图", "!default,base,"),
    PresetFilter("2. 只隐藏地势", "!hillShading,!buildings"),
    PresetFilter("3. 显示标签并隐藏道路，道路编号，水域，地势，界线", "!base,!buildings,!hillShading,!water,!countryRegion,!road,!roadShield,"),
    PresetFilter("4. 隐藏地势和水域", "!hillShading,!water,!buildings"),
    PresetFilter("5. 空白底图且只显示国界线", "!default,countryRegion,"),
    PresetFilter("6. 空白底图且只显示铁路线", "!default,railway,"),
    PresetFilter("7. 空白底图且只显示大洲名", "!default,continentName,"),
    PresetFilter("8. 空白底图且只显示水域名", "!default,waterName,"),
    PresetFilter("9. 空白底图且只显示国家名", "!default,countryRegionName,"),
    PresetFilter("10.空白底图且只显示首都名及其点状符号", "!default,capitalCityName,"),
    PresetFilter("11.空白底图且只显示省会名及其点状符号", "!default,admin1CityName,"),
    PresetFilter("12.空白底图且只显示地级市名及其点状符号", "!default,admin2CityName,majorCityName,"),
    PresetFilter("13.空白底图且只显示县区名及其点状符号", "!default,minorCityName,"),
    PresetFilter("14.空白底图且显示县区名,乡镇名,村名及其点状符号", "!default,minorCityName,townName,villageName,"),
    PresetFilter("15.空白底图且显示地级市名,县区名及其点状符号", "!default,admin2CityName,majorCityName,minorCityName,"),
    PresetFilter("16.空白底图且显示国外城市名及其点状符号", "!default,cityName,"),
    PresetFilter("17.空白底图且显示国家名,国外城市名及其点状符号", "!default,countryRegionName,cityName,"),
    PresetFilter("18.陆地空白色且显示水域形状,水域名,界线", "!default,water,island,waterName,countryRegion,"),
    PresetFilter("19.陆地空白色且显示水域形状,界线,道路,隐藏铁路", "!default,water,island,countryRegion,road,!railway,"),
    PresetFilter("20.陆地空白色且隐藏地势,道路编号,路名,道路", "!land,!base,water,island,!road,!roadName,!roadShield,"),
    PresetFilter("21.显示未包含地势和绿地的底图,岛屿名", "!default,land,base,!reserve,islandName,"),
    PresetFilter("22.显示未包含地势的底图,城市名,水域名,岛屿名", "!hillShading,"),
)
