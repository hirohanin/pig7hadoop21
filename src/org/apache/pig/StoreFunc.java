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
package org.apache.pig;

import java.io.IOException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.util.UDFContext;


/**
* This abstract class is used to implement functions to write records
* from a dataset.
* 
*
*/

public abstract class StoreFunc implements StoreFuncInterface {

    /**
     * This method is called by the Pig runtime in the front end to convert the
     * output location to an absolute path if the location is relative. The
     * StoreFunc implementation is free to choose how it converts a relative 
     * location to an absolute location since this may depend on what the location
     * string represent (hdfs path or some other data source). 
     *  
     * 
     * @param location location as provided in the "store" statement of the script
     * @param curDir the current working direction based on any "cd" statements
     * in the script before the "store" statement. If there are no "cd" statements
     * in the script, this would be the home directory - 
     * <pre>/user/<username> </pre>
     * @return the absolute location based on the arguments passed
     * @throws IOException 
     * @throws IOException if the conversion is not possible
     */
    @Override
    public String relToAbsPathForStoreLocation(String location, Path curDir) 
    throws IOException {
        return LoadFunc.getAbsolutePath(location, curDir);
    }

    /**
     * Return the OutputFormat associated with StoreFunc.  This will be called
     * on the front end during planning and not on the backend during
     * execution. 
     * @return the {@link OutputFormat} associated with StoreFunc
     * @throws IOException if an exception occurs while constructing the 
     * OutputFormat
     *
     */
    public abstract OutputFormat getOutputFormat() throws IOException;

    /**
     * Communicate to the storer the location where the data needs to be stored.  
     * The location string passed to the {@link StoreFunc} here is the 
     * return value of {@link StoreFunc#relToAbsPathForStoreLocation(String, Path)} 
     * This method will be called in the frontend and backend multiple times. Implementations
     * should bear in mind that this method is called multiple times and should
     * ensure there are no inconsistent side effects due to the multiple calls.
     * {@link #checkSchema(ResourceSchema)} will be called before any call to
     * {@link #setStoreLocation(String, Job)}.
     * 

     * @param location Location returned by 
     * {@link StoreFunc#relToAbsPathForStoreLocation(String, Path)}
     * @param job The {@link Job} object
     * @throws IOException if the location is not valid.
     */
    public abstract void setStoreLocation(String location, Job job) throws IOException;
 
    /**
     * Set the schema for data to be stored.  This will be called on the
     * front end during planning if the store is associated with a schema.
     * A Store function should implement this function to
     * check that a given schema is acceptable to it.  For example, it
     * can check that the correct partition keys are included;
     * a storage function to be written directly to an OutputFormat can
     * make sure the schema will translate in a well defined way.  
     * @param s to be checked
     * @throws IOException if this schema is not acceptable.  It should include
     * a detailed error message indicating what is wrong with the schema.
     */
    @Override
    public void checkSchema(ResourceSchema s) throws IOException {
        // default implementation is a no-op
    }

    /**
     * Initialize StoreFunc to write data.  This will be called during
     * execution before the call to putNext.
     * @param writer RecordWriter to use.
     * @throws IOException if an exception occurs during initialization
     */
    public abstract void prepareToWrite(RecordWriter writer) throws IOException;

    /**
     * Write a tuple the output stream to which this instance was
     * previously bound.
     * 
     * @param t the tuple to store.
     * @throws IOException if an exception occurs during the write
     */
    public abstract void putNext(Tuple t) throws IOException;
    
    /**
     * This method will be called by Pig both in the front end and back end to
     * pass a unique signature to the {@link StoreFunc} which it can use to store
     * information in the {@link UDFContext} which it needs to store between
     * various method invocations in the front end and back end. This method 
     * will be called before other methods in {@link StoreFunc}.
     * @param signature a unique signature to identify this StoreFunc
     */
    @Override
    public void setStoreFuncUDFContextSignature(String signature) {
        // default implementation is a no-op
    }
    
    /**
     * This method will be called by Pig if the job which contains this store
     * fails. Implementations can clean up output locations in this method to
     * ensure that no incorrect/incomplete results are left in the output location.
     * The implementation in {@link StoreFunc} deletes the output location if it
     * is a {@link FileSystem} location.
     * @param location Location returned by 
     * {@link StoreFunc#relToAbsPathForStoreLocation(String, Path)}
     * @param job The {@link Job} object - this should be used only to obtain 
     * cluster properties through {@link Job#getConfiguration()} and not to set/query
     * any runtime job information. 
     */
    @Override
    public void cleanupOnFailure(String location, Job job) 
    throws IOException {
        cleanupOnFailureImpl(location, job);
    }
    
    /**
     * Implementation for {@link #cleanupOnFailure(String, Job)}
     * @param location
     * @param job
     * @throws IOException
     */
    public static void cleanupOnFailureImpl(String location, Job job) 
    throws IOException {
        FileSystem fs = FileSystem.get(job.getConfiguration());
        Path path = new Path(location);
        if(fs.exists(path)){
            fs.delete(path, true);
        }    
    }
}
