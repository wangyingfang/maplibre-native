//
//  FilterChain.m
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/12.
//

#import "FilterChain.h"
#import <MapLibre/MLNMapView.h>
#import <MapLibre/MLNBackgroundStyleLayer.h>
#import <MapLibre/MLNSymbolStyleLayer.h>

@implementation NSObject (Internal)

-(NSString*)toJsonStringPrettyPrinted:(BOOL)prettyPrinted {
    NSData* data = [NSJSONSerialization dataWithJSONObject:self options:prettyPrinted ? NSJSONWritingPrettyPrinted : 0 error:nil];
    return data ? [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] : nil;
}

@end

@implementation NSArray (Internal)
- (id)findObjectUsingBlock:(BOOL (NS_NOESCAPE ^)(id))block {
    for (id it in self) if (block(it)) return it;
    return nil;
}
@end

@interface VisibleLayer: NSObject

@property(nonatomic, readonly) NSString* layerId;
@property(nonatomic, readonly) NSString* type;
@property(nonatomic, readonly) NSString* sourceLayer;
@property(nonatomic, readonly) id filterExpression;

+(instancetype)layerWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer filterExpression:(id)filterExpression;
-(instancetype)initWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer filterExpression:(id)filterExpression;

@end

@implementation VisibleLayer

+(instancetype)layerWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer filterExpression:(id) filterExpression {
    return [[VisibleLayer alloc] initWithLayerId:layerId type:type sourceLayer:sourceLayer filterExpression:filterExpression];
}
-(instancetype)initWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer filterExpression:(id)filterExpression {
    self = [super init];
    if (self) {
        _layerId = layerId;
        _type = type;
        _sourceLayer = sourceLayer;
        _filterExpression = filterExpression;
    }
    return self;
}


@end

@implementation FilterCondition

+(instancetype)conditionWithPattern:(NSString*)pattern {
    return [[FilterCondition alloc] initWithPattern:pattern expression:nil];
}

+(instancetype)conditionWithPattern:(NSString*)pattern expression:(NSString*)expression {
    return [[FilterCondition alloc] initWithPattern:pattern expression:expression];
}

-(instancetype)initWithPattern:(NSString*)pattern expression:(nullable NSString*)expression {
    self = [super init];
    if (self) {
        _pattern = pattern;
        _expression = expression;
    }
    return self;
}

@end

@implementation FilterExpression

+(instancetype)expression:(NSString*)expression negative:(BOOL)negative {
    return  [[FilterExpression alloc] initWithExpression:expression negative:negative];
}

-(instancetype)initWithExpression:(NSString*)expression negative:(BOOL)negative {
    self = [super init];
    if (self) {
        _expression = expression;
        _negative = negative;
    }
    return self;
}

@end

@interface FilterChain (Private)
-(NSArray<VisibleLayer*>*)defaultVisibleMapLayers;
@end

@implementation Filter

+(instancetype)filterWithChain:(FilterChain*)chain condition:(FilterCondition*)condition {
    return [[Filter alloc] initWithChain:chain condition:condition negative:NO];
}

+(instancetype)filterWithChain:(FilterChain*)chain condition:(FilterCondition*)condition negative:(BOOL)negative {
    return [[Filter alloc] initWithChain:chain condition:condition negative:negative];
}

-(instancetype)initWithChain:(FilterChain*)chain condition:(FilterCondition*)condition negative:(BOOL)negative {
    self = [super init];
    if (self) {
        _chain = chain;
        _pattern = condition.pattern;
        _conditions = [NSMutableArray arrayWithObject:[FilterExpression expression:condition.expression negative:negative]];
    }
    return self;
}

-(void)combineCondition:(FilterCondition*)condition {
    [self combineCondition:condition negative:NO];
}

-(void)combineCondition:(FilterCondition*)condition negative:(BOOL)negative {
    if (![condition.pattern isEqualToString:_pattern]) {
        @throw [NSException exceptionWithName:@"Combine condition failed" reason:@"Pattern mismatch" userInfo:nil];
    }
    [_conditions addObject:[FilterExpression expression:condition.expression negative:negative]];
}

-(BOOL)isLayerMatchedWithLayerId:(NSString*)layerId sourceLayer:(NSString*)sourceLayer {
    BOOL (^test)(NSString*, NSString*) = ^BOOL(NSString* v, NSString* p) {
        if (![p length] || [p isEqualToString:v]) return YES;
        NSError* error = nil;
        NSRegularExpression* regex = [NSRegularExpression regularExpressionWithPattern:p options:NSRegularExpressionAnchorsMatchLines error:&error];
        NSTextCheckingResult* match = [regex firstMatchInString:v options:0 range:NSMakeRange(0, [v length])];
        return match && match.range.length == v.length;
    };
    NSArray<NSString*>* patterns = [[self->_pattern stringByAppendingString:@";"] componentsSeparatedByString:@";"];
    NSString* layerIdFilter = patterns.count > 0 ? patterns[0] : @"";
    NSString* sourceLayerFilter = patterns.count > 1 ? patterns[1] : @"";
    return test(layerId, layerIdFilter) && test(sourceLayer, sourceLayerFilter);
}

-(BOOL)doFilterWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer {
    MLNMapView* mapView = [_chain mapView];
    NSAssert(mapView, @"Map instance not been set.");
    if (![self isLayerMatchedWithLayerId:layerId sourceLayer:sourceLayer]) {
        return NO;
    }
    
    VisibleLayer* visibileLayer = [[_chain defaultVisibleMapLayers] findObjectUsingBlock:^BOOL(VisibleLayer* obj) {
        return [obj.layerId isEqualToString:layerId];
    }];
    MLNStyleLayer* layer = visibileLayer ? [mapView.style layerWithIdentifier:visibileLayer.layerId] : nil;
    NSAssert(layer, @"the layer could not be found.");
    if (!layer) {
        return NO;
    }
    if ([layer isKindOfClass:MLNVectorStyleLayer.class]) {
        NSMutableArray<id>* positiveList = [NSMutableArray array];
        NSMutableArray<id>* negativeList = [NSMutableArray array];
        
        for (FilterExpression* condition in _conditions) {
            id exp = [condition.expression length] ? [NSJSONSerialization JSONObjectWithData:[condition.expression dataUsingEncoding:NSUTF8StringEncoding] options:0 error:nil] : nil;
            if (condition.negative) {
                [negativeList addObject:exp ? @[@"!", exp] : [NSNumber numberWithBool:NO]];
            } else {
                [positiveList addObject:(exp ? exp : [NSNumber numberWithBool:YES])];
            }
        }
        id expression = nil;
        if ([positiveList count]) {
            expression = [positiveList count] == 1 ? positiveList[0] : [@[@"any"] arrayByAddingObjectsFromArray:positiveList];
        }
        if ([negativeList count]) {
            if (expression) {
                [negativeList addObject:expression];
            }
            expression = [negativeList count] == 1 ? negativeList[0] : [@[@"all"] arrayByAddingObjectsFromArray:negativeList];
        }
        id defaultExpression = visibileLayer.filterExpression;
        if (defaultExpression != nil) {
            expression = expression ? @[@"all", defaultExpression, expression] : defaultExpression;
        }
        NSLog(@"layerId=%@", layerId);
        if ([expression isKindOfClass:NSNumber.class]) {
            expression = @[@"all", expression];
        }
        NSLog(@"expression=%@", [expression toJsonStringPrettyPrinted:YES]);

        [((MLNVectorStyleLayer*)layer) setFilterExpression:expression];
    } else {
        FilterExpression* lastCondition = [_conditions lastObject];
        layer.visible = !lastCondition.negative;
        NSLog(@"layerId=%@", layerId);
        NSLog(@"visible=%d", !lastCondition.negative);
    }
    
    return YES;
}

@end


@implementation FilterChain

+(instancetype)chain {
    return [[FilterChain alloc] init];
}

-(instancetype) init {
    self = [super init];
    if (self) {
        _filters = [NSMutableArray array];
    }
    return self;
}

+(instancetype)chainWithCondition:(FilterCondition*)condition {
    return [[FilterChain alloc] initWithCondition:condition];
}

+(instancetype)chainWithCondition:(FilterCondition*)condition negative:(BOOL)negative {
    return [[FilterChain alloc] initWithCondition:condition negative:negative];
}

-(instancetype)initWithCondition:(FilterCondition*)condition {
    return [self initWithCondition:condition negative:NO];
}

-(instancetype)initWithCondition:(FilterCondition*)condition negative:(BOOL)negative {
    self = [super init];
    if (self) {
        _filters = [NSMutableArray arrayWithObject:[Filter filterWithChain:self condition:condition negative:negative]];
    }
    return self;
}

-(void)addCondition:(FilterCondition*)condition {
    [self addCondition:condition negative:NO];
}

-(void)addCondition:(FilterCondition*)condition negative:(BOOL)negative {
    Filter* filter = [_filters findObjectUsingBlock:^BOOL(Filter* obj) {
        return [obj.pattern isEqualToString:condition.pattern];
    }];
    if (filter) {
        [filter combineCondition:condition negative:negative];
    } else {
        [_filters addObject:[Filter filterWithChain:self condition:condition negative:negative]];
    }
}

-(void)doFilterWithMapView:(MLNMapView*)mapView {
    _mapView = mapView;
    
    NSArray<VisibleLayer*>* visibleLayers = [self defaultVisibleMapLayers];
    
    NSMutableArray<NSString*>* layerIdsUpdated = [NSMutableArray array];
    for (Filter* filter in _filters) {
        for (VisibleLayer* visibleLayer in visibleLayers) {
            if ([filter doFilterWithLayerId:visibleLayer.layerId type:visibleLayer.type sourceLayer:visibleLayer.sourceLayer]) {
                [layerIdsUpdated addObject:visibleLayer.layerId];
            }
        }
    }
    // Reset layers those are not effected.
    NSArray<MLNStyleLayer*>* layers = [_mapView.style layers];
    NSAssert([layers count], @"Empty layers???");
    for (VisibleLayer* visibleLayer in visibleLayers) {
        if (![layerIdsUpdated containsObject:visibleLayer.layerId]) {
            MLNStyleLayer* layer =  [layers findObjectUsingBlock:^BOOL(MLNStyleLayer* it) {
                return [it.identifier isEqualToString:visibleLayer.layerId];
            }];
            NSAssert(layer, @"Could not found the layer?");
            if ([layer isKindOfClass:MLNVectorStyleLayer.class]) {
                MLNVectorStyleLayer* vectorLayer = (MLNVectorStyleLayer*)layer;
                id expression = visibleLayer.filterExpression ? visibleLayer.filterExpression : [NSNumber numberWithBool:true];
                [vectorLayer setFilterExpression:expression];
            } else {
                [layer setVisible:YES];
            }
        }
    }

    _mapView = nil;
}

static NSMutableArray<VisibleLayer*>* _defaultVisibleMapLayers = nil;

-(NSArray<VisibleLayer*>*)defaultVisibleMapLayers {
    if (!_mapView) {
        @throw [NSException exceptionWithName:@"Failed to get visible map layers" reason:@"The map instance not been set" userInfo:nil];
    }
    
    if (!_defaultVisibleMapLayers) {
        NSString* backgroundLayerId = @"microsoft.bing.maps.base.land";
        
        _defaultVisibleMapLayers = [NSMutableArray array];
        NSArray<MLNStyleLayer*>* layers = [_mapView.style layers];
        if ([layers count]) {
            for (MLNStyleLayer* layer in layers) {
                NSString* layerId = layer.identifier;
                if (![layerId isEqualToString:backgroundLayerId] && layer.visible) {
                    NSString* type = @"rastor";
                    NSString* sourceLayer = @"";
                    id filterExpression = nil;
                    if ([layer isKindOfClass:MLNVectorStyleLayer.class]) {
                        MLNVectorStyleLayer* vectorLayer = (MLNVectorStyleLayer*)layer;
                        type = @"vector";
                        sourceLayer = vectorLayer.sourceLayerIdentifier;
                        filterExpression = vectorLayer.filterExpression;
                    }
                    [_defaultVisibleMapLayers addObject:[VisibleLayer layerWithLayerId:layerId type:type sourceLayer:sourceLayer filterExpression:filterExpression]];
                    NSLog(@"VisibleLayer: id:%@, type:%@, sourceLayer:%@, filterExpression:%@", layerId, type, sourceLayer, filterExpression);
                    
                }
            }
        }
    }
    return _defaultVisibleMapLayers;
}

+(void)resetAfterStyleChanged {
    _defaultVisibleMapLayers = nil;
}

@end

 
@implementation FilterCondition (NamedConditions)

static NSDictionary<NSString*, NSArray<FilterCondition*>*>* _namedConditions = nil;

+(NSDictionary<NSString*, NSArray<FilterCondition*>*>*) namedConditions {
    static dispatch_once_t tk;
    dispatch_once(&tk, ^{
        _namedConditions = @{
            @"default": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing.maps\\..*"]
            ],
            @"land": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.baseFeature.vector_land"]
            ],
            @"base": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing.maps\\.baseFeature\\.([\\w\\d\\-_]+_fill|vector_land);"]
            ],
            @"reserve": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.baseFeature\\.[\\w\\d\\-_]+;reserve|golf_course"]
            ],
            @"hillShading": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.hillShading\\.hillShading;"]
            ],
            @"water": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.baseFeature.generic_water_feature_fill"]
            ],
            @"sea": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.baseFeature.generic_water_feature_fill" expression:@"[\"!\",[\"has\",\"st-et\"]]"]
            ],
            @"lake": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.baseFeature.generic_water_feature_fill" expression:@"[\"==\",[\"get\",\"st-et\"],\"lake\"]"]
            ],
            @"river": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.baseFeature.generic_water_feature_fill" expression:@"[\"==\",[\"get\",\"st-et\"],\"river\"]"]
            ],
            @"waterName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_water_feature_(labelonly|polygonlabel);"]
            ],
            @"seaName": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.labels.generic_water_feature_labelonly;"]
            ],
            @"lakeName": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.labels.generic_water_feature_polygonlabel" expression:@"[\"==\",[\"get\",\"st-et\"],\"lake\"]"]
            ],
            @"riverName": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.labels.generic_water_feature_polygonlabel" expression:@"[\"==\",[\"get\",\"st-et\"],\"river\"]"]
            ],
            @"continentName": @[
                [FilterCondition conditionWithPattern:@"microsoft.bing.maps.labels.entity_override_continents_for_cn_region_symbol_label"]
            ],
            @"countryRegion": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;country_region"]
            ],
            @"countryRegionName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;country_region"]
            ],
            @"islandName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;island"]
            ],
            @"cityName": @[
                // 首都
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_(sov_capital|beijing)_(labelonly|iconlabel);"],
                // 一般地名
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)"],
                // 乡镇
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_neighborhood_labelonly;"],
                // 台北
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_taipei_(labelonly|iconlabel)"],
                // 桃园
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_taoyuan_(labelonly|iconlabel)"],
                // 新北
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_new_taipei_(labelonly|iconlabel)"],
                // 其它增补城市（已确定：丹东、东港市、二连浩特）
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)"],
            ],
            @"capitalCityName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_(sov_capital|beijing)_(labelonly|iconlabel);"]
            ],
            @"admin1CityName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"admin1cap\"]"],
                // 台北市
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_taipei_(labelonly|iconlabel)"],
            ],
            @"admin2CityName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"admin2cap\"]"],
                // 桃园
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_taoyuan_(labelonly|iconlabel)"],
                // 新北
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_new_taipei_(labelonly|iconlabel)"],
                // 其它增补城市（已确定：丹东）
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"admin2cap\"]"],
            ],
            // 一般城市（境外的直辖市、大城市等. NOTE: 国内无这个分类!）
            @"majorCityName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"city\"]"],
                // 其它增补城市（已确定：东港市、二连浩特）
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"city\"]"],
            ],
            @"minorCityName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"minorcity\"]"],
                // 其它增补城市（已确定：东港市、二连浩特）
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_label_orientation_[\\w\\d\\-_]+_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"minorcity\"]"],
            ],
            @"townName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_neighborhood_labelonly;"],
            ],
            @"villageName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.generic_populated_place_(labelonly|iconlabel)" expression:@"[\"==\",[\"get\",\"cn-ppl\"],\"village\"]"],
            ],
            @"road": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"],
            ],
            @"railway": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.roads\\.[\\w\\d\\-_]+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"],
            ],
            @"roadName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;road[\\w\\d\\-_]*|railway[\\w\\d\\-_]*|tramway"],
            ],
            @"railwayName": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\.[\\w\\d\\-_]+;railway[\\w\\d\\-_]*"],
            ],
            @"roadShield": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\..+_road_shield.*;road"],
            ],
            @"landmark": @[
                [FilterCondition conditionWithPattern:@"microsoft\\.bing\\.maps\\.labels\\..+_landmark(_\\w+)?"],
            ],
        };
    });
    return _namedConditions;
}
@end

