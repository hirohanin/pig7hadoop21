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
package org.apache.pig.builtin;

import java.io.IOException;
import org.apache.pig.EvalFunc;
import org.apache.pig.PigException;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.schema.Schema;


/**
 * Generates the size of the first field of a tuple.
 */
public class BagSize extends EvalFunc<Long> {

    @Override
    public Long exec(Tuple input) throws IOException {
        try {
            DataBag bag = (DataBag)(input.get(0));
            return bag == null ? null : Long.valueOf(bag.size());
        } catch (ExecException exp) {
            throw exp;
        } catch (Exception e) {
            int errCode = 2106;
            String msg = "Error while computing size in " + this.getClass().getSimpleName();
            throw new ExecException(msg, errCode, PigException.BUG, e);            
        }
    }

    @Override
    public Schema outputSchema(Schema input) {
        return new Schema(new Schema.FieldSchema(null, DataType.LONG)); 
    }

}
