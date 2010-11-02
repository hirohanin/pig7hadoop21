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
package org.apache.pig.impl.logicalLayer;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import org.apache.pig.PigException;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.PlanVisitor;
import org.apache.pig.impl.plan.ProjectionMap;
import org.apache.pig.impl.plan.RequiredFields;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.util.Pair;
import org.apache.pig.data.DataType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LODistinct extends RelationalOperator {

    private static final long serialVersionUID = 2L;
    private static Log log = LogFactory.getLog(LODistinct.class);

    /**
     * 
     * @param plan
     *            Logical plan this operator is a part of.
     * @param k
     *            Operator key to assign to this node.
     */
    public LODistinct(LogicalPlan plan, OperatorKey k) {

        super(plan, k);
    }

    public LogicalOperator getInput() {
        return mPlan.getPredecessors(this).get(0);
    }

    @Override
    public Schema getSchema() throws FrontendException {
        if (!mIsSchemaComputed) {
            // Get the schema of the parent
            Collection<LogicalOperator> s = mPlan.getPredecessors(this);
            ArrayList<Schema.FieldSchema> fss = new ArrayList<Schema.FieldSchema>();
            try {
                LogicalOperator op = s.iterator().next();
                if (null == op) {
                    int errCode = 1006;
                    String msg = "Could not find operator in plan";
                    throw new FrontendException(msg, errCode, PigException.BUG, false, null);
                }
                if(op instanceof ExpressionOperator) {
                    Schema.FieldSchema fs = new Schema.FieldSchema(((ExpressionOperator)op).getFieldSchema());
                    if(DataType.isSchemaType(fs.type)) {
                        mSchema = fs.schema;
                    } else {
                        fss.add(fs);
                        mSchema = new Schema(fss);
                        for (int i=0;i<getInput().getSchema().size();i++)
                            mSchema.getField(i).setParent(getInput().getSchema().getField(i).canonicalName, getInput());
                    }
                } else {
                    if (op.getSchema()!=null) {
                        mSchema = new Schema(op.getSchema());
                        for (int i=0;i<op.getSchema().size();i++)
                            mSchema.getField(i).setParent(op.getSchema().getField(i).canonicalName, op);
                    }
                    else
                        mSchema = null;
                }
                mIsSchemaComputed = true;
            } catch (FrontendException ioe) {
                mSchema = null;
                mIsSchemaComputed = false;
                throw ioe;
            }
        }
        return mSchema;
    }

    @Override
    public String name() {
        return "Distinct " + mKey.scope + "-" + mKey.id;
    }

    @Override
    public boolean supportsMultipleInputs() {
        return false;
    }

    @Override
    public void visit(LOVisitor v) throws VisitorException {
        v.visit(this);
    }

    @Override
    public byte getType() {
        return DataType.BAG ;
    }

    /**
     * @see org.apache.pig.impl.logicalLayer.LogicalOperator#clone()
     * Do not use the clone method directly. Operators are cloned when logical plans
     * are cloned using {@link LogicalPlanCloner}
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        LODistinct distinctClone = (LODistinct)super.clone();
        return distinctClone;
    }
    
    @Override
    public ProjectionMap getProjectionMap() {
        
        if(mIsProjectionMapComputed) return mProjectionMap;
        mIsProjectionMapComputed = true;
        
        Schema outputSchema;
        try {
            outputSchema = getSchema();
        } catch (FrontendException fee) {
            mProjectionMap = null;
            return mProjectionMap;
        }
        
        Schema inputSchema = null;        
        
        List<LogicalOperator> predecessors = (ArrayList<LogicalOperator>)mPlan.getPredecessors(this);
        if(predecessors != null) {
            try {
                inputSchema = predecessors.get(0).getSchema();
            } catch (FrontendException fee) {
                mProjectionMap = null;
                return mProjectionMap;
            }
        } else {
            mProjectionMap = null;
            return mProjectionMap;
        }
        
        if(Schema.equals(inputSchema, outputSchema, false, true)) {
            //there is a one is to one mapping between input and output schemas
            mProjectionMap = new ProjectionMap(false);
            return mProjectionMap;
        } else {
            //problem - input and output schemas for a distinct have to match!
            mProjectionMap = null;
            return mProjectionMap;
        }
    }
    
    @Override
    public List<RequiredFields> getRequiredFields() {
        List<RequiredFields> requiredFields = new ArrayList<RequiredFields>();
        requiredFields.add(new RequiredFields(false, true));
        return requiredFields;
    }

    @Override
    public List<RequiredFields> getRelevantInputs(int output, int column) throws FrontendException {
        if (!mIsSchemaComputed)
            getSchema();
        
        if (output!=0)
            return null;
        
        if (column<0)
            return null;
        
        // if we have schema information, check if output column is valid
        if (mSchema!=null)
        {
            if (column >= mSchema.size())
                return null;
        }
        
        ArrayList<Pair<Integer, Integer>> inputList = new ArrayList<Pair<Integer, Integer>>();
        inputList.add(new Pair<Integer, Integer>(0, column));
        List<RequiredFields> result = new ArrayList<RequiredFields>();
        result.add(new RequiredFields(inputList));
        return result;
    }
}
