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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.pig.EvalFunc;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.builtin.PigStorage;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.io.BufferedPositionedInputStream;
import org.apache.pig.impl.io.FileLocalizer;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.UDFContext;
import org.junit.Test;

import junit.framework.TestCase;


public class TestUDFContext extends TestCase {
    
    static MiniCluster cluster = null;
    
    @Override 
    protected void setUp() throws Exception {
        cluster = MiniCluster.buildCluster();
    }


    @Test
    public void testUDFContext() throws Exception {
        Util.createInputFile(cluster, "a.txt", new String[] { "dumb" });
        Util.createInputFile(cluster, "b.txt", new String[] { "dumber" });
        FileLocalizer.deleteTempFiles();
        PigServer pig = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());
        String[] statement = { "A = LOAD 'a.txt' USING org.apache.pig.test.utils.UDFContextTestLoader('joe');",
            "B = LOAD 'b.txt' USING org.apache.pig.test.utils.UDFContextTestLoader('jane');",
            "C = union A, B;",
            "D = FOREACH C GENERATE $0, $1, org.apache.pig.test.utils.UDFContextTestEvalFunc($0), org.apache.pig.test.utils.UDFContextTestEvalFunc2($0);" };

        File tmpFile = File.createTempFile("temp_jira_851", ".pig");
        FileWriter writer = new FileWriter(tmpFile);
        for (String line : statement) {
            writer.write(line + "\n");
        }
        writer.close();
        
        pig.registerScript(tmpFile.getAbsolutePath());
        Iterator<Tuple> iterator = pig.openIterator("D");
        while (iterator.hasNext()) {
            Tuple tuple = iterator.next();
            if ("dumb".equals(tuple.get(0).toString())) {
                assertEquals(tuple.get(1).toString(), "joe");
            } else if ("dumber".equals(tuple.get(0).toString())) {
                assertEquals(tuple.get(1).toString(), "jane");
            }
        	assertEquals(Integer.valueOf(tuple.get(2).toString()), new Integer(5));
        	assertEquals(tuple.get(3).toString(), "five");
        }
    }
}
