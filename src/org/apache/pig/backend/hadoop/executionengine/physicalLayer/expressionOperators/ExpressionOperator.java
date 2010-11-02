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
package org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators;


import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.impl.plan.VisitorException;

/**
 * A base class for all types of expressions. All expression
 * operators must extend this class.
 *
 */

public abstract class ExpressionOperator extends PhysicalOperator {
    private static final long serialVersionUID = 1L;
    protected Log log = LogFactory.getLog(getClass());
    
    public ExpressionOperator(OperatorKey k) {
        this(k,-1);
    }

    public ExpressionOperator(OperatorKey k, int rp) {
        super(k, rp);
    }
    
    @Override
    public boolean supportsMultipleOutputs() {
        return false;
    }
    
    @Override
    public Result getNext(DataBag db) throws ExecException {
        return new Result();
    }
    
    public abstract void visit(PhyPlanVisitor v) throws VisitorException;
    

    /**
     * Make a deep copy of this operator.  This is declared here to make it
     * possible to call clone on ExpressionOperators.
     * @throws CloneNotSupportedException
     */
    @Override
    public ExpressionOperator clone() throws CloneNotSupportedException {
        String s = "This expression operator does not implement clone.";
        log.error(s);
        throw new CloneNotSupportedException(s);
    }

    /**
     * Get the sub-expressions of this expression.
     * This is called if reducer is run as accumulative mode, all the child
     * expression must be called if they have any UDF to drive the UDF.accumulate()
     */
    protected abstract List<ExpressionOperator> getChildExpressions();
    
    /** check whether this expression contains any UDF
     * this is called if reducer is run as accumulative mode
     * in this case, all UDFs must be called 
     */
    public boolean containUDF() {
        if (this instanceof POUserFunc) {
            return true;
        }
        
        List<ExpressionOperator> l = getChildExpressions();
        if (l != null) {
            for(ExpressionOperator e: l) {
                if (e.containUDF()) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Double d) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(d);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
       
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Integer v) throws ExecException {    	
        if (isAccumStarted()) {    		
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {            				
                        res = e.getNext(v);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
       
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Long l) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(l);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
    
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Float f) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(f);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
       
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Boolean b) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {           			
                    if (e.containUDF()) {    
                        res = e.getNext(b);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
    
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, String s) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(s);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
       
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, DataByteArray dba) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(dba);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
    
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Map map) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(map);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
    
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, Tuple t) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(t);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
    
    /**
     * Drive all the UDFs in accumulative mode
     */
    protected Result accumChild(List<ExpressionOperator> child, DataBag db) throws ExecException {
        if (isAccumStarted()) {
            if (child == null) {
                child = getChildExpressions();
            }
            Result res = null;
            if (child != null) {        		
                for(ExpressionOperator e: child) {        			
                    if (e.containUDF()) {    
                        res = e.getNext(db);
                        if (res.returnStatus != POStatus.STATUS_BATCH_OK) {
                            return res;
                        }
                    }
                }
            }
            
            res = new Result();
            res.returnStatus = POStatus.STATUS_BATCH_OK;
            
            return res;
        }
        
        return null;
    }
}
