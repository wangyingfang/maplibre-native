//
//  CommandEvaluator.h
//  MapToolsApp
//
//  Created by Hongbo Yu on 2024/11/11.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface ArgStyle : NSObject

@property (nonatomic) NSString* title;
@property (nonatomic) NSString* value;

@end

@class CommandEvaluator;

@protocol CommandExecutor

-(void) evaluator:(CommandEvaluator*)evaluator setAutomationMode:(BOOL) enabled;
-(void) evaluator:(CommandEvaluator*)evaluator setTheme:(NSString*) themeName;
-(void) evaluator:(CommandEvaluator*)evaluator setSceneWithLat:(double)lat lon:(double)lon zoom:(double)zoom;
-(void) evaluator:(CommandEvaluator*)evaluator updateStyles:(NSArray<ArgStyle*>*)styles;
-(void) evaluator:(CommandEvaluator*)evaluator setStyle:(NSInteger)index;
-(void) evaluatorExtractLabels:(CommandEvaluator*)evaluator;

-(void) evaluatorBegin:(CommandEvaluator*)evaluator;
-(void) evaluatorEnd:(CommandEvaluator*)evaluator;
@end

@interface CommandEvaluator : NSObject

@property (nonatomic, assign) id<CommandExecutor> executor;

-(instancetype)initWithExecutor:(id<CommandExecutor>)executor;
-(void)evaluate:(NSString*)commandString;

@end

NS_ASSUME_NONNULL_END
	
