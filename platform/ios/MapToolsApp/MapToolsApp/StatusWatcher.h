//
//  StatusChangedWatcher.h
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/11.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class MLNMapView;
@class StatusWatcher;

@protocol StatusWatcherCallback

-(void)watcher:(StatusWatcher*)watcher statusChangedTo:(NSString*)status;

@end


@interface StatusWatcher : NSObject

-(instancetype)initWithCallback:(id<StatusWatcherCallback>)block debounce:(BOOL)debounce;

-(void)attachToMapView:(MLNMapView*)mapView;
-(void)detachFromMapView;

@end

NS_ASSUME_NONNULL_END
