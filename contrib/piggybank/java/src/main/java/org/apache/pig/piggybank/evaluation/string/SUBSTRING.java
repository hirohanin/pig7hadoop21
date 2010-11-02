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

package org.apache.pig.piggybank.evaluation.string;

import java.io.IOException;

import org.apache.pig.EvalFunc;
import org.apache.pig.PigWarning;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.DataType;
import org.apache.pig.impl.logicalLayer.schema.Schema;



/**
 * string.SUBSTRING implements eval function to get a part of a string.
 * Example:<code>
 *      register pigudfs.jar;
 *      A = load 'mydata' as (name);
 *      B = foreach A generate string.SUBSTRING(name, 10, 12);
 *      dump B;
 *      </code>
 * First argument is the string to take a substring of.<br>
 * Second argument is the index of the first character of substring.<br>
 * Third argument is the index of the last character of substring.<br>
 * if the last argument is past the end of the string, substring of (beginIndex, length(str)) is returned.
 */
public class SUBSTRING extends EvalFunc<String> {

    /**
     * Method invoked on every tuple during foreach evaluation
     * @param input tuple; first column is assumed to have the column to convert
     * @exception java.io.IOException
     */
    public String exec(Tuple input) throws IOException {
        if (input == null || input.size() < 3) {
            log.warn("invalid number of arguments to SUBSTRING");
            return null;
        }
        try {
            String source = (String)input.get(0);
            Integer beginindex = (Integer)input.get(1);
            Integer endindex = (Integer)input.get(2);
            return source.substring(beginindex, Math.min(source.length(), endindex));
        } catch (NullPointerException npe) {
            log.warn(npe.toString());
            return null;
        } catch (ClassCastException e) {
            log.warn(e.toString());
            return null;
        }
    }

    @Override
    public Schema outputSchema(Schema input) {
        return new Schema(new Schema.FieldSchema(null, DataType.CHARARRAY));
    }

}