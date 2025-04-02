//
//  PresetFilter.m
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/14.
//

#import "PresetFilter.h"
#import "FilterChain.h"
#import <MapLibre/MLNMapView.h>

@implementation PresetFilter

+(instancetype)filterWithTitle:(NSString*)title query:(NSString*)query {
    return [[PresetFilter alloc] initWithTitle:title query:query];
}
-(instancetype)initWithTitle:(NSString*)title query:(NSString*)query {
    self = [super init];
    if (self) {
        self->_title = title;
        self->_query = query;
    }
    return self;
}

-(void) doFilterWithMapView:(MLNMapView*)mapView {
    FilterChain* filters = [FilterChain chain];
    NSArray<NSString*>* expressions = [_query componentsSeparatedByString:@","];
    for (NSString* it in expressions) {
        NSString* exp = [it stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet];
        if (![exp length]) {
            continue;
        }
        BOOL negative = [exp hasPrefix:@"!"];
        NSString* conditionName = negative ? [exp substringFromIndex:1] : exp;
        NSArray<FilterCondition*>* compositeCondition = [[FilterCondition namedConditions] objectForKey:conditionName];
        NSAssert([compositeCondition count], ([NSString stringWithFormat:@"Invalid condition name: %@", conditionName]));
        for (FilterCondition* condition in compositeCondition) {
            [filters addCondition:condition negative:negative];
        }
    }
    [filters doFilterWithMapView:mapView];
}

@end

@implementation PresetFilter (AllFilters)

NSArray<PresetFilter*>* _allFilters = nil;

+(NSArray<PresetFilter*>*) allFilters {
    static dispatch_once_t tk;
    dispatch_once(&tk, ^{
        _allFilters = @[
            [PresetFilter filterWithTitle:@"0. 默认样式" query:@""],
            [PresetFilter filterWithTitle:@"1. 只显示未包含地势的底图" query:@"!default,base,"],
            [PresetFilter filterWithTitle:@"2. 只隐藏地势" query:@"!hillShading,"],
            [PresetFilter filterWithTitle:@"3. 显示标签并隐藏道路，道路编号，水域，地势，界线" query:@"!default,waterName,continentName,countryRegionName,islandName,cityName,roadName,"],
            [PresetFilter filterWithTitle:@"4. 隐藏地势和水域" query:@"!hillShading,!water,"],
            [PresetFilter filterWithTitle:@"5. 空白底图且只显示国界线" query:@"!default,countryRegion,"],
            [PresetFilter filterWithTitle:@"6. 空白底图且只显示铁路线" query:@"!default,railway,"],
            [PresetFilter filterWithTitle:@"7. 空白底图且只显示大洲名" query:@"!default,continentName,"],
            [PresetFilter filterWithTitle:@"8. 空白底图且只显示水域名" query:@"!default,waterName,"],
            [PresetFilter filterWithTitle:@"9. 空白底图且只显示国家名" query:@"!default,countryRegionName,"],
            [PresetFilter filterWithTitle:@"10.空白底图且只显示首都名及其点状符号" query:@"!default,capitalCityName,"],
            [PresetFilter filterWithTitle:@"11.空白底图且只显示省会名及其点状符号" query:@"!default,admin1CityName,"],
            [PresetFilter filterWithTitle:@"12.空白底图且只显示地级市名及其点状符号" query:@"!default,admin2CityName,majorCityName,"],
            [PresetFilter filterWithTitle:@"13.空白底图且只显示县区名及其点状符号" query:@"!default,minorCityName,"],
            [PresetFilter filterWithTitle:@"14.空白底图且显示县区名,乡镇名,村名及其点状符号" query:@"!default,minorCityName,townName,villageName,"],
            [PresetFilter filterWithTitle:@"15.空白底图且显示地级市名,县区名及其点状符号" query:@"!default,admin2CityName,majorCityName,minorCityName,"],
            [PresetFilter filterWithTitle:@"16.空白底图且显示国外城市名及其点状符号" query:@"!default,cityName,"],
            [PresetFilter filterWithTitle:@"17.空白底图且显示国家名,国外城市名及其点状符号" query:@"!default,countryRegionName,cityName,"],
            [PresetFilter filterWithTitle:@"18.陆地空白色且显示水域形状,水域名,界线" query:@"!default,water,island,waterName,countryRegion,"],
            [PresetFilter filterWithTitle:@"19.陆地空白色且显示水域形状,界线,道路,隐藏铁路" query:@"!default,water,island,countryRegion,road,!railway,"],
            [PresetFilter filterWithTitle:@"20.陆地空白色且隐藏地势,道路编号,路名,道路" query:@"!land,!base,water,island,!road,!roadName,!roadShield,"],
            [PresetFilter filterWithTitle:@"21.显示未包含地势和绿地的底图,岛屿名" query:@"!default,land,base,!reserve,islandName,"],
            [PresetFilter filterWithTitle:@"22.显示未包含地势的底图,城市名,水域名,岛屿名" query:@"!buildings,!hillShading,!continentName,!countryRegion,!countryRegionName,!road,!roadName,!roadShield,!landmark,"],
        ];
    });
    return _allFilters;
}

@end
