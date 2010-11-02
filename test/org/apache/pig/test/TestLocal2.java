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

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.pig.EvalFunc;
import org.apache.pig.PigServer;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.test.utils.TestHelper;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Iterator;

public class TestLocal2 extends TestCase {

    private String initString = "local";
    //MiniCluster cluster = MiniCluster.buildCluster();

    private PigServer pig ;

    public TestLocal2() throws Throwable {
        pig = new PigServer(initString) ;
    }


    @Test
    public void testUnion1() throws Exception {
        File tmpFile1 = genDataSetFile(false, 30 ) ;
        File tmpFile2 = genDataSetFile(false, 50 ) ;
        pig.registerQuery("a = load '"
                + Util.generateURI(tmpFile1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("b = load '"
                + Util.generateURI(tmpFile2.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("c = union a, b; ") ;
        
        verifyUnion( "c", 30 + 50 );
    }

    @Test
    public void testUnion1WithNulls() throws Exception {

        File tmpFile1 = genDataSetFile(true, 30 ) ;
        File tmpFile2 = genDataSetFile(true, 50 ) ;
        pig.registerQuery("a = load '"
                + Util.generateURI(tmpFile1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("b = load '"
                + Util.generateURI(tmpFile2.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("c = union a, b; ") ;

        verifyUnion( "c", 30 + 50 );
    }

    @Test
    public void testUnion2() throws Exception {

        File tmpFile1 = genDataSetFile(false, 30) ;
        File tmpFile2 = genDataSetFile(false, 50) ;
        pig.registerQuery("a = load '"
                + Util.generateURI(tmpFile1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("b = load '"
                + Util.generateURI(tmpFile2.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("a1 = foreach a generate $0, $1; ") ;
        pig.registerQuery("b1 = foreach b generate $0, $1; ") ;
        pig.registerQuery("c = union a1, b1; ") ;

        verifyUnion( "c", 30 + 50 );
    }

    @Test
    public void testUnion2WithNulls() throws Exception {
        File tmpFile1 = genDataSetFile(true, 30) ;
        File tmpFile2 = genDataSetFile(true, 50) ;
        pig.registerQuery("a = load '"
                + Util.generateURI(tmpFile1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("b = load '"
                + Util.generateURI(tmpFile2.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("a1 = foreach a generate $0, $1; ") ;
        pig.registerQuery("b1 = foreach b generate $0, $1; ") ;
        pig.registerQuery("c = union a1, b1; ") ;

        verifyUnion( "c", 30 + 50 );
    }
    
    @Test
    public void testPig800Distinct() throws Exception {
        // Regression test for Pig-800
        File fp1 = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(fp1));
        
        ps.println("1\t1}");
        ps.close();
        
        pig.registerQuery("A = load '"
                + Util.generateURI(fp1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("B = foreach A generate flatten("
                + Pig800Udf.class.getName() + "($0));");
        pig.registerQuery("C = distinct B;");
        
        Iterator<Tuple> iter = pig.openIterator("C");
        // Before PIG-800 was fixed this went into an infinite loop, so just
        // managing to open the iterator is sufficient.
        
    }
    
    @Test
    public void testPig800Sort() throws Exception {
        // Regression test for Pig-800
        File fp1 = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(fp1));
        
        ps.println("1\t1}");
        ps.close();
        
        pig.registerQuery("A = load '"
                + Util.generateURI(fp1.toString(), pig.getPigContext())
                + "'; ");
        pig.registerQuery("B = foreach A generate flatten("
                + Pig800Udf.class.getName() + "($0));");
        pig.registerQuery("C = order B by $0;");
        
        Iterator<Tuple> iter = pig.openIterator("C");
        // Before PIG-800 was fixed this went into an infinite loop, so just
        // managing to open the iterator is sufficient.
        
    }
    
    @Test
    public void testJoin1() throws Exception {
        // Regression test for Pig-925
        File fp1 = File.createTempFile("test1", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(fp1));
        
        ps.println("1\t1");
        ps.println("2\t2");
        ps.close();
        
        File fp2 = File.createTempFile("test2", "txt");
        ps = new PrintStream(new FileOutputStream(fp2));
        
        ps.println("1\t1");
        ps.println("2\t2");
        ps.close();
        
        
        pig.registerQuery("A = load '"
                + Util.generateURI(fp1.toString(), pig.getPigContext())
                + "'AS (a0:int, a1:int); ");
        pig.registerQuery("B = load '"
                + Util.generateURI(fp2.toString(), pig.getPigContext())
                + "'AS (b0:int, b1:int); ");
        pig.registerQuery("C = join A by a0, B by b0;");
        
        Iterator<Tuple> iter = pig.openIterator("C");
        assertTrue(iter.hasNext());
        Tuple t = iter.next();
        assertTrue(t.get(0).equals(new Integer(1)));
        assertTrue(t.get(1).equals(new Integer(1)));
        assertTrue(t.get(2).equals(new Integer(1)));
        assertTrue(t.get(3).equals(new Integer(1)));
        
        assertTrue(iter.hasNext());
        t = iter.next();
        assertTrue(t.get(0).equals(new Integer(2)));
        assertTrue(t.get(1).equals(new Integer(2)));
        assertTrue(t.get(2).equals(new Integer(2)));
        assertTrue(t.get(3).equals(new Integer(2)));
        
        assertTrue(!iter.hasNext());
    }
    
    
    static public class Pig800Udf extends EvalFunc<DataBag> {
        
        @Override
        public DataBag exec(Tuple input) throws IOException {
            DataBag output = BagFactory.getInstance().newDefaultBag();
            return output;
        }
    }

    //verifies results
    public void verifyUnion(String id, int actualCount ) throws Exception {

        Iterator<Tuple> it = pig.openIterator(id);
        Tuple t = null ;
        int count = 0 ;

        while(it.hasNext()) {
            t = it.next() ;
            System.out.println(count + ":" + t) ;
            count++ ;
        }

        Assert.assertEquals(count, actualCount);
    }

    @Test
    public void testLocalInit() throws Exception {
        File pigFile = new File("script.pig");
        File siteFile = new File("mapred-site.xml");
        try {
            pigFile.createNewFile();
            int status,status2;
            status = Util.executeJavaCommand("java -cp "+ 
                    System.getProperty("java.class.path") + 
                    "  org.apache.pig.Main -x local " + pigFile.getAbsolutePath() );

            String contents = "<?xml version=\"1.0\"?>\n" +
            "<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>\n" +
            "<configuration xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
            "  <property>\n" +
            "    <name>mapred.system.dir</name>\n" +
            "    <value>/mapredsystem/hadoop/mapredsystem</value>\n" +
            "    <description>No description</description>\n" +
            "    <final>true</final>\n" +
            "  </property>\n" +
            "</configuration>\n";
            assertTrue( siteFile.createNewFile() );
            PrintStream ps = new PrintStream(siteFile);
            ps.print(contents);
            ps.close();
            status2 = Util.executeJavaCommand("java -cp "+ 
                    System.getProperty("java.class.path") + 
                    "  org.apache.pig.Main -x local " + pigFile.getAbsolutePath() );
            assertEquals( "Without a mapred-site.xml pig should just run", 0, status );
            assertEquals( "With map.system.dir redefined in mapred-site.xml pig " +
                    "should exit", 2, status2 );
        } finally {
            if( siteFile.exists() ) 
                siteFile.delete();
            if( pigFile.exists() )
                pigFile.delete();
        }
    }

    /***
     * For generating a sample dataset
     */
    private File genDataSetFile(boolean hasNulls, int dataLength ) throws IOException {

        String[][] data = new String[dataLength][] ;

        DecimalFormat formatter = new DecimalFormat("0000000");

        if ( hasNulls == true ) {

                for (int i = 0; i < dataLength; i++) {

                     data[i] = new String[2] ;
                     if ( i % 7  == 0 ) {
                        data[i][0] = "";
                        data[i][1] = formatter.format(dataLength - i);

                     } else if ( i % 10 ==0  ) {

                        data[i][0] = formatter.format(i % 10);
                        data[i][1] = "";

                     } else if ( i % 13 == 0 ) {

                        data[i][0] = "";
                        data[i][1] = "";

                     } else {
                        data[i][0] = formatter.format(i % 10);
                        data[i][1] = formatter.format(dataLength - i);
                     }
             }

        } else {


            for (int i = 0; i < dataLength; i++) {
                data[i] = new String[2] ;
                data[i][0] = formatter.format(i % 10);
                data[i][1] = formatter.format(dataLength - i);
            }

        }
        return TestHelper.createTempFile(data) ;
    }

}
