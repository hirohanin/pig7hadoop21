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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.ConstantExpression;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.EqualToExpr;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.POProject;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POFilter;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POLoad;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POStore;
import org.apache.pig.experimental.logical.LogicalPlanMigrationVistor;
import org.apache.pig.experimental.logical.optimizer.PlanPrinter;
import org.apache.pig.experimental.logical.optimizer.ProjectionPatcher;
import org.apache.pig.experimental.logical.optimizer.SchemaPatcher;
import org.apache.pig.experimental.logical.optimizer.UidStamper;
import org.apache.pig.experimental.logical.relational.LOLoad;
import org.apache.pig.experimental.logical.relational.LogToPhyTranslationVisitor;
import org.apache.pig.experimental.logical.rules.FilterAboveForeach;
import org.apache.pig.experimental.logical.rules.ColumnMapKeyPrune;
import org.apache.pig.experimental.logical.rules.MapKeysPruneHelper;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.optimizer.PlanOptimizer;
import org.apache.pig.experimental.plan.optimizer.PlanTransformListener;
import org.apache.pig.experimental.plan.optimizer.Rule;
import org.apache.pig.impl.logicalLayer.LogicalPlan;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.test.TestExperimentalFilterAboveForeach.MyPlanOptimizer;
import org.apache.pig.test.utils.LogicalPlanTester;

import junit.framework.TestCase;

public class TestExperimentalPruneMapKeys extends TestCase {

    private PhysicalPlan translatePlan(OperatorPlan plan) throws IOException {
        LogToPhyTranslationVisitor visitor = new LogToPhyTranslationVisitor(plan);
        visitor.visit();
        return visitor.getPhysicalPlan();
    }
    
    private org.apache.pig.experimental.logical.relational.LogicalPlan migratePlan(LogicalPlan lp) throws VisitorException{
        LogicalPlanMigrationVistor visitor = new LogicalPlanMigrationVistor(lp);        
        visitor.visit();
        org.apache.pig.experimental.logical.relational.LogicalPlan newPlan = visitor.getNewLogicalPlan();
        
        try {
            UidStamper stamper = new UidStamper(newPlan);
            stamper.visit();
            
            // run filter rule
            Set<Rule> s = new HashSet<Rule>();
            List<Set<Rule>> ls = new ArrayList<Set<Rule>>();
            ls.add(s);
            // Add the PruneMap Filter
            Rule r = new ColumnMapKeyPrune("PruneMapKeys");
            s.add(r);            
            
            printPlan((org.apache.pig.experimental.logical.relational.LogicalPlan)newPlan);
            
            // Run the optimizer
            MyPlanOptimizer optimizer = new MyPlanOptimizer(newPlan, ls, 3);
            optimizer.addPlanTransformListener(new ProjectionPatcher());
            optimizer.addPlanTransformListener(new SchemaPatcher());
            optimizer.optimize();
            
            return newPlan;
        }catch(Exception e) {
            throw new VisitorException(e);
        }
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
        
    @SuppressWarnings("unchecked")
    public void testSimplePlan() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt' as (a:map[], b:int, c:float);");
        lpt.buildPlan("b = filter a by a#'name' == 'hello';");
        LogicalPlan plan = lpt.buildPlan("store b into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 1, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue(annotation == null || annotation.isEmpty() );
        }
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan2() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt' as (a:map[], b:int, c:float);");
        lpt.buildPlan("b = filter a by a#'name' == 'hello';");
        lpt.buildPlan("c = foreach b generate b,c;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        assertEquals( 1, newLogicalPlan.getSources().size() );
        LOLoad load = (LOLoad) newLogicalPlan.getSources().get(0);
        Map<Long,Set<String>> annotation = 
            (Map<Long, Set<String>>) load.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
        assertTrue( annotation != null );
        assertEquals( 1, annotation.keySet().size() );
        Integer[] keySet = annotation.keySet().toArray( new Integer[0] );
        assertEquals( new Integer(0), keySet[0] );
        Set<String> keys = annotation.get(0);
        assertEquals( 1, keys.size() );
        assertEquals( "name", keys.toArray( new String[0] )[0] );            
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan3() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt' as (a:map[], b:int, c:float);");
        lpt.buildPlan("b = filter a by a#'name' == 'hello';");
        lpt.buildPlan("c = foreach b generate a#'age',b,c;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        assertEquals( 1, newLogicalPlan.getSources().size() );
        LOLoad load = (LOLoad) newLogicalPlan.getSources().get(0);
        Map<Long,Set<String>> annotation = 
            (Map<Long, Set<String>>) load.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
        assertTrue( annotation != null );
        assertEquals( 1, annotation.keySet().size() );
        Integer[] keySet = annotation.keySet().toArray( new Integer[0] );
        assertEquals( new Integer(0), keySet[0] );
        Set<String> keys = annotation.get(0);
        assertEquals( 2, keys.size() );
        assertTrue( keys.contains("name") );
        assertTrue( keys.contains("age"));
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan4() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt' as (a:map[], b:int, c:float);");
        lpt.buildPlan("b = filter a by a#'name' == 'hello';");
        lpt.buildPlan("c = foreach b generate a#'age',a,b,c;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 1, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue(annotation == null || annotation.isEmpty() );
        }
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan5() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt' as (a:chararray, b:int, c:float);");
        lpt.buildPlan("b = filter a by a == 'hello';");
        lpt.buildPlan("c = foreach b generate a,b,c;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 1, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue(annotation == null );
        }
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan6() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt';");
        lpt.buildPlan("b = filter a by $0 == 'hello';");
        lpt.buildPlan("c = foreach b generate $0,$1,$2;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 1, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue(annotation == null );
        }
    }
    
    @SuppressWarnings("unchecked")
    public void testSimplePlan7() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt';");
        lpt.buildPlan("a1 = load 'b.txt' as (a:map[],b:int, c:float);" );
        lpt.buildPlan("b = join a by $0, a1 by a#'name';");
        lpt.buildPlan("c = foreach b generate $0,$1,$2;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        printPlan(plan);
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 2, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue( annotation == null || annotation.isEmpty() );
        }
    }

    @SuppressWarnings("unchecked")
    public void testSimplePlan8() throws Exception {
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("a = load 'd.txt';");
        lpt.buildPlan("a1 = load 'b.txt' as (a:chararray,b:int, c:float);" );
        lpt.buildPlan("b = join a by $0, a1 by a;");
        lpt.buildPlan("c = foreach b generate $0,$1,$2;" );
        LogicalPlan plan = lpt.buildPlan("store c into 'empty';");        
        
        printPlan(plan);
        
        org.apache.pig.experimental.logical.relational.LogicalPlan newLogicalPlan = migratePlan(plan);
        
        List<Operator> sources = newLogicalPlan.getSources();
        assertEquals( 2, sources.size() );
        for( Operator source : sources ) {
            Map<Long,Set<String>> annotation = 
                (Map<Long, Set<String>>) source.getAnnotation(MapKeysPruneHelper.REQUIRED_MAPKEYS);
            assertTrue( annotation == null || annotation.isEmpty() );
        }
    }
    
    public void printPlan(org.apache.pig.experimental.logical.relational.LogicalPlan logicalPlan ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out);
        PlanPrinter pp = new PlanPrinter(logicalPlan,ps);
        pp.visit();
        System.err.println(out.toString());
    }
    
    public void printPlan(LogicalPlan logicalPlan) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out);
        logicalPlan.explain(ps, "text", true);
        System.err.println(out.toString());
    }
    
    public void printPlan(PhysicalPlan physicalPlan) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out);
        physicalPlan.explain(ps, "text", true);
        System.err.println(out.toString());
    }
}
