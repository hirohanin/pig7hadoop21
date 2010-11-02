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

package org.apache.pig.test;

import java.io.IOException;
import java.util.*;
import org.apache.pig.data.DataType;
import org.apache.pig.experimental.logical.expression.*;
import org.apache.pig.experimental.logical.relational.LOFilter;
import org.apache.pig.experimental.logical.relational.LOJoin;
import org.apache.pig.experimental.logical.relational.LOLoad;
import org.apache.pig.experimental.logical.relational.LOStore;
import org.apache.pig.experimental.logical.relational.LogicalPlan;
import org.apache.pig.experimental.logical.relational.LogicalRelationalOperator;
import org.apache.pig.experimental.logical.relational.LogicalSchema;
import org.apache.pig.experimental.logical.rules.MergeFilter;
import org.apache.pig.experimental.logical.rules.PushUpFilter;
import org.apache.pig.experimental.logical.rules.SplitFilter;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.optimizer.PlanOptimizer;
import org.apache.pig.experimental.plan.optimizer.PlanTransformListener;
import org.apache.pig.experimental.plan.optimizer.Rule;
import org.apache.pig.impl.util.MultiMap;

import junit.framework.TestCase;

public class TestExperimentalFilterRule extends TestCase {

    LogicalPlan plan = null;
    LogicalRelationalOperator load1 = null;
    LogicalRelationalOperator load2 = null;
    LogicalRelationalOperator filter = null;
    LogicalRelationalOperator join = null;
    LogicalRelationalOperator store = null;    
    
    private void prep() {
        plan = new LogicalPlan();
        LogicalSchema schema = new LogicalSchema();
        schema.addField(new LogicalSchema.LogicalFieldSchema("id", null, DataType.INTEGER));
        schema.addField(new LogicalSchema.LogicalFieldSchema("name", null, DataType.CHARARRAY));
        schema.addField(new LogicalSchema.LogicalFieldSchema("age", null, DataType.INTEGER));    
        schema.getField(0).uid = 1;
        schema.getField(1).uid = 2;
        schema.getField(2).uid = 3;
        LogicalRelationalOperator l1 = new LOLoad(null, schema, plan);
        l1.setAlias("A");
        plan.add(l1);

        schema = new LogicalSchema();
        schema.addField(new LogicalSchema.LogicalFieldSchema("id", null, DataType.INTEGER));
        schema.addField(new LogicalSchema.LogicalFieldSchema("dept", null, DataType.INTEGER));
        schema.addField(new LogicalSchema.LogicalFieldSchema("salary", null, DataType.FLOAT));    
        schema.getField(0).uid = 4;
        schema.getField(1).uid = 5;
        schema.getField(2).uid = 6;
        LogicalRelationalOperator l2 = new LOLoad(null, schema, plan);
        l2.setAlias("B");
        plan.add(l2);
        
        MultiMap<Integer, LogicalExpressionPlan> joinPlans = new MultiMap<Integer, LogicalExpressionPlan>();
        LogicalExpressionPlan p1 = new LogicalExpressionPlan();
        ProjectExpression lp1 = new ProjectExpression(p1, DataType.CHARARRAY, 0, 1);
        p1.add(lp1);
        joinPlans.put(0, p1);
        
        LogicalExpressionPlan p2 = new LogicalExpressionPlan();
        ProjectExpression lp2 = new ProjectExpression(p2, DataType.INTEGER, 1, 1);
        p2.add(lp2);
        joinPlans.put(1, p2);
     
        LogicalRelationalOperator j1 = new LOJoin(plan, joinPlans, LOJoin.JOINTYPE.HASH, new boolean[]{true, true});
        j1.setAlias("C");
        plan.add(j1);
        
        
        // build an expression with no AND
        LogicalExpressionPlan p3 = new LogicalExpressionPlan();
        LogicalExpression lp3 = new ProjectExpression(p3, DataType.INTEGER, 0, 2);
        LogicalExpression cont = new ConstantExpression(p3, DataType.INTEGER, new Integer(3));
        p3.add(lp3);
        p3.add(cont);       
        LogicalExpression eq = new EqualExpression(p3, lp3, cont);        
        
        LogicalRelationalOperator f1 = new LOFilter(plan, p3);
        f1.setAlias("D");
        plan.add(f1);
        
        LogicalRelationalOperator s1 = new LOStore(plan);
        plan.add(s1);       
        
        // load --|-join - filter - store
        // load --|   
        plan.connect(l1, j1);
        plan.connect(l2, j1);
        plan.connect(j1, f1);        
        plan.connect(f1, s1);      
        
        try {
            lp1.setUid(j1);
            lp2.setUid(j1);
            lp3.setUid(f1);
        }catch(Exception e) {
            
        }
        
        filter = f1;
        store = s1;
        join = j1;
        load1 = l1;
        load2 = l2;
    }
    
    public void testFilterRule() throws Exception  {
        prep();
        // run split filter rule
        Rule r = new SplitFilter("SplitFilter");
        Set<Rule> s = new HashSet<Rule>();
        s.add(r);
        List<Set<Rule>> ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        MyPlanOptimizer optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        assertEquals(plan.getPredecessors(filter).get(0), join);
        assertEquals(plan.getSuccessors(filter).get(0), store);
        
        // run push up filter rule
        r = new PushUpFilter("PushUpFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        // the filter should be moved up to be after load
        assertEquals(plan.getSuccessors(load1).get(0), filter);
        assertEquals(plan.getSuccessors(filter).get(0), join);
        assertEquals(plan.getSuccessors(join).get(0), store);
        
        // run merge filter rule
        r = new MergeFilter("MergeFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        // the filter should the same as before, nothing to merge
        assertEquals(plan.getSuccessors(load1).get(0), filter);
        assertEquals(plan.getSuccessors(filter).get(0), join);
        assertEquals(plan.getSuccessors(join).get(0), store);
    }
        
    // build an expression with 1 AND, it should split into 2 filters
    public void testFilterRuleWithAnd() throws Exception  {
        prep();
        
        LogicalExpressionPlan p4 = new LogicalExpressionPlan();        
        LogicalExpression lp3 = new ProjectExpression(p4, DataType.INTEGER, 0, 2);
        LogicalExpression cont = new ConstantExpression(p4, DataType.INTEGER, new Integer(3));
        p4.add(lp3);
        p4.add(cont);
        LogicalExpression eq = new EqualExpression(p4, lp3, cont);      
      
        LogicalExpression lp4 = new ProjectExpression(p4, DataType.FLOAT, 0, 5);
        LogicalExpression cont2 = new ConstantExpression(p4, DataType.FLOAT, new Float(100));
        p4.add(lp4);
        p4.add(cont2);
        LogicalExpression eq2 = new EqualExpression(p4, lp4, cont2);        
    
        LogicalExpression and = new AndExpression(p4, eq, eq2);        
        
        lp3.setUid(filter);
        lp4.setUid(filter);
        
        ((LOFilter)filter).setFilterPlan(p4);
        
        // run split filter rule
        Rule r = new SplitFilter("SplitFilter");
        Set<Rule> s = new HashSet<Rule>();
        s.add(r);
        List<Set<Rule>> ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        PlanOptimizer optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        assertEquals(plan.getPredecessors(filter).get(0), join);
        Operator next = plan.getSuccessors(filter).get(0);
        assertEquals(LOFilter.class, next.getClass());        
        next = plan.getSuccessors(next).get(0);
        assertEquals(LOStore.class, next.getClass());
        
        // run push up filter rule
        r = new PushUpFilter("PushUpFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        // both filters should be moved up to be after each load
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        assertEquals(plan.getSuccessors(join).get(0), store);
        
        // run merge filter rule
        r = new MergeFilter("MergeFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        // the filters should the same as before, nothing to merge
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        assertEquals(plan.getSuccessors(join).get(0), store);
    }
    
    public void testFilterRuleWith2And() throws Exception  {
        prep();
        // build an expression with 2 AND, it should split into 3 filters
        LogicalExpressionPlan p5 = new LogicalExpressionPlan();
        
       
        LogicalExpression lp3 = new ProjectExpression(p5, DataType.INTEGER, 0, 2);
        LogicalExpression cont = new ConstantExpression(p5, DataType.INTEGER, new Integer(3));
        p5.add(lp3);
        p5.add(cont);       
        LogicalExpression eq = new EqualExpression(p5, lp3, cont);
        
        LogicalExpression lp4 = new ProjectExpression(p5, DataType.INTEGER, 0, 3);
        LogicalExpression cont2 = new ConstantExpression(p5, DataType.INTEGER, new Integer(3));        
        p5.add(lp4);
        p5.add(cont2);
        LogicalExpression eq2 = new EqualExpression(p5, lp4, cont2);        
        
        lp3.setUid(filter);
        lp4.setUid(filter);
        
        LogicalExpression and1 = new AndExpression(p5, eq, eq2);
        
       
        lp3 = new ProjectExpression(p5, DataType.INTEGER, 0, 0);
        lp4 = new ProjectExpression(p5, DataType.INTEGER, 0, 3);     
        lp3.setUid(filter);
        lp4.setUid(filter);
        p5.add(lp3);
        p5.add(lp4);   
        eq2 = new EqualExpression(p5, lp3, lp4);        
              
        LogicalExpression and2 = new AndExpression(p5, and1, eq2);        
        
        ((LOFilter)filter).setFilterPlan(p5);
        
        Rule r = new SplitFilter("SplitFilter");
        Set<Rule> s = new HashSet<Rule>();
        s.add(r);
        List<Set<Rule>> ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        MyPlanOptimizer optimizer = new MyPlanOptimizer(plan, ls, 3);
        MyPlanTransformListener listener = new MyPlanTransformListener();
        optimizer.addPlanTransformListener(listener);
        optimizer.optimize();
        
        assertEquals(plan.getPredecessors(filter).get(0), join);
        Operator next = plan.getSuccessors(filter).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        next = plan.getSuccessors(next).get(0);
        assertEquals(LOStore.class, next.getClass());
        
        OperatorPlan transformed = listener.getTransformed();
        assertEquals(transformed.size(), 3);
        
        // run push up filter rule
        r = new PushUpFilter("PushUpFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        listener = new MyPlanTransformListener();
        optimizer.addPlanTransformListener(listener);
        optimizer.optimize();
        
        // 2 filters should be moved up to be after each load, and one filter should remain
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(join).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOStore.class);
        
        transformed = listener.getTransformed();
        assertEquals(transformed.size(), 4);
        assertEquals(transformed.getSinks().get(0).getClass(), LOFilter.class);
        assertEquals(transformed.getSources().get(0).getClass(), LOLoad.class);
        
        // run merge filter rule
        r = new MergeFilter("MergeFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        listener = new MyPlanTransformListener();
        optimizer.addPlanTransformListener(listener);
        optimizer.optimize();
        
        // the filters should the same as before, nothing to merge
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(join).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOStore.class);
        
        transformed = listener.getTransformed();
        assertNull(transformed);
    }   
    
    public void testFilterRuleWith2And2() throws Exception  {
        prep();
        // build an expression with 2 AND, it should split into 3 filters
        LogicalExpressionPlan p5 = new LogicalExpressionPlan();
        
        LogicalExpression lp3 = new ProjectExpression(p5, DataType.INTEGER, 0, 2);
        lp3.setUid(filter);
        LogicalExpression cont = new ConstantExpression(p5, DataType.INTEGER, new Integer(3));
        p5.add(lp3);
        p5.add(cont);
        LogicalExpression eq = new EqualExpression(p5, lp3, cont);      
        
        lp3 = new ProjectExpression(p5, DataType.INTEGER, 0, 0);
        LogicalExpression lp4 = new ProjectExpression(p5, DataType.INTEGER, 0, 3);        
        p5.add(lp4);
        p5.add(lp3);
        lp3.setUid(filter);
        lp4.setUid(filter);
        LogicalExpression eq2 = new EqualExpression(p5, lp3, lp4);
        
        LogicalExpression and1 = new AndExpression(p5, eq, eq2);
        
        lp3 = new ProjectExpression(p5, DataType.INTEGER, 0, 2);
        lp4 = new ProjectExpression(p5, DataType.FLOAT, 0, 5);        
        p5.add(lp3);
        p5.add(lp4);
        lp3.setUid(filter);
        lp4.setUid(filter);
        eq2 = new EqualExpression(p5, lp3, lp4);
        
        LogicalExpression and2 = new AndExpression(p5, and1, eq2);    
        
        ((LOFilter)filter).setFilterPlan(p5);
        
        Rule r = new SplitFilter("SplitFilter");
        Set<Rule> s = new HashSet<Rule>();
        s.add(r);
        List<Set<Rule>> ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        MyPlanOptimizer optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        assertEquals(plan.getPredecessors(filter).get(0), join);
        Operator next = plan.getSuccessors(filter).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        next = plan.getSuccessors(next).get(0);
        assertEquals(LOStore.class, next.getClass());
        
        // run push up filter rule
        r = new PushUpFilter("PushUpFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        optimizer.optimize();
        
        // 1 filter should be moved up to be after a load, and 2 filters should remain
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next, join);     
        
        next = plan.getSuccessors(join).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOFilter.class);
                
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOStore.class);
        
        // run merge filter rule
        r = new MergeFilter("MergeFilter");
        s = new HashSet<Rule>();
        s.add(r);
        ls = new ArrayList<Set<Rule>>();
        ls.add(s);
        optimizer = new MyPlanOptimizer(plan, ls, 3);
        MyPlanTransformListener listener = new MyPlanTransformListener();
        optimizer.addPlanTransformListener(listener);
        optimizer.optimize();
        
        // the 2 filters after join should merge
        next = plan.getSuccessors(load1).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        assertEquals(plan.getSuccessors(next).get(0), join);
        
        next = plan.getSuccessors(load2).get(0);
        assertEquals(next, join);        
        
        next = plan.getSuccessors(join).get(0);
        assertEquals(next.getClass(), LOFilter.class);
        
        next = plan.getSuccessors(next).get(0);
        assertEquals(next.getClass(), LOStore.class);
        
        OperatorPlan transformed = listener.getTransformed();
        assertEquals(transformed.size(), 2);
    }   
    
    public class MyPlanOptimizer extends PlanOptimizer {

        protected MyPlanOptimizer(OperatorPlan p, List<Set<Rule>> rs,
                int iterations) {
            super(p, rs, iterations);			
        }
        
        public void addPlanTransformListener(PlanTransformListener listener) {
            super.addPlanTransformListener(listener);
        }
        
    }
    
    public class MyPlanTransformListener implements PlanTransformListener {

        private OperatorPlan tp;

        @Override
        public void transformed(OperatorPlan fp, OperatorPlan tp)
                throws IOException {
            this.tp = tp;
        }
        
        public OperatorPlan getTransformed() {
            return tp;
        }
    }
}
