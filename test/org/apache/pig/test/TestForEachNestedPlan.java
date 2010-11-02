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

import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.ExecType;
import org.apache.pig.test.utils.TestHelper;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.parser.ParseException;
import org.junit.Test;
import junit.framework.TestCase;
import junit.framework.Assert;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;

public class TestForEachNestedPlan extends TestCase {

    MiniCluster cluster = MiniCluster.buildCluster();

    private PigServer pig ;

    public TestForEachNestedPlan() throws Throwable {
        pig = new PigServer(ExecType.MAPREDUCE, cluster.getProperties()) ;
    }

    Boolean[] nullFlags = new Boolean[]{ false, true };
    
    @Test
    public void testInnerOrderBy() throws Exception {
        for (int i = 0; i < nullFlags.length; i++) {
            System.err.println("Running testInnerOrderBy with nullFlags set to :"
                            + nullFlags[i]);
            File tmpFile = genDataSetFile1(nullFlags[i]);
            pig.registerQuery("a = load '" 
                    + Util.generateURI(tmpFile.toString(), pig.getPigContext()) + "'; ");
            pig.registerQuery("b = group a by $0; ");
            pig.registerQuery("c = foreach b { " + "     c1 = order $1 by *; "
                    + "    generate flatten(c1); " + "};");
            Iterator<Tuple> it = pig.openIterator("c");
            Tuple t = null;
            int count = 0;
            while (it.hasNext()) {
                t = it.next();
                System.out.println(count + ":" + t);
                count++;
            }
            Assert.assertEquals(count, 30);
        }
    }

    @Test
    public void testInnerOrderByStarWithSchema() throws Exception {        
        File tmpFile = genDataSetFile1(false);
        pig.registerQuery("a = load '" + Util.generateURI(tmpFile.toString(), 
                pig.getPigContext()) + "' as (a0, a1);");
        pig.registerQuery("b = group a by a0; ");
        pig.registerQuery("c = foreach b { d = order a by *; "
                + "  generate group, d; };");
        Iterator<Tuple> it = pig.openIterator("c");
        Tuple t = null;
        int count = 0;
        while (it.hasNext()) {
            t = it.next();
            System.out.println(count + ":" + t);
            count++;
        }
        Assert.assertEquals(count, 10);
    }
    
    @Test
    public void testAlgebricFuncWithoutGroupBy() 
    throws IOException, ParseException {
        String INPUT_FILE = "test-sum.txt";

        PrintWriter w = new PrintWriter(new FileWriter(INPUT_FILE));
        w.println("10\t{(1),(2),(3)}");
        w.println("20\t{(4),(5),(6),(7)}");
        w.println("30\t{(8),(9)}");
        w.close();

        try {

            Util.copyFromLocalToCluster(cluster, INPUT_FILE, INPUT_FILE);

            pig.registerQuery("a = load '" + INPUT_FILE + "' "
                    + "as (id:int, g:bag{t:tuple(u:int)});");
            pig.registerQuery("b = foreach a generate id, SUM(g);") ;

            Iterator<Tuple> iter = pig.openIterator("b");

            List<Tuple> expectedResults =
                Util.getTuplesFromConstantTupleStrings(
                    new String[] {
                            "(10,6L)",
                            "(20,22L)",
                            "(30,17L)"
                    });

            int counter = 0;
            while (iter.hasNext()) {
                assertEquals(expectedResults.get(counter++).toString(),
                        iter.next().toString());
            }

            assertEquals(expectedResults.size(), counter);

        } finally{
            new File(INPUT_FILE).delete();
            try {
                Util.deleteFile(cluster, INPUT_FILE);
            } catch (IOException e) {
                e.printStackTrace();
                Assert.fail();
            }
        }
    }

    @Test
    public void testInnerDistinct() 
    throws IOException, ParseException {
        String INPUT_FILE = "test-distinct.txt";

        PrintWriter w = new PrintWriter(new FileWriter(INPUT_FILE));
        w.println("10\t89");
        w.println("20\t78");
        w.println("10\t68");
        w.println("10\t89");
        w.println("20\t92");
        w.close();

        try {
            Util.copyFromLocalToCluster(cluster, INPUT_FILE, INPUT_FILE);
        
            pig.registerQuery("A = load '" + INPUT_FILE
                    + "' as (age:int, gpa:int);");
            pig.registerQuery("B = group A by age;");
            pig.registerQuery("C = foreach B { D = A.gpa; E = distinct D; " +
            		"generate group, MIN(E); };");
    
            Iterator<Tuple> iter = pig.openIterator("C");

            List<Tuple> expectedResults =
                Util.getTuplesFromConstantTupleStrings(
                        new String[] {"(10,68)", "(20,78)"});

            int counter = 0;
            while (iter.hasNext()) {
               assertEquals(expectedResults.get(counter++).toString(),
                        iter.next().toString());                
            }
    
            assertEquals(expectedResults.size(), counter);
        } finally{
            new File(INPUT_FILE).delete();
            try {
                Util.deleteFile(cluster, INPUT_FILE);
            } catch (IOException e) {
                e.printStackTrace();
                Assert.fail();
            }
        }
    }
    
    /***
     * For generating a sample dataset
     */
    private File genDataSetFile1(boolean withNulls) throws IOException {

        int dataLength = 30;
        String[][] data = new String[dataLength][] ;

        DecimalFormat formatter = new DecimalFormat("0000000");

        Random r = new Random();
        
        for (int i = 0; i < dataLength; i++) {
            data[i] = new String[2] ;
            // inject nulls randomly
            if(withNulls && r.nextInt(dataLength) < 0.3 * dataLength) {
                data[i][0] = "";
            } else {
                data[i][0] = formatter.format(i % 10);
            }
            data[i][1] = formatter.format((dataLength - i)/2);
        }

        return TestHelper.createTempFile(data) ;
    }
}
