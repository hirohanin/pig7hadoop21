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

import java.io.*;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.pig.CollectableLoadFunc;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.test.utils.LogicalPlanTester;
import org.apache.pig.test.utils.TestHelper;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.MRCompilerException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POCollectedGroup;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.builtin.PigStorage;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.logicalLayer.LOCogroup;
import org.apache.pig.impl.logicalLayer.LogicalPlan;
import org.apache.pig.impl.plan.OperatorKey;
import org.junit.After;
import org.junit.Before;

public class TestCollectedGroup extends TestCase {
    private static final String INPUT_FILE = "MapSideGroupInput.txt";
    
    private PigServer pigServer;
    private MiniCluster cluster = MiniCluster.buildCluster();

    public TestCollectedGroup() throws ExecException, IOException{
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());
    }
    
    @Before
    public void setUp() throws Exception {
        createFiles();
    }

    private void createFiles() throws IOException {
        PrintWriter w = new PrintWriter(new FileWriter(INPUT_FILE));
        w.println("100\tapple1\t95");
        w.println("100\tapple2\t83");
        w.println("100\tapple2\t74");
        w.println("200\torange1\t100");
        w.println("200\torange2\t89");
        w.println("300\tstrawberry\t64");      
        w.println("300\tstrawberry\t64");      
        w.println("300\tstrawberry\t76");      
        w.println("400\tpear\t78");
        w.close();
        
        Util.copyFromLocalToCluster(cluster, INPUT_FILE, INPUT_FILE);
    }
    
    @After
    public void tearDown() throws Exception {
        new File(INPUT_FILE).delete();
        Util.deleteFile(cluster, INPUT_FILE);
    }
    
    public void testNonCollectableLoader() throws Exception{
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
        LogicalPlan lp = lpt.buildPlan("B = group A by id using 'collected';");
        PigContext pc = new PigContext(ExecType.MAPREDUCE,cluster.getProperties());
        pc.connect();
        try {
            Util.buildMRPlan(Util.buildPhysicalPlan(lp, pc),pc);  
            fail("Must throw MRCompiler Exception");
        } catch (Exception e) {
            assertTrue(e instanceof MRCompilerException);
        }
    }

    public void testCollectedGrpSpecifiedInSingleQuotes1(){
        
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
        LogicalPlan lp = lpt.buildPlan("B = group A by id using 'collected';");
        assertEquals(LOCogroup.GROUPTYPE.COLLECTED, ((LOCogroup)lp.getLeaves().get(0)).getGroupType());
    }
    
    public void testCollectedGrpSpecifiedInSingleQuotes2(){
        
        LogicalPlanTester lpt = new LogicalPlanTester();
        lpt.buildPlan("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
        LogicalPlan lp = lpt.buildPlan("B = group A all using 'regular';");
        assertEquals(LOCogroup.GROUPTYPE.REGULAR, ((LOCogroup)lp.getLeaves().get(0)).getGroupType());
    }
    
    public void testPOMapsideGroupNoNullPlans() throws IOException {
        POCollectedGroup pmg = new POCollectedGroup(new OperatorKey());
        List<PhysicalPlan> plans = pmg.getPlans();

        Assert.assertTrue(plans != null);
        Assert.assertTrue(plans.size() == 0);
    }      
     
    public void testMapsideGroupParserNoSupportForMultipleInputs() throws IOException {
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
        pigServer.registerQuery("B = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
    
        try {
            pigServer.registerQuery("C = group A by id, B by id using \"collected\";");
            fail("Pig doesn't support multi-input collected group.");
        } catch (Exception e) {
             Assert.assertEquals(e.getMessage(), 
                "Error during parsing. Collected group is only supported for single input");
        }
    }
    
    public void testMapsideGroupParserNoSupportForGroupAll() throws IOException {
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
    
        try {
            pigServer.registerQuery("B = group A all using \"collected\";");
            fail("Pig doesn't support collected group all.");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), 
                "Error during parsing. Collected group is only supported for columns or star projection");
        }
    }
     
    public void testMapsideGroupParserNoSupportForByExpression() throws IOException {
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' as (id, name, grade);");
    
        try {
            pigServer.registerQuery("B = group A by id*grade using \"collected\";");
            fail("Pig doesn't support collected group by expression.");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), 
                "Error during parsing. Collected group is only supported for columns or star projection");
        }
    }

    public void testMapsideGroupByOneColumn() throws IOException{
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' using "+DummyCollectableLoader.class.getName() +"() as (id, name, grade);");
        try {
            DataBag dbfrj = BagFactory.getInstance().newDefaultBag();
            DataBag dbshj = BagFactory.getInstance().newDefaultBag();
            {
                pigServer.registerQuery("B = group A by id using \"collected\";");
                pigServer.registerQuery("C = foreach B generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("C");

                while (iter.hasNext()) {
                    dbfrj.add(iter.next());
                }
            }
            {
                pigServer.registerQuery("D = group A by id;");
                pigServer.registerQuery("E = foreach D generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("E");

                while (iter.hasNext()) {
                    dbshj.add(iter.next());
                }
            }
            Assert.assertTrue(dbfrj.size()>0 && dbshj.size()>0);
            Assert.assertEquals(true, TestHelper.compareBags(dbfrj, dbshj));

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
 
    public void testMapsideGroupByMultipleColumns() throws IOException{
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' using "+DummyCollectableLoader.class.getName() +"() as (id, name, grade);");
        
        try {
            DataBag dbfrj = BagFactory.getInstance().newDefaultBag();
            DataBag dbshj = BagFactory.getInstance().newDefaultBag();
            {
                pigServer.registerQuery("B = group A by (id, name) using \"collected\";");
                pigServer.registerQuery("C = foreach B generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("C");

                while (iter.hasNext()) {
                    dbfrj.add(iter.next());
                }
            }
            {
                pigServer.registerQuery("D = group A by (id, name);");
                pigServer.registerQuery("E = foreach D generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("E");

                while (iter.hasNext()) {
                    dbshj.add(iter.next());
                }
            }
            Assert.assertTrue(dbfrj.size()>0 && dbshj.size()>0);
            Assert.assertEquals(true, TestHelper.compareBags(dbfrj, dbshj));

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
  
    public void testMapsideGroupByStar() throws IOException{
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());

        pigServer.registerQuery("A = LOAD '" + INPUT_FILE + "' using "+DummyCollectableLoader.class.getName() +"() as (id, name, grade);");
        
        try {
            DataBag dbfrj = BagFactory.getInstance().newDefaultBag();
            DataBag dbshj = BagFactory.getInstance().newDefaultBag();
            {
                pigServer.registerQuery("B = group A by * using \"collected\";");
                pigServer.registerQuery("C = foreach B generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("C");

                while (iter.hasNext()) {
                    dbfrj.add(iter.next());
                }
            }
            {
                pigServer.registerQuery("D = group A by *;");
                pigServer.registerQuery("E = foreach D generate group, COUNT(A);");
                Iterator<Tuple> iter = pigServer.openIterator("E");

                while (iter.hasNext()) {
                    dbshj.add(iter.next());
                }
            }
            Assert.assertTrue(dbfrj.size()>0 && dbshj.size()>0);
            Assert.assertEquals(true, TestHelper.compareBags(dbfrj, dbshj));

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public static class DummyCollectableLoader extends PigStorage implements CollectableLoadFunc{

        String udfContextSignature;
        
        @Override
        public void setUDFContextSignature(String signature) {
            udfContextSignature = signature;
        }
        
        @Override
        public void ensureAllKeyInstancesInSameSplit() throws IOException {
            assertNotNull(udfContextSignature);
        }
        
    }
}
