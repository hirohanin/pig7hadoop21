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

import java.io.Serializable;
import java.util.Arrays;

public class ResourceStatistics implements Cloneable {

    /* Getters intentionally return mutable arrays instead of copies,
     * to simplify updates without unnecessary copying.
     * Setters make a copy of the arrays in order to prevent an array
     * from being shared by two objects, with modifications in one
     * accidentally changing the other.
     */
    
    // arrays are initialized to empty so we don't have to worry about NPEs
    // setters disallow setting them to null.
    
    private static final long serialVersionUID = 1L;
    public Long mBytes; // size in megabytes
    public Long numRecords;  // number of records
    public Long avgRecordSize;
    public ResourceFieldStatistics[] fields = new ResourceFieldStatistics[0];

    public static class ResourceFieldStatistics implements Serializable {

        public static final long serialVersionUID = 1L;

        public int version;

        public Long numDistinctValues;  // number of distinct values represented in this field

        // We need some way to represent a histogram of values in the field,
        // as those will be useful.  However, we can't count on being
        // able to hold such histograms in memory.  Have to figure out
        // how they can be kept on disk and represented here.

        // for now.. don't create so many buckets you can't hold them in memory
        
        // an ordered array of the most common values, 
        // in descending order of frequency
        public Object[] mostCommonValues = new Object[0];
        
        // an array that matches the mostCommonValues array, and lists
        // the frequencies of those values as a fraction (0 through 1) of
        // the total number of records
        public float[] mostCommonValuesFreq = new float[0];
        
        // an ordered array of values, from min val to max val
        // such that the number of records with values 
        // between valueHistogram[i] and and valueHistogram[i+1] is
        // roughly equal for all values of i.
        // NOTE: if mostCommonValues is non-empty, the values in that array
        // should not be included in the histogram. Adjust accordingly.
        public Object[] valueHistogram = new Object[0];

        
        public int getVersion() {
            return version;
        }

        public ResourceFieldStatistics setVersion(int version) {
            this.version = version;  
            return this;
        }

        public Long getNumDistinctValues() {
            return numDistinctValues;
        }

        public ResourceFieldStatistics setNumDistinctValues(Long numDistinctValues) {
            this.numDistinctValues = numDistinctValues; 
            return this;
        }

        public Object[] getMostCommonValues() {
            return mostCommonValues;
        }

        public ResourceFieldStatistics  setMostCommonValues(Object[] mostCommonValues) {
            if (mostCommonValues !=null)
                this.mostCommonValues = 
                    Arrays.copyOf(mostCommonValues, mostCommonValues.length);
            return this;
        }

        public float[] getMostCommonValuesFreq() {
            return mostCommonValuesFreq;
        }

        public ResourceFieldStatistics setMostCommonValuesFreq(float[] mostCommonValuesFreq) {
            if (mostCommonValuesFreq != null) 
                this.mostCommonValuesFreq = 
                    Arrays.copyOf(mostCommonValuesFreq, mostCommonValuesFreq.length);
            return this;
        }

        public Object[] getValueHistogram() {
            return valueHistogram;
        }

        public ResourceFieldStatistics  setValueHistogram(Object[] valueHistogram) {
            if (valueHistogram != null) 
                this.valueHistogram = Arrays.copyOf(valueHistogram, valueHistogram.length);
            return this;
        }

        
        /*
         * equals() and hashCode() overridden mostly for ease of testing
         * you shouldn't encounter a situation in which you need to .equals()
         * two sets of statistics on different objects "in the wild"
         */
        @Override
        public boolean equals(Object anOther) {
            if (anOther == null || !(anOther.getClass().equals(this.getClass())))
                return false;
            ResourceFieldStatistics other = (ResourceFieldStatistics) anOther;
            // setters do not allow null values, so no worries about NPEs here
            return (Arrays.equals(mostCommonValues, other.mostCommonValues) &&
                    Arrays.equals(mostCommonValuesFreq, other.mostCommonValuesFreq) &&
                    Arrays.equals(valueHistogram, other.valueHistogram) &&
                    this.numDistinctValues.equals(other.numDistinctValues) &&
                    this.version == other.version
                    );
        }
        
        /**
         * A naive hashCode implementation following the example in IBM's developerworks:
         * http://www.ibm.com/developerworks/java/library/j-jtp05273.html
         */
        @Override
        public int hashCode() {
            int hash = 1;
            hash = 31 * hash +  Arrays.hashCode(mostCommonValues);
            hash = 31 * hash + Arrays.hashCode(mostCommonValuesFreq);
            hash = 31 * hash + numDistinctValues.hashCode();
            hash = 31 * hash + Arrays.hashCode(valueHistogram);
            hash = 31 * hash + version;
            return 0;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ResourceStatistics. Version: "+version+"\n");
            sb.append("MCV:\n");
            for (Object o : mostCommonValues) sb.append('['+ o.toString() +']');
            sb.append("\n MCVfreq:\n");
            for (Float f : mostCommonValuesFreq) sb.append('['+f.toString()+']');
            sb.append("\n");
            sb.append("numDistVals: "+numDistinctValues);
            sb.append("valHistogram: \n");
            for (Object o : valueHistogram) sb.append('['+o.toString()+']');
            sb.append("\n");
            return sb.toString();
        }
    }

    
    public Long getmBytes() {
        return mBytes;
    }
    public ResourceStatistics setmBytes(Long mBytes) {
        this.mBytes = mBytes;
        return this;
    }
    public Long getNumRecords() {
        return numRecords;
    }
    public ResourceStatistics setNumRecords(Long numRecords) {
        this.numRecords = numRecords;
        return this;
    }
    
    /* 
     * returns average record size. This number can be explicitly specified by statistics, or
     * if absent, computed using totalbytes/totalrecords. Will return null if can't be computed.
     */
    public Long getAvgRecordSize() {
        if (avgRecordSize == null && (mBytes != null && numRecords != null))
            return mBytes / numRecords;
        else 
            return avgRecordSize;
    }
    
    public void setAvgRecordSize(Long size) {
        avgRecordSize = size;
    }
    
    public ResourceFieldStatistics[] getFields() {
        return fields;
    }
    
    public ResourceStatistics setFields(ResourceFieldStatistics[] fields) {
        if (fields != null) 
            this.fields = Arrays.copyOf(fields, fields.length);
        return this;
    }


    /*
     * equals() and hashCode() overridden mostly for ease of testing
     * you shouldn't encounter a situation in which you need to .equals()
     * two sets of statistics on different objects "in the wild"
     */
    @Override
    public boolean equals(Object anOther) {
        if (anOther == null || !(anOther.getClass().equals(this.getClass())))
            return false;        
        ResourceStatistics other = (ResourceStatistics) anOther;
        return (Arrays.equals(fields, other.fields) &&
                ((mBytes==null) 
                        ? (other.mBytes==null) : mBytes.equals(other.mBytes)) &&
                ((numRecords == null) 
                        ? (other.numRecords==null) : numRecords.equals(other.numRecords)) 
        );
    }
    
    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31*hash + Arrays.hashCode(fields);
        hash = 31*hash + (mBytes == null ? 0 : mBytes.hashCode());
        hash = 31*hash + (numRecords == null ? 0 : numRecords.hashCode());
        return hash;
    }
    // Probably more in here
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Field Stats: \n");
        for (ResourceFieldStatistics f : fields) sb.append(f.toString());
        sb.append("mBytes: "+mBytes);
        sb.append("numRecords: "+numRecords);
        return sb.toString();
    }
    
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
      }
}