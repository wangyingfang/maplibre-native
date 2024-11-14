#import "MLNVectorStyleLayer.h"
#import "MLNStyleLayer_Private.h"
#import "MLNValueEvaluator.h"

#include <mbgl/style/conversion/filter.hpp>

@implementation MLNVectorStyleLayer

- (void)setPredicate:(NSPredicate *)predicate {
    [NSException raise:MLNAbstractClassException
                format:@"MLNVectorStyleLayer is an abstract class"];
}

- (NSPredicate *)predicate {
    [NSException raise:MLNAbstractClassException
                format:@"MLNVectorStyleLayer is an abstract class"];
    return nil;
}

// NOTE ++++++++ Hongbo Yu
-(void)setFilterExpression:(id)expression {
    auto filter = mbgl::style::Filter();
    if (expression) {
        mbgl::style::conversion::Error valueError;
        auto value = mbgl::style::conversion::convert<mbgl::style::Filter>(mbgl::style::conversion::makeConvertible(expression), valueError);
        filter = mbgl::style::Filter(*value);
    }
    self.rawLayer->setFilter(filter);
}

-(id)filterExpression {
    if (self.rawLayer) {
        if (self.rawLayer->getFilter().expression) {
            return MLNJSONObjectFromMBGLExpression(**self.rawLayer->getFilter().expression);
        }
    }
    return nil;
}
// NOTE ------ Hongbo Yu

- (NSString *)description {
    if (self.rawLayer) {
        return [NSString stringWithFormat:
                @"<%@: %p; identifier = %@; sourceIdentifier = %@; "
                @"sourceLayerIdentifier = %@; predicate = %@; visible = %@>",
                NSStringFromClass([self class]), (void *)self, self.identifier,
                self.sourceIdentifier, self.sourceLayerIdentifier, self.predicate,
                self.visible ? @"YES" : @"NO"];
    }
    else {
        return [NSString stringWithFormat:
                @"<%@: %p; identifier = %@; sourceIdentifier = <unknown>; "
                @"sourceLayerIdentifier = <unknown>; predicate = <unknown>; visible = <unknown>>",
                NSStringFromClass([self class]), (void *)self, self.identifier];
    }
}

@end
