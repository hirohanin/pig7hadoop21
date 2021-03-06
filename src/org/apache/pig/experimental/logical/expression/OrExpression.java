/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.experimental.logical.expression;

import java.io.IOException;

import org.apache.pig.data.DataType;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.PlanVisitor;

/**
 * Boolean OR Expression
 *
 */
public class OrExpression extends BinaryExpression {

    /**
     * Will add this operator to the plan and connect it to the 
     * left and right hand side operators.
     * @param plan plan this operator is part of
     * @param lhs expression on its left hand side
     * @param rhs expression on its right hand side
     */
    public OrExpression(OperatorPlan plan,
                         LogicalExpression lhs,
                         LogicalExpression rhs) {
        super("Or", plan, DataType.BOOLEAN, lhs, rhs);
    }

    /**
     * @link org.apache.pig.experimental.plan.Operator#accept(org.apache.pig.experimental.plan.PlanVisitor)
     */
    @Override
    public void accept(PlanVisitor v) throws IOException {
        if (!(v instanceof LogicalExpressionVisitor)) {
            throw new IOException("Expected LogicalExpressionVisitor");
        }
        ((LogicalExpressionVisitor)v).visitOr(this);
    }
    
    @Override
    public boolean isEqual(Operator other) {
        if (other != null && other instanceof OrExpression) {
            OrExpression ao = (OrExpression)other;
            try {
                return ao.getLhs().isEqual(getLhs()) && ao.getRhs().isEqual(getRhs());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return false;
        }
        
    }

}
