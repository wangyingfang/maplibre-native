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
    PresetFilter("1. 默认样式", ""),
    PresetFilter("2. 示界线", "!default,countryRegion,"),
    PresetFilter("3. 未包含地势的底图", "!default,base,"),
    PresetFilter("4. 未包含地势的底图，界线", "!default,base,countryRegion,"),
    PresetFilter("5. 未包含地势的底图，界线，洲名，大洋名", "!default,base,seaName,continentName,"),
    PresetFilter("6. 未包含地势的底图，界线，水域名", "!default,base,waterName,countryRegion,"),
    PresetFilter("7. 未包含地势的底图，国家名", "!default,base,countryRegionName,"),
    PresetFilter("8. 未包含地势的底图，水域名", "!default,base,waterName,"),
    PresetFilter("9. 未包含地势的底图，岛屿名", "!default,base,islandName,"),
    PresetFilter("10.未包含地势的底图，首都注释和点状符号", "!default,base,capitalCityName,"),
    PresetFilter("11.未包含地势的底图，省会，国外普通城市注释和点状符号", "!default,base,admin1CityName,majorCityName,"),
    PresetFilter("12.未包含地势的底图，村级注释和点状符号", "!default,base,villageName,"),
    PresetFilter("13.未包含地势的底图，区县注释和点状符号", "!default,base,minorCityName,"),
    PresetFilter("14.未包含地势的底图，地级市注释和点状符号", "!default,base,admin2CityName,majorCityName,"),
    PresetFilter("15.未包含地势的底图，地级市,县区注释和点状符号", "!default,base,admin2CityName,majorCityName,minorCityName,"),
    PresetFilter("16.未包含地势的底图，界线，地级市注释和点状符号", "!default,base,countryRegion,admin2CityName,majorCityName,"),
    PresetFilter("17.隐藏地势", ""), // NOTE There is no hill shading layer(s) in current map styles.
    PresetFilter("18.隐藏地势，道路编号，路名", "!roadName,!roadShield,"),
    PresetFilter("19.未包含地势的底图，山峰", ""), // NOTE There is no hill shading/label layer(s) in current map styles.
    PresetFilter("20.未包含地势的底图，城市名和点状符号", "!default,base,cityName,"),
    PresetFilter("21.未包含地势的底图，界线，城市名和点状符号", "!default,base,countryRegion,cityName,"),
    PresetFilter("22.未包含地势的底图，国家名，地区名，城市名和点状符号", "!default,base,countryRegionName,cityName,"),
    PresetFilter("23.未包含地势的底图，界线，道路", "!default,base,countryRegion,road,"),
    PresetFilter("24.省会和点状符号（白天）", "!default,admin1CityName,"),
    PresetFilter("25.省会和点状符号（黑夜）", "!default,admin1CityName,"), // dup
    PresetFilter("26.首都和点状符号（白天）", "!default,capitalCityName,"),
    PresetFilter("27.首都和点状符号（黑夜）", "!default,capitalCityName,"), // dup
    PresetFilter("28.隐藏地势和水域", "!water,capitalCityName,"),
    PresetFilter("29.显示标签并隐藏道路，道路编号，水域，地势，界线", "!default,waterName,continentName,countryRegionName,islandName,cityName,roadName,"),
)
