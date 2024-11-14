//
//  ViewController.m
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/5/30.
//

#import "ViewController.h"
#import <MapLibre/MLNMapView.h>
#import <MapLibre/MLNFeature.h>
#import <MapLibre/MLNMapProjection.h>
#include <math.h>
#import "CommandEvaluator.h"
#import "StatusWatcher.h"
#import "PresetFilter.h"

@interface ViewController () <CommandExecutor, StatusWatcherCallback>

@property (nonatomic) IBOutlet MLNMapView* mapView;
@property (nonatomic) IBOutlet UIButton* optionsButton;
@property (nonatomic) IBOutlet UITextView* inputTextView;
@property (nonatomic) IBOutlet UILabel* outputLabel;

@property (nonatomic) NSString* labels;
@property (nonatomic) NSString* status;
@property (nonatomic) StatusWatcher* statusWatcher;

-(IBAction)submitBtnAction:(id)sender;

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    _labels = @"[]";
    _status = @"";

    // Setup MapView.
    [[_mapView logoView] setHidden:YES];
    [[_mapView attributionButton] setHidden:YES];
    _mapView.minimumZoomLevel = 3 - 1;
    _mapView.maximumZoomLevel = 22 - 1;
    [self setStyle:NO];
    _statusWatcher = [[StatusWatcher alloc] initWithCallback:self debounce:YES];
    [_statusWatcher attachToMapView:_mapView];

    // Setup options menu.
    NSMutableArray<UIAction*>* filterActions = [NSMutableArray array];
    for (PresetFilter* pf in [PresetFilter allFilters]) {
        [filterActions addObject:[UIAction actionWithTitle:pf.title image:nil identifier:nil handler:^(UIAction *action) {
            [pf doFilterWithMapView:self->_mapView];
        }]];
    }
    
    UIMenu* menu = [UIMenu menuWithTitle:@"Options" children:@[
        [UIMenu menuWithTitle:@"Select styles" children:@[
            [UIAction actionWithTitle:@"Road light" image:nil identifier:nil handler:^(UIAction *action) {
                [self setStyle:NO];
            }],
            [UIAction actionWithTitle:@"Road dark" image:nil identifier:nil handler:^(UIAction *action) {
                [self setStyle:YES];
            }],
        ]],
        [UIMenu menuWithTitle:@"Select filters" children:filterActions],
        [UIAction actionWithTitle:@"Test1" image:nil identifier:nil handler:^(UIAction *action) {
            PresetFilter* filter = [PresetFilter filterWithTitle:@"5. 空白底图且只显示国界线" query:@"!countryRegionName"];
            [filter doFilterWithMapView:self->_mapView];
        }],
    ]];
    _optionsButton.menu = menu;
    _optionsButton.showsMenuAsPrimaryAction = YES;
}

-(IBAction)submitBtnAction:(id)sender {
    CommandEvaluator* evaluator = [[CommandEvaluator alloc] initWithExecutor:self];
    NSString* inputStr = _inputTextView.text;
    [_inputTextView resignFirstResponder];
    if ([inputStr length]) {
        [evaluator evaluate:inputStr];
        _inputTextView.text = @"";
    }
}

-(void) setStyle:(BOOL)darkMode {
    NSString* resName = darkMode ? @"bing_maps_v9_china_dark" : @"bing_maps_v9_china";
    NSString* resPath = [NSBundle.mainBundle pathForResource:resName ofType:@"json"];
    NSURL* resUrl = resPath ? [NSURL fileURLWithPath:resPath] : nil;
    if (!resUrl) {
        NSLog(@"Error loading style file: %@", resName);
        return;
    }
    _mapView.styleURL = resUrl;
}

-(void) setCameraWithLat:(double)lat lon:(double)lon zoom:(double)zoom {
    [_mapView setCenterCoordinate:CLLocationCoordinate2DMake(lat, lon) zoomLevel:zoom animated:NO];
}

-(void) extractLabels {
    // This local helper used to convert rgba (0.0-1.0) to rgba (0-255).
    NSArray<NSNumber*>* (^cvtColor)(NSArray<NSNumber*>*) = ^NSArray<NSNumber*>* (NSArray<NSNumber*>* input) {
        if (!input) return nil;
        NSCAssert(input.count == 4, @"Only accept 4 color components");
        NSMutableArray* result = [NSMutableArray arrayWithCapacity:input.count];
        [input enumerateObjectsUsingBlock:^(NSNumber * obj, NSUInteger idx, BOOL * stop) {
            [result addObject:[NSNumber numberWithInt:(int)(obj.floatValue * 255)]];
        }];
        return result;
    };
    // This local helper helper used to calculate distance between two 2d points.
    CGFloat (^calcDist)(CGFloat, CGFloat, CGFloat, CGFloat) = ^CGFloat (CGFloat x1, CGFloat y1, CGFloat x2, CGFloat y2) {
        return sqrtf(powf(x2 - x1, 2) + powf(y2 - y1, 2));
    };

    CGRect screenRect = _mapView.bounds;
    CGPoint screenCenter = CGPointMake(screenRect.size.width / 2, screenRect.size.height / 2);
    NSArray* features = [_mapView visibleFeaturesInRect:screenRect];
    NSMutableArray* results = [NSMutableArray array];
    for (id<MLNFeature> feature in features) {
        if ([feature isKindOfClass:MLNPointFeature.class]) {
            MLNPointFeature* pointFeature = (MLNPointFeature*)feature;
            NSString* name = [pointFeature attributeForKey:@"name"];
            NSNumber* textSize = [pointFeature attributeForKey:@"textSize"];
            NSArray<NSNumber*>* textColor = cvtColor([pointFeature attributeForKey:@"textColor"]);
            if (name && textSize && textColor) {
                CLLocationCoordinate2D coordinate = pointFeature.coordinate;
                CGPoint pixels = [_mapView.mapProjection convertCoordinate:coordinate];
                CGFloat distanceInPixels = calcDist(screenCenter.x, screenCenter.y, pixels.x, pixels.y);
                NSString* formattedText = [pointFeature attributeForKey:@"formatedText"];
                NSMutableDictionary* item = [NSMutableDictionary dictionaryWithDictionary:@{
                    @"l": @[[NSNumber numberWithDouble:coordinate.latitude], [NSNumber numberWithDouble:coordinate.longitude]],
                    @"d": [NSNumber numberWithDouble:distanceInPixels],
                    @"ft": formattedText,
                    @"ts": textSize,
                    @"tc": textColor,
                }];
                NSNumber* textHaloWidth = [pointFeature attributeForKey:@"textHaloWidth"];
                if (textHaloWidth && textHaloWidth.floatValue > 0.5f) {
                    NSArray<NSNumber*>* textHaloColor = cvtColor([pointFeature attributeForKey:@"textHaloColor"]);
                    [item addEntriesFromDictionary:@{
                        @"thw": textHaloWidth,
                        @"thc": textHaloColor
                    }];
                }
                NSString* iconId = [pointFeature attributeForKey:@"iconId"];
                NSNumber* iconSize = [pointFeature attributeForKey:@"iconSize"];
                if (iconId && iconSize.floatValue > 0.5) {
                    NSArray<NSNumber*>* iconColor = cvtColor([pointFeature attributeForKey:@"iconColor"]);
                    [item addEntriesFromDictionary:@{
                        @"iid": iconId,
                        @"is": iconSize,
                        @"ic": iconColor,
                    }];
                    NSNumber* iconHaloWidth = [pointFeature attributeForKey:@"iconHaloWidth"];
                    if (iconHaloWidth && iconHaloWidth.floatValue > 0.5f) {
                        NSArray<NSNumber*>* iconHaloColor = cvtColor([pointFeature attributeForKey:@"iconHaloColor"]);
                        [item addEntriesFromDictionary:@{
                            @"ihw": iconHaloWidth,
                            @"ihc": iconHaloColor
                        }];
                    }
                }
                [results addObject:item];
            }
        }
    }
    NSError* parseError = nil;
    NSData* jsonData = [NSJSONSerialization dataWithJSONObject:results options:0 error:&parseError];
    if (parseError) {
        NSLog(@"parseError=%@", parseError);
        _labels = @"[]";
    } else {
        NSString* jsonStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        _labels = jsonStr;
    }
}

-(void) updateOutput {
    CLLocationCoordinate2D loc = _mapView.centerCoordinate;
    double zoom = _mapView.zoomLevel;
    CGSize mapSize = _mapView.bounds.size;
    CLLocationCoordinate2D northWest = [_mapView.mapProjection convertPoint:CGPointZero];
    CLLocationCoordinate2D southEast = [_mapView.mapProjection convertPoint:CGPointMake(mapSize.width - 1, mapSize.height - 1)];
    NSString* outputStr = [NSString stringWithFormat:
                           @"{"
                           @"\"status\":\"%@\","
                           @"\"bounds\":[%f,%f,%f,%f],"
                           @"\"center\":[%f,%f],"
                           @"\"zoom\":%f,"
                           @"\"labels\":%@"
                           @"}"
                           ,
                           _status,
                           northWest.latitude, northWest.longitude, southEast.latitude, southEast.longitude,
                           loc.latitude, loc.longitude,
                           zoom + 1,
                           _labels
                           
    ];
    _outputLabel.text = outputStr;
    NSLog(@"outputStr=%@", outputStr);
}

-(void) evaluator:(nonnull CommandEvaluator*)evaluator setAutomationMode:(BOOL) enabled {
    // TODO Show/hide those elements that obscure the map view.
    NSLog(@"Set automation mode to %i", enabled);
}

-(void) evaluator:(nonnull CommandEvaluator*)evaluator setTheme:(NSString*) themeName {
    NSLog(@"Set theme to %@", themeName);
    BOOL isDarkStyle = [themeName isEqualToString:@"roadDark"];
    [self setStyle:isDarkStyle];
}

-(void) evaluator:(nonnull CommandEvaluator*)evaluator setSceneWithLat:(double)lat lon:(double)lon zoom:(double)zoom {
    NSLog(@"Set scene to lat:%f, lon:%f, zoom:%f", lat, lon, zoom);
    [self setCameraWithLat:lat lon:lon zoom:zoom];
}

-(void) evaluator:(nonnull CommandEvaluator*)evaluator updateStyles:(nonnull NSArray<ArgStyle*>*)styles {
    
}

-(void) evaluator:(nonnull CommandEvaluator*)evaluator setStyle:(NSInteger)index {
    NSArray<PresetFilter*>* presetFilters = [PresetFilter allFilters];
    index = MIN(MAX(0, index), [presetFilters count]);
    PresetFilter* filter = [[PresetFilter allFilters] objectAtIndex:index];
    [filter doFilterWithMapView:_mapView];
}

-(void) evaluatorExtractLabels:(CommandEvaluator*)evaluator {
    [self extractLabels];
    [self updateOutput];
}

-(void) evaluatorBegin:(nonnull CommandEvaluator*)evaluator {
    _labels = @"[]";
}

-(void) evaluatorEnd:(nonnull CommandEvaluator*)evaluator {
}

- (void)watcher:(nonnull StatusWatcher *)watcher statusChangedTo:(nonnull NSString *)status {
    NSLog(@"Loading status changed to %@", status);
    _status = status;
    [self updateOutput];
}

@end
