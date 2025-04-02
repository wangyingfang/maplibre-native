//
//  StatusChangedWatcher.m
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/11.
//

#import "StatusWatcher.h"
#import <MapLibre/MLNMapView.h>
#import <MapLibre/MLNMapViewDelegate.h>

@interface StatusWatcher()<MLNMapViewDelegate>

@property (nonatomic, assign) id<StatusWatcherCallback> callback;
@property (nonatomic, assign) BOOL debounce;
@property (nonatomic, weak) MLNMapView* mapView;

@end

@implementation StatusWatcher

-(instancetype)initWithCallback:(id<StatusWatcherCallback>)callback debounce:(BOOL)debounce {
    self = [super init];
    if (self) {
        _callback = callback;
        _debounce = debounce;
    }
    return self;
}

-(void)attachToMapView:(MLNMapView*)mapView {
    _mapView = mapView;
    if (_mapView) {
        _mapView.delegate = self;
    }
}

-(void)detachFromMapView {
    if (_mapView && _mapView.delegate == self) {
        _mapView.delegate = nil;
    }
}

- (void)mapViewDidBecomeIdle:(MLNMapView *)mapView {
    NSLog(@"mapViewDidBecomeIdle");
    [_callback watcher:self statusChangedTo:@"idle"];
}

- (void)mapView:(MLNMapView *)mapView regionWillChangeWithReason:(MLNCameraChangeReason)reason animated:(BOOL)animated {
    NSLog(@"mapViewCameraWillChange");
    [_callback watcher:self statusChangedTo:@"busy"];
}

- (void)mapViewWillStartLoadingMap:(MLNMapView *)mapView {
    NSLog(@"mapViewWillStartLoadingMap");
    [_callback watcher:self statusChangedTo:@"busy"];
}

- (void)mapViewWillStartRenderingMap:(MLNMapView *)mapView {
    NSLog(@"mapViewWillStartRenderingMap");
    [_callback watcher:self statusChangedTo:@"busy"];
}

- (void)mapViewRegionIsChanging:(MLNMapView *)mapView {
    NSLog(@"mapViewRegionIsChanging");
    [_callback watcher:self statusChangedTo:@"busy"];
}

@end
