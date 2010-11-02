/**
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
package org.apache.pig.experimental.logical.rules;

import java.io.IOException;
import java.util.List;

import org.apache.pig.experimental.logical.expression.AndExpression;
import org.apache.pig.experimental.logical.expression.LogicalExpression;
import org.apache.pig.experimental.logical.expression.LogicalExpressionPlan;
import org.apache.pig.experimental.logical.relational.LOFilter;
import org.apache.pig.experimental.logical.relational.LogicalPlan;
import org.apache.pig.experimental.logical.relational.LogicalRelationalOperator;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.OperatorSubPlan;
import org.apache.pig.experimental.plan.optimizer.Rule;
import org.apache.pig.experimental.plan.optimizer.Transformer;
import org.apache.pig.impl.util.Pair;

public class SplitFilter extends Rule {    

    public SplitFilter(String n) {
        super(n);       
    }

    @Override
    public Transformer getNewTransformer() {        
        return new SplitFilterTransformer();
    }

    public class SplitFilterTransformer extends Transformer {
        private OperatorSubPlan subPlan;

        @Override
        public boolean check(OperatorPlan matched) throws IOException {
            LOFilter filter = (LOFilter)matched.getSources().get(0);
            LogicalExpressionPlan cond = filter.getFilterPlan();
            LogicalExpression root = (LogicalExpression) cond.getSources().get(0);
            if (root instanceof AndExpression) {
                return true;
            }
            
            return false;
        }

        @Override
        public void transform(OperatorPlan matched) throws IOException {
            subPlan = new OperatorSubPlan(currentPlan);
            
            // split one LOFilter into 2 by "AND"
            LOFilter filter = (LOFilter)matched.getSources().get(0);
            LogicalExpressionPlan cond = filter.getFilterPlan();
            LogicalExpression root = (LogicalExpression) cond.getSources().get(0);
            if (!(root instanceof AndExpression)) {
                return;
            }
            LogicalExpressionPlan op1 = new LogicalExpressionPlan();
            op1.add((LogicalExpression)cond.getSuccessors(root).get(0));
            fillSubPlan(cond, op1, (LogicalExpression)cond.getSuccessors(root).get(0));
            
            LogicalExpressionPlan op2 = new LogicalExpressionPlan();
            op2.add((LogicalExpression)cond.getSuccessors(root).get(1));
            fillSubPlan(cond, op2, (LogicalExpression)cond.getSuccessors(root).get(1));
            
            filter.setFilterPlan(op1);
            LOFilter filter2 = new LOFilter((LogicalPlan)currentPlan, op2);
            currentPlan.add(filter2);
            
            Operator succed = null;
            try {
                List<Operator> succeds = currentPlan.getSuccessors(filter);
                if (succeds != null) {
                    succed = succeds.get(0);
                    subPlan.add(succed);
                    Pair<Integer, Integer> p = currentPlan.disconnect(filter, succed);
                    currentPlan.connect(filter2, 0, succed, p.second);
                    currentPlan.connect(filter, p.first, filter2, 0); 
                } else {
                    currentPlan.connect(filter, 0, filter2, 0); 
                }
            }catch(Exception e) {
                throw new IOException(e);
            }                       
            
            subPlan.add(filter);
            subPlan.add(filter2);            
        }
        
        @Override
        public OperatorPlan reportChanges() {
            return subPlan;
        }
        
        private void fillSubPlan(OperatorPlan origPlan, 
                OperatorPlan subPlan, Operator startOp) throws IOException {
                       
            List<Operator> l = origPlan.getSuccessors(startOp);
            if (l != null) {
                for(Operator le: l) {
                    subPlan.add(le);
                    subPlan.connect(startOp, le);
                    fillSubPlan(origPlan, subPlan, le);
                }            
            }
        }

    }

    @Override
    protected OperatorPlan buildPattern() {        
        // the pattern that this rule looks for
        // is filter
        LogicalPlan plan = new LogicalPlan();      
        LogicalRelationalOperator op2 = new LOFilter(plan);
        plan.add(op2);
        
        return plan;
    }
}

