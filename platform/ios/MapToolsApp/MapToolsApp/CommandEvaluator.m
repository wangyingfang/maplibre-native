//
//  CommandEvaluator.m
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/11.
//

#import "CommandEvaluator.h"

@implementation ArgStyle

@end

@implementation CommandEvaluator

-(instancetype)initWithExecutor:(id<CommandExecutor>)executor {
    self = [super init];
    if (self) {
        _executor = executor;
    }
    return  self;
}

-(void)evaluate:(NSString*)commandString {
    NSData* data = [commandString dataUsingEncoding:NSUTF8StringEncoding];
    NSError* error;
    NSArray* obj = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
    if (error) {
        NSLog(@"Error parsing command string: %@", error);
        return;
    }
    if (![obj isKindOfClass:NSArray.class]) {
        NSLog(@"Invalid format of command string.");
        return;
    }
    [_executor evaluatorBegin:self];
    for (NSDictionary* item in obj) {
        if (![item isKindOfClass:NSDictionary.class] || ![[item objectForKey:@"type"] isKindOfClass:NSString.class]) {
            NSLog(@"Invalid format of command item.");
            continue;
        }
        [self evaluateItemWithArgs:[item objectForKey:@"args"] forType:[item objectForKey:@"type"]];
    }
    [_executor evaluatorEnd:self];
}

-(void)evaluateItemWithArgs:(id)args forType:(NSString*)type  {
    if ([type isEqualToString:@"setAutomationMode"]) {
        [_executor evaluator:self setAutomationMode:!!args];
    } else if ([type isEqualToString:@"setTheme"]) {
        if (![args isKindOfClass:NSString.class]) {
            NSLog(@"Invalid args type specified.");
            return;
        }
        [_executor evaluator:self setTheme:args];
    } else if ([type isEqualToString:@"setStyle"]) {
        if (![args isKindOfClass:NSNumber.class]) {
            NSLog(@"Invalid args type specified.");
            return;
        }
        NSInteger index = [args intValue];
        [_executor evaluator:self setStyle:index];
    } else if ([type isEqualToString:@"updateStyle"]) {
        // TODO
    } else if ([type isEqualToString:@"setScene"]) {
        if (![args isKindOfClass:NSDictionary.class] || ![[args objectForKey:@"lat"] isKindOfClass:NSNumber.class] ||
            ![[args objectForKey:@"lon"] isKindOfClass:NSNumber.class] || ![[args objectForKey:@"zoom"] isKindOfClass:NSNumber.class]) {
            NSLog(@"Invalid args type specified.");
            return;
        }
        double lat = [[args objectForKey:@"lat"] doubleValue];
        double lon = [[args objectForKey:@"lon"] doubleValue];
        double zoom = [[args objectForKey:@"zoom"] doubleValue];
        [_executor evaluator:self setSceneWithLat:lat lon:lon zoom:zoom];
    } else if ([type isEqualToString:@"extractLabels"]) {
        [_executor evaluatorExtractLabels:self];
    }
}

@end

