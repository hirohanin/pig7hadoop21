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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.FuncSpec;
import org.apache.pig.LoadCaster;
import org.apache.pig.LoadFunc;
import org.apache.pig.PigException;
import org.apache.pig.ResourceSchema.ResourceFieldSchema;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.NodeIdGenerator;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.StreamToPig;
import org.apache.pig.impl.util.CastUtils;

/**
 * This is just a cast that converts DataByteArray into either String or
 * Integer. Just added it for testing the POUnion. Need the full operator
 * implementation.
 */
public class POCast extends ExpressionOperator {
    private FuncSpec funcSpec = null;
    transient private LoadCaster caster;
    transient private Log log = LogFactory.getLog(getClass());
    private boolean castNotNeeded = false;
    private Byte realType = null;
    private transient List<ExpressionOperator> child;
    private ResourceFieldSchema fieldSchema = null;

    private static final long serialVersionUID = 1L;

    public POCast(OperatorKey k) {
        super(k);
    }

    public POCast(OperatorKey k, int rp) {
        super(k, rp);
    }

    private void instantiateFunc() throws IOException {
        if (caster != null) return;
           
        if (funcSpec != null) {
            Object obj = PigContext
                    .instantiateFuncFromSpec(funcSpec);
            if (obj instanceof LoadFunc) {
                caster = ((LoadFunc)obj).getLoadCaster();
            } else if (obj instanceof StreamToPig) {
                caster = ((StreamToPig)obj).getLoadCaster();
            } else {
                throw new IOException("Invalid class type "
                        + funcSpec.getClassName());
            }
        }        
    }

    public void setFuncSpec(FuncSpec lf) throws IOException {
        this.funcSpec = lf;
        instantiateFunc();
    }

    @Override
    public void visit(PhyPlanVisitor v) throws VisitorException {
        v.visitCast(this);
    }

    @Override
    public String name() {
        if (resultType==DataType.BAG||resultType==DataType.TUPLE)
            return "Cast" + "[" + DataType.findTypeName(resultType)+":"
            + fieldSchema.calcCastString() + "]" + " - "
            + mKey.toString();
        else
            return "Cast" + "[" + DataType.findTypeName(resultType) + "]" + " - "
                + mKey.toString();
    }

    @Override
    public boolean supportsMultipleInputs() {
        return false;
    }

    @Override
    public Result getNext(Integer i) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte resultType = in.getResultType();
        switch (resultType) {
        case DataType.BAG: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.TUPLE: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // res.result is not of type ByteArray. But it can be one of the types from which cast is still possible.
                    if (realType == null)
                        // Find the type and cache it.
                        realType = DataType.findType(res.result);
                    try {
                        res.result = DataType.toInteger(res.result, realType);
                    } catch (ClassCastException cce) {
                        // Type has changed. Need to find type again and try casting it again.
                        realType = DataType.findType(res.result);
                        res.result = DataType.toInteger(res.result, realType);
                    }
                    return res;
                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToInteger(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to int.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Integer");
                }
            }
            return res;
        }

        case DataType.MAP: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BOOLEAN: {
            Boolean b = null;
            Result res = in.getNext(b);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                if (((Boolean) res.result) == true)
                    res.result = Integer.valueOf(1);
                else
                    res.result = Integer.valueOf(0);
            }
            return res;
        }
        case DataType.INTEGER: {

            Result res = in.getNext(i);
            return res;
        }

        case DataType.DOUBLE: {
            Double d = null;
            Result res = in.getNext(d);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = DataType.toInteger(res.result);
                res.result = Integer.valueOf(((Double) res.result).intValue());
            }
            return res;
        }

        case DataType.LONG: {
            Long l = null;
            Result res = in.getNext(l);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = Integer.valueOf(((Long) res.result).intValue());
            }
            return res;
        }

        case DataType.FLOAT: {
            Float f = null;
            Result res = in.getNext(f);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = Integer.valueOf(((Float) res.result).intValue());
            }
            return res;
        }

        case DataType.CHARARRAY: {
            String str = null;
            Result res = in.getNext(str);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = CastUtils.stringToInteger((String)res.result);
            }
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(Long l) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte resultType = in.getResultType();
        switch (resultType) {
        case DataType.BAG: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.TUPLE: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.MAP: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // res.result is not of type ByteArray. But it can be one of the types from which cast is still possible.
                    if (realType == null)
                        // Find the type in first call and cache it.
                        realType = DataType.findType(res.result);
                    try {
                        res.result = DataType.toLong(res.result, realType);
                    } catch (ClassCastException cce) {
                        // Type has changed. Need to find type again and try casting it again.
                        realType = DataType.findType(res.result);
                        res.result = DataType.toLong(res.result, realType);
                    }
                    return res;
                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToLong(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to long.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Long");
                }
            }
            return res;
        }

        case DataType.BOOLEAN: {
            Boolean b = null;
            Result res = in.getNext(b);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                if (((Boolean) res.result) == true)
                    res.result = Long.valueOf(1);
                else
                    res.result = Long.valueOf(0);
            }
            return res;
        }
        case DataType.INTEGER: {
            Integer dummyI = null;
            Result res = in.getNext(dummyI);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = Long.valueOf(((Integer) res.result).longValue());
            }
            return res;
        }

        case DataType.DOUBLE: {
            Double d = null;
            Result res = in.getNext(d);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = DataType.toInteger(res.result);
                res.result = Long.valueOf(((Double) res.result).longValue());
            }
            return res;
        }

        case DataType.LONG: {

            Result res = in.getNext(l);

            return res;
        }

        case DataType.FLOAT: {
            Float f = null;
            Result res = in.getNext(f);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = Long.valueOf(((Float) res.result).longValue());
            }
            return res;
        }

        case DataType.CHARARRAY: {
            String str = null;
            Result res = in.getNext(str);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = CastUtils.stringToLong((String)res.result);
            }
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(Double d) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte resultType = in.getResultType();
        switch (resultType) {
        case DataType.BAG: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.TUPLE: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.MAP: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // res.result is not of type ByteArray. But it can be one of the types from which cast is still possible.
                    if (realType == null)
                        // Find the type in first call and cache it.
                        realType = DataType.findType(res.result);
                    try {
                        res.result = DataType.toDouble(res.result, realType);
                    } catch (ClassCastException cce) {
                        // Type has changed. Need to find type again and try casting it again.
                        realType = DataType.findType(res.result);
                        res.result = DataType.toDouble(res.result, realType);
                    }
                    return res;
                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToDouble(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to double.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Double");
                }
            }
            return res;
        }

        case DataType.BOOLEAN: {
            Boolean b = null;
            Result res = in.getNext(b);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                if (((Boolean) res.result) == true)
                    res.result = new Double(1);
                else
                    res.result = new Double(0);
            }
            return res;
        }
        case DataType.INTEGER: {
            Integer dummyI = null;
            Result res = in.getNext(dummyI);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = new Double(((Integer) res.result).doubleValue());
            }
            return res;
        }

        case DataType.DOUBLE: {

            Result res = in.getNext(d);

            return res;
        }

        case DataType.LONG: {
            Long l = null;
            Result res = in.getNext(l);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = new Double(((Long) res.result).doubleValue());
            }
            return res;
        }

        case DataType.FLOAT: {
            Float f = null;
            Result res = in.getNext(f);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = new Double(((Float) res.result).doubleValue());
            }
            return res;
        }

        case DataType.CHARARRAY: {
            String str = null;
            Result res = in.getNext(str);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = CastUtils.stringToDouble((String)res.result);
            }
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(Float f) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte resultType = in.getResultType();
        switch (resultType) {
        case DataType.BAG: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.TUPLE: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.MAP: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // res.result is not of type ByteArray. But it can be one of the types from which cast is still possible.
                    if (realType == null)
                        // Find the type in first call and cache it.
                        realType = DataType.findType(res.result);
                    try {
                        res.result = DataType.toFloat(res.result, realType);
                    } catch (ClassCastException cce) {
                        // Type has changed. Need to find type again and try casting it again.
                        realType = DataType.findType(res.result);
                        res.result = DataType.toFloat(res.result, realType);
                    }
                    return res;
                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToFloat(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to float.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Float");
                }
            }
            return res;
        }

        case DataType.BOOLEAN: {
            Boolean b = null;
            Result res = in.getNext(b);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                if (((Boolean) res.result) == true)
                    res.result = new Float(1);
                else
                    res.result = new Float(0);
            }
            return res;
        }
        case DataType.INTEGER: {
            Integer dummyI = null;
            Result res = in.getNext(dummyI);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = new Float(((Integer) res.result).floatValue());
            }
            return res;
        }

        case DataType.DOUBLE: {
            Double d = null;
            Result res = in.getNext(d);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = DataType.toInteger(res.result);
                res.result = new Float(((Double) res.result).floatValue());
            }
            return res;
        }

        case DataType.LONG: {

            Long l = null;
            Result res = in.getNext(l);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = new Float(((Long) res.result).floatValue());
            }
            return res;
        }

        case DataType.FLOAT: {

            Result res = in.getNext(f);

            return res;
        }

        case DataType.CHARARRAY: {
            String str = null;
            Result res = in.getNext(str);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = CastUtils.stringToFloat((String)res.result);
            }
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(String str) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte resultType = in.getResultType();
        switch (resultType) {
        case DataType.BAG: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.TUPLE: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.MAP: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // res.result is not of type ByteArray. But it can be one of the types from which cast is still possible.
                    if (realType == null)
                        // Find the type in first call and cache it.
                        realType = DataType.findType(res.result);
                    try {
                        res.result = DataType.toString(res.result, realType);
                    } catch (ClassCastException cce) {
                        // Type has changed. Need to find type again and try casting it again.
                        realType = DataType.findType(res.result);
                        res.result = DataType.toString(res.result, realType);
                    }
                    return res;
                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToCharArray(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to string.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log
                            .error("Error while casting from ByteArray to CharArray");
                }
            }
            return res;
        }

        case DataType.BOOLEAN: {
            Boolean b = null;
            Result res = in.getNext(b);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                if (((Boolean) res.result) == true)
                    res.result = "1";
                else
                    res.result = "0";
            }
            return res;
        }
        case DataType.INTEGER: {
            Integer dummyI = null;
            Result res = in.getNext(dummyI);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = ((Integer) res.result).toString();
            }
            return res;
        }

        case DataType.DOUBLE: {
            Double d = null;
            Result res = in.getNext(d);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = DataType.toInteger(res.result);
                res.result = ((Double) res.result).toString();
            }
            return res;
        }

        case DataType.LONG: {

            Long l = null;
            Result res = in.getNext(l);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = ((Long) res.result).toString();
            }
            return res;
        }

        case DataType.FLOAT: {
            Float f = null;
            Result res = in.getNext(f);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                res.result = ((Float) res.result).toString();
            }
            return res;
        }

        case DataType.CHARARRAY: {
            Result res = in.getNext(str);

            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(Tuple t) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte castToType = DataType.TUPLE;
        Byte resultType = in.getResultType();
        switch (resultType) {

        case DataType.TUPLE: {
            Result res = in.getNext(t);
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = new
                // String(((DataByteArray)res.result).toString());
                if (castNotNeeded) {
                    // we examined the data once before and
                    // determined that the input is the same
                    // type as the type we are casting to
                    // so just send the input out as output
                    return res;
                }
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // check if the type of res.result is
                    // same as the type we are trying to cast to
                    if (DataType.findType(res.result) == castToType) {
                        // remember this for future calls
                        castNotNeeded = true;
                        // just return the output
                        return res;
                    } else {
                        // the input is a differen type
                        // rethrow the exception
                        int errCode = 1081;
                        String msg = "Cannot cast to tuple. Expected bytearray but received: " + DataType.findTypeName(res.result);
                        throw new ExecException(msg, errCode, PigException.INPUT, e);
                    }

                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToTuple(dba.get(), fieldSchema);
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to tuple.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Tuple");
                }
            }
            return res;
        }

        case DataType.BAG:

        case DataType.MAP:

        case DataType.INTEGER:

        case DataType.DOUBLE:

        case DataType.LONG:

        case DataType.FLOAT:

        case DataType.CHARARRAY:

        case DataType.BOOLEAN: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(DataBag bag) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte castToType = DataType.BAG;
        Byte resultType = in.getResultType();
        switch (resultType) {

        case DataType.BAG: {
            Result res = in.getNext(bag);
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = new
                // String(((DataByteArray)res.result).toString());
                if (castNotNeeded) {
                    // we examined the data once before and
                    // determined that the input is the same
                    // type as the type we are casting to
                    // so just send the input out as output
                    return res;
                }
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // check if the type of res.result is
                    // same as the type we are trying to cast to
                    if (DataType.findType(res.result) == castToType) {
                        // remember this for future calls
                        castNotNeeded = true;
                        // just return the output
                        return res;
                    } else {
                        // the input is a differen type
                        // rethrow the exception
                        int errCode = 1081;
                        String msg = "Cannot cast to bag. Expected bytearray but received: " + DataType.findTypeName(res.result);
                        throw new ExecException(msg, errCode, PigException.INPUT, e);
                    }

                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToBag(dba.get(), fieldSchema);
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to bag.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to DataBag");
                }
            }
            return res;
        }

        case DataType.TUPLE:

        case DataType.MAP:

        case DataType.INTEGER:

        case DataType.DOUBLE:

        case DataType.LONG:

        case DataType.FLOAT:

        case DataType.CHARARRAY:

        case DataType.BOOLEAN: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    @Override
    public Result getNext(Map m) throws ExecException {
        PhysicalOperator in = inputs.get(0);
        Byte castToType = DataType.MAP;
        Byte resultType = in.getResultType();
        switch (resultType) {

        case DataType.MAP: {
            Result res = in.getNext(m);
            return res;
        }

        case DataType.BYTEARRAY: {
            DataByteArray dba = null;
            Result res = in.getNext(dba);
            if (res.returnStatus == POStatus.STATUS_OK && res.result != null) {
                // res.result = new
                // String(((DataByteArray)res.result).toString());
                if (castNotNeeded) {
                    // we examined the data once before and
                    // determined that the input is the same
                    // type as the type we are casting to
                    // so just send the input out as output
                    return res;
                }
                try {
                    dba = (DataByteArray) res.result;
                } catch (ClassCastException e) {
                    // check if the type of res.result is
                    // same as the type we are trying to cast to
                    if (DataType.findType(res.result) == castToType) {
                        // remember this for future calls
                        castNotNeeded = true;
                        // just return the output
                        return res;
                    } else {
                        // the input is a differen type
                        // rethrow the exception
                        int errCode = 1081;
                        String msg = "Cannot cast to map. Expected bytearray but received: " + DataType.findTypeName(res.result);
                        throw new ExecException(msg, errCode, PigException.INPUT, e);
                    }

                }
                try {
                    if (null != caster) {
                        res.result = caster.bytesToMap(dba.get());
                    } else {
                        int errCode = 1075;
                        String msg = "Received a bytearray from the UDF. Cannot determine how to convert the bytearray to map.";
                        throw new ExecException(msg, errCode, PigException.INPUT);
                    }
                } catch (ExecException ee) {
                    throw ee;
                } catch (IOException e) {
                    log.error("Error while casting from ByteArray to Map");
                }
            }
            return res;
        }

        case DataType.TUPLE:

        case DataType.BAG:

        case DataType.INTEGER:

        case DataType.DOUBLE:

        case DataType.LONG:

        case DataType.FLOAT:

        case DataType.CHARARRAY:

        case DataType.BOOLEAN: {
            Result res = new Result();
            res.returnStatus = POStatus.STATUS_ERR;
            return res;
        }

        }

        Result res = new Result();
        res.returnStatus = POStatus.STATUS_ERR;
        return res;
    }

    private void readObject(ObjectInputStream is) throws IOException,
            ClassNotFoundException {
        is.defaultReadObject();
        instantiateFunc();
    }

    @Override
    public POCast clone() throws CloneNotSupportedException {
        POCast clone = new POCast(new OperatorKey(mKey.scope, NodeIdGenerator
                .getGenerator().getNextNodeId(mKey.scope)));
        clone.cloneHelper(this);
        clone.funcSpec = funcSpec;
        clone.fieldSchema = fieldSchema;
        try {
            clone.instantiateFunc();
        } catch (IOException e) {
            CloneNotSupportedException cnse = new CloneNotSupportedException();
            cnse.initCause(e);
            throw cnse;
        }
        return clone;
    }

    /**
     * Get child expression of this expression
     */
    @Override
    public List<ExpressionOperator> getChildExpressions() {
        if (child == null) {
            child = new ArrayList<ExpressionOperator>();
            if (inputs.get(0) instanceof ExpressionOperator) {
                child.add( (ExpressionOperator)inputs.get(0));		
            }
        }
        
        return child;				
    }
    
    public void setFieldSchema(ResourceFieldSchema s) {
        fieldSchema = s;
    }
    
    public FuncSpec getFuncSpec() {
        return funcSpec;
    }

}
