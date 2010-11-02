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
package org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators;

import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.PigException;
import org.apache.pig.SortInfo;
import org.apache.pig.StoreFuncInterface;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.io.FileSpec;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.VisitorException;

/**
 * The store operator which is used in two ways:
 * 1) As a local operator it can be used to store files
 * 2) In the Map Reduce setting, it is used to create jobs
 *    from MapReduce operators which keep the loads and
 *    stores in the Map and Reduce Plans till the job is created
 *
 */
public class POStore extends PhysicalOperator {

    private static final long serialVersionUID = 1L;
    private static Result empty = new Result(POStatus.STATUS_NULL, null);
    transient private StoreFuncInterface storer;    
    transient private final Log log = LogFactory.getLog(getClass());
    transient private POStoreImpl impl;
    private FileSpec sFile;
    private Schema schema;

    // flag to distinguish user stores from MRCompiler stores.
    private boolean isTmpStore;
    
    // If we know how to reload the store, here's how. The lFile
    // FileSpec is set in PigServer.postProcess. It can be used to
    // reload this store, if the optimizer has the need.
    private FileSpec lFile;
    
    // if the predecessor of store is Sort (order by)
    // then sortInfo will have information of the sort 
    // column names and the asc/dsc info
    private SortInfo sortInfo;
    
    private String signature;
    
    public POStore(OperatorKey k) {
        this(k, -1, null);
    }

    public POStore(OperatorKey k, int rp) {
        this(k, rp, null);
    }
    
    public POStore(OperatorKey k, int rp, List<PhysicalOperator> inp) {
        super(k, rp, inp);
    }
    
    /**
     * Set up the storer
     * @throws IOException
     */
    public void setUp() throws IOException{
        if (impl != null) {
            try{
                storer = impl.createStoreFunc(this);
            }catch (IOException ioe) {
                int errCode = 2081;
                String msg = "Unable to setup the store function.";            
                throw new ExecException(msg, errCode, PigException.BUG, ioe);
            }
        }
    }
    
    /**
     * Called at the end of processing for clean up.
     * @throws IOException
     */
    public void tearDown() throws IOException{
        if (impl != null) {
            impl.tearDown();
        }
   }
    
    /**
     * To perform cleanup when there is an error.
     * @throws IOException
     */
    public void cleanUp() throws IOException{
        if (impl != null) {
            impl.cleanUp();
        }
    }
    
    @Override
    public Result getNext(Tuple t) throws ExecException {
        Result res = processInput();
        try {
            switch (res.returnStatus) {
            case POStatus.STATUS_OK:
                storer.putNext((Tuple)res.result);
                res = empty;
                break;
            case POStatus.STATUS_EOP:
                break;
            case POStatus.STATUS_ERR:
            case POStatus.STATUS_NULL:
            default:
                break;
            }
        } catch (IOException ioe) {
            int errCode = 2135;
            String msg = "Received error from store function." + ioe.getMessage();
            throw new ExecException(msg, errCode, ioe);
        }
        return res;
    }

    @Override
    public String name() {
        if(sFile!=null)
            return "Store" + "(" + sFile.toString() + ")" + " - " + mKey.toString();
        else
            return "Store" + "(" + "DummyFil:DummyLdr" + ")" + " - " + mKey.toString();
    }

    @Override
    public boolean supportsMultipleInputs() {
        return false;
    }

    @Override
    public boolean supportsMultipleOutputs() {
        return true;
    }

    @Override
    public void visit(PhyPlanVisitor v) throws VisitorException {
        v.visitStore(this);
    }

    public FileSpec getSFile() {
        return sFile;
    }

    public void setSFile(FileSpec sFile) {
        this.sFile = sFile;
    }

    public void setInputSpec(FileSpec lFile) {
        this.lFile = lFile;
    }

    public FileSpec getInputSpec() {
        return lFile;
    }
    
    public void setIsTmpStore(boolean tmp) {
        isTmpStore = tmp;
    }
    
    public boolean isTmpStore() {
        return isTmpStore;
    }

    public void setStoreImpl(POStoreImpl impl) {
        this.impl = impl;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }
    
    public Schema getSchema() {
        return schema;
    }
    
    public StoreFuncInterface getStoreFunc() {
        StoreFuncInterface sFunc = (StoreFuncInterface)PigContext.instantiateFuncFromSpec(sFile.getFuncSpec());
        sFunc.setStoreFuncUDFContextSignature(signature);
        return sFunc;
    }
    
    /**
     * @param sortInfo the sortInfo to set
     */
    public void setSortInfo(SortInfo sortInfo) {
        this.sortInfo = sortInfo;
    }

    /**
     * @return the sortInfo
     */
    public SortInfo getSortInfo() {
        return sortInfo;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
}
