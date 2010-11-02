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
package org.apache.pig.test.utils;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.DefaultBagFactory;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.io.FileSpec;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POLoad;

/**
 * Will contain static methods that will be useful
 * for unit tests
 *
 */
public class TestHelper {
    public static int dispAfterNumTuples = 1000;
    public static boolean bagContains(DataBag db, Tuple t) {
        Iterator<Tuple> iter = db.iterator();
        for (Tuple tuple : db) {
            if (tuple.compareTo(t) == 0 || tupleEquals(tuple, t))
                return true;
        }
        return false;
    }
    
    public static boolean compareBags(DataBag db1, DataBag db2) {
        if (db1.size() != db2.size())
            return false;
        
        int i=-1;
        boolean equal = true;
        for (Tuple tuple : db2) {
            boolean contains = false;
            for (Tuple tuple2 : db1) {
                if (tuple.compareTo(tuple2) == 0) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                equal = false;
                break;
            }
            /*if(++i%dispAfterNumTuples==0)
                System.out.println(i/dispAfterNumTuples);*/
        }
        return equal;
    }
    
    public static DataBag projectBag(DataBag db2, int i) throws ExecException {
        DataBag ret = DefaultBagFactory.getInstance().newDefaultBag();
        for (Tuple tuple : db2) {
            Object o = tuple.get(i);
            Tuple t1 = new DefaultTuple();
            t1.append(o);
            ret.add(t1);
        }
        return ret;
    }
    
    public static DataBag projectBag(DataBag db2, int[] fields) throws ExecException {
        DataBag ret = DefaultBagFactory.getInstance().newDefaultBag();
        for (Tuple tuple : db2) {
            Tuple t1 = new DefaultTuple();
            for (int fld : fields) {
                Object o = tuple.get(fld);
                t1.append(o);
            }
            ret.add(t1);
        }
        return ret;
    }
    
    public static int compareInputStreams(InputStream exp, InputStream act) throws IOException{
        byte[] bExp = new byte[4096], bAct = new byte[4096];
        
        int outLen,inLen = -1;
        while(act.read(bAct)!=-1){
            exp.read(bExp);
            int cmp = compareByteArray(bExp, bAct);
            if(cmp!=0)
                return cmp;
        }
        return 0;
    }
    
    public static int compareByteArray(byte[] b1, byte[] b2){
        if(b1.length>b2.length)
            return 1;
        else if(b1.length<b2.length)
            return -1;
        for(int i=0;i<b1.length;i++){
            if(b1[i]>b2[i])
                return 1;
            else if(b1[i]<b2[i])
                return -1;
        }
        return 0;
    }
    
    /*public static boolean areFilesSame(FileSpec expLocal, FileSpec actHadoop, PigContext pc, int dispAftNumTuples) throws ExecException, IOException{
        Random r = new Random();
        
        POLoad ldExp = new POLoad(new OperatorKey("", r.nextLong()));
        ldExp.setPc(pc);
        ldExp.setLFile(expLocal);
        
        POLoad ldAct = new POLoad(new OperatorKey("", r.nextLong()));
        ldAct.setPc(pc);
        ldAct.setLFile(actHadoop);
        
        Tuple t = null;
        int numActTuples = -1;
        boolean matches = true;
        for(Result resAct=ldAct.getNext(t);resAct.returnStatus!=POStatus.STATUS_EOP;resAct=ldAct.getNext(t)){
            Tuple tupAct = (Tuple)resAct.result;
            ++numActTuples;
            boolean found = false;
            for(Result resExp=ldExp.getNext(t);resExp.returnStatus!=POStatus.STATUS_EOP;resExp=ldExp.getNext(t)){
                Tuple tupExp = (Tuple)resExp.result;
                if(tupAct.compareTo(tupExp)==0){
                    found = true;
                    ldExp.tearDown();
                    break;
                }
            }
            if(!found){
                matches = false;
                break;
            }
            if(numActTuples%dispAftNumTuples ==0)
                System.out.println(numActTuples/dispAftNumTuples);
        }
        
        int numExpTuples = -1;
        while(ldExp.getNext(t).returnStatus!=POStatus.STATUS_EOP)
            ++numExpTuples;
        
        return (matches && numActTuples==numExpTuples);
    }*/
    
    public static boolean areFilesSame(FileSpec expLocal, FileSpec actHadoop, PigContext pc) throws ExecException, IOException{
        Random r = new Random();
        
        POLoad ldExp = new POLoad(new OperatorKey("", r.nextLong()));
        ldExp.setPc(pc);
        ldExp.setLFile(expLocal);
        
        POLoad ldAct = new POLoad(new OperatorKey("", r.nextLong()));
        ldAct.setPc(pc);
        ldAct.setLFile(actHadoop);
        
        Tuple t = null;
        int numActTuples = -1;
        DataBag bagAct = DefaultBagFactory.getInstance().newDefaultBag();
        Result resAct = null;
        while((resAct = ldAct.getNext(t)).returnStatus!=POStatus.STATUS_EOP){
            ++numActTuples;
            bagAct.add(trimTuple((Tuple)resAct.result));
        }
        
        int numExpTuples = -1;
        DataBag bagExp = DefaultBagFactory.getInstance().newDefaultBag();
        Result resExp = null;
        while((resExp = ldExp.getNext(t)).returnStatus!=POStatus.STATUS_EOP){
            ++numExpTuples;
            bagExp.add(trimTuple((Tuple)resExp.result));
        }
        
        if(numActTuples!=numExpTuples)
            return false;
        
        return compareBags(bagExp, bagAct);
    }
    
    private static Tuple trimTuple(Tuple t){
        Tuple ret = TupleFactory.getInstance().newTuple();
        for (Object o : t.getAll()) {
            DataByteArray dba = (DataByteArray)o;
            DataByteArray nDba = new DataByteArray(dba.toString().trim().getBytes());
            ret.append(nDba);
        }
        return ret;
    }

       /**
     * Create temp file from a given dataset
     * This assumes
     *  1) The dataset has at least 1 record
     *  2) All records are of the same size
     */
    public static File createTempFile(String[][] data) throws IOException {

        File fp1 = File.createTempFile("test", "txt");
        PrintStream ps = new PrintStream(new FileOutputStream(fp1));

        for(int i = 0; i < data.length ; i++) {

            // Building up string for each line
            StringBuilder sb = new StringBuilder() ;
            for(int j = 0 ; j < data[0].length ; j++) {
                if (j != 0) {
                    sb.append("\t") ;
                }
                sb.append(data[i][j]) ;
            }

            // Write the line to file
            ps.println(sb.toString());
        }

        ps.close();
        return fp1 ;
    }
    
    //a quick way to check for map equality as the map value returned by PigStorage has byte array
    public static boolean mapEquals(Map<String, Object> expectedMap, Map<String, Object> convertedMap) {
        if(expectedMap == null) {
            if(convertedMap != null) {
                return false;
            }
        } else {
            if (convertedMap == null) {
                return false;
            }
        }
        
        if(expectedMap.size() != convertedMap.size()) {
            return false;
        }
        
        for(String key: expectedMap.keySet()) {
            Object v = convertedMap.get(key);
            String convertedValue = new String(((DataByteArray)v).get());
            if(!expectedMap.get(key).toString().equals(convertedValue)) {
                return false;
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public static boolean tupleEquals(Tuple expectedTuple, Tuple convertedTuple) {
        if(expectedTuple == null) {
            if(convertedTuple != null) {
                return false;
            }
        } else {
            if(convertedTuple == null) {
                return false;
            }
        }
        
        if(expectedTuple.size() != convertedTuple.size()) {
            return false;
        }
        
        for(int i = 0; i < expectedTuple.size(); ++i) {
            Object e ;
            Object c ;
            
            try {
                e = expectedTuple.get(i);
                c = convertedTuple.get(i);
            } catch (Exception e1) {
                return false;
            }
            
            if(e instanceof Map) {
                Map<String, Object> eMap = (Map<String, Object>)e;
                if(c instanceof Map) {
                    Map<String, Object> cMap = (Map<String, Object>)c;
                    if(!mapEquals(eMap, cMap)) {
                        return false;
                    }
                } else {
                    return false;
                }
            } else if (e instanceof Tuple) {
                if(c instanceof Tuple) {
                    if(!tupleEquals((Tuple)e, (Tuple)c)) {
                        return false;
                    }
                } else {
                    return false;
                }
            } else if (e instanceof DataBag){
                if(c instanceof DataBag) {
                    if(!bagEquals((DataBag)e, (DataBag)c)) {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                if(e == null) {
                    if(c != null) {
                        return false;
                    }
                } else {
                    if(c == null) {
                        return false;
                    } else {
                        if(!e.equals(c)) {
                            return false;
                        }
                    }
                }
            }
        }
        
        return true;
    }   
    
    public static boolean bagEquals(DataBag expectedBag, DataBag convertedBag) {
        if(expectedBag == null) {
            if(convertedBag != null) {
                return false;
            }
        } else {
            if(convertedBag == null) {
                return false;
            }
        }
        
        if(expectedBag.size() != convertedBag.size()) {
            return false;
        }
        
        Iterator<Tuple> expectedBagIterator = expectedBag.iterator();
        Iterator<Tuple> convertedBagIterator = convertedBag.iterator();
        
        while(expectedBagIterator.hasNext()) {
            Tuple expectedBagTuple = expectedBagIterator.next();
            Tuple convertedBagTuple = convertedBagIterator.next();
            if(!tupleEquals(expectedBagTuple, convertedBagTuple)) {
                return false;
            }
        }
        
        return true;

    }

}
