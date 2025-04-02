//
//  PresetFilter.h
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/14.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class MLNMapView;

@interface PresetFilter : NSObject

@property (nonatomic, readonly) NSString* title;
@property (nonatomic, readonly) NSString* query;

+(instancetype)filterWithTitle:(NSString*)title query:(NSString*)query;
-(instancetype)initWithTitle:(NSString*)title query:(NSString*)query;

-(void) doFilterWithMapView:(MLNMapView*)mapView;

@end

@interface PresetFilter (AllFilters)

+(NSArray<PresetFilter*>*) allFilters;

@end

NS_ASSUME_NONNULL_END
