// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * @author baldersheim
 */
public class MathASinFunction extends FunctionNode {
    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, double value will be requested.
     */
    public MathASinFunction(GroupingExpression exp) {
        super("math.asin", Arrays.asList(exp));
    }
}
