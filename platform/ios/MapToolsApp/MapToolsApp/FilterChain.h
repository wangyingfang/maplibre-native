//
//  FilterChain.h
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/12.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FilterCondition : NSObject

@property (nonatomic, readonly) NSString* pattern;
@property (nonatomic, readonly) NSString* expression;

+(instancetype)conditionWithPattern:(NSString*)pattern;
+(instancetype)conditionWithPattern:(NSString*)pattern expression:(NSString*)expression;
-(instancetype)initWithPattern:(NSString*)pattern expression:(nullable NSString*)expression;
@end

@interface FilterCondition (NamedConditions)

+(NSDictionary<NSString*, NSArray<FilterCondition*>*>*) namedConditions;

@end

@interface FilterExpression : NSObject

@property (nonatomic, readonly, assign) BOOL negative;
@property (nonatomic, readonly) NSString* expression;

+(instancetype)expression:(NSString*)expression negative:(BOOL)negative;
-(instancetype)initWithExpression:(NSString*)expression negative:(BOOL)negative;

@end

@class FilterChain;
@class MLNMapView;

@interface Filter : NSObject

@property(nonatomic, weak) FilterChain* chain;
@property(nonatomic) NSString* pattern;
@property(nonatomic) NSMutableArray<FilterExpression*>* conditions;

+(instancetype)filterWithChain:(FilterChain*)chain condition:(FilterCondition*)condition;
+(instancetype)filterWithChain:(FilterChain*)chain condition:(FilterCondition*)condition negative:(BOOL)negative;
-(instancetype)initWithChain:(FilterChain*)chain condition:(FilterCondition*)condition negative:(BOOL)negative;

-(void)combineCondition:(FilterCondition*)condition;
-(void)combineCondition:(FilterCondition*)condition negative:(BOOL)negative;

-(BOOL)doFilterWithLayerId:(NSString*)layerId type:(NSString*)type sourceLayer:(NSString*)sourceLayer;

@end

@interface FilterChain : NSObject

@property(nonatomic) NSMutableArray<Filter*>* filters;
@property(nonatomic, readonly) MLNMapView* mapView;

+(instancetype)chain;
+(instancetype)chainWithCondition:(FilterCondition*)condition;
+(instancetype)chainWithCondition:(FilterCondition*)condition negative:(BOOL)negative;
-(instancetype)initWithCondition:(FilterCondition*)condition;
-(instancetype)initWithCondition:(FilterCondition*)condition negative:(BOOL)negative;

-(void)addCondition:(FilterCondition*)condition;
-(void)addCondition:(FilterCondition*)condition negative:(BOOL)negative;

-(void)doFilterWithMapView:(MLNMapView*)mapView;

+(void)resetAfterStyleChanged;

@end

NS_ASSUME_NONNULL_END
 
