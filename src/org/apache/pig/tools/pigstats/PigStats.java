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

package org.apache.pig.tools.pigstats;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.mapred.Counters;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.mapred.jobcontrol.JobControl;
import org.apache.pig.ExecType;
import org.apache.pig.PigCounters;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.plans.MROperPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POStore;
import org.apache.pig.data.BagFactory;
import org.apache.pig.impl.util.ObjectSerializer;

public class PigStats {
    MROperPlan mrp;
    PhysicalPlan php;
    JobControl jc;
    JobClient jobClient;
    Map<String, Map<String, String>> stats = new HashMap<String, Map<String,String>>();
    // String lastJobID;
    ArrayList<String> rootJobIDs = new ArrayList<String>();
    ExecType mode;
    
    private static final String localModeDataFile = "part-00000";
    
    public void setMROperatorPlan(MROperPlan mrp) {
        this.mrp = mrp;
    }
    
    public void setJobControl(JobControl jc) {
        this.jc = jc;
    }
    
    public void setJobClient(JobClient jobClient) {
        this.jobClient = jobClient;
    }
    
    public String getMRPlan() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mrp.dump(new PrintStream(baos));
        return baos.toString();
    }
    
    public void setExecType(ExecType mode) {
        this.mode = mode;
    }
    
    public void setPhysicalPlan(PhysicalPlan php) {
        this.php = php;
    }
    
    public String getPhysicalPlan() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        php.explain(baos);
        return baos.toString();
    }
    
    public Map<String, Map<String, String>> accumulateStats() throws ExecException {
        if(mode == ExecType.MAPREDUCE)
            return accumulateMRStats();
        else if(mode == ExecType.LOCAL)
            return accumulateLocalStats();
        else
            throw new RuntimeException("Unrecognized mode. Either MapReduce or Local mode expected.");
    }
    
    private Map<String, Map<String, String>> accumulateLocalStats() {
        //The counter placed before a store in the local plan should be able to get the number of records
        for(PhysicalOperator op : php.getLeaves()) {
            Map<String, String> jobStats = new HashMap<String, String>();
            stats.put(op.toString(), jobStats);         
            String localFilePath=normalizeToLocalFilePath(((POStore)op).getSFile().getFileName());
            File outputFile = new File( localFilePath + File.separator + localModeDataFile );
            
            long lineCounter = 0;
            try {
                BufferedReader in = new BufferedReader(new FileReader( outputFile ));
                @SuppressWarnings("unused")
                String tmpString = null;
                while( (tmpString = in.readLine()) != null ) {
                    lineCounter++;
                }
                in.close();
            } catch (FileNotFoundException e) {
            } catch (IOException e) {                
            } finally {
                jobStats.put("PIG_STATS_LOCAL_OUTPUT_RECORDS", (Long.valueOf(lineCounter)).toString());
            }            
            jobStats.put("PIG_STATS_LOCAL_BYTES_WRITTEN", (Long.valueOf(outputFile.length())).toString());
        }
        return stats;
    }
    
    private String normalizeToLocalFilePath(String fileName) {
        if (fileName.startsWith("file:")){
            return fileName.substring(5);
        }
        return fileName;
    }

    private Map<String, Map<String, String>> accumulateMRStats() throws ExecException {
        
        for(Job job : jc.getSuccessfulJobs()) {
            
            
            JobConf jobConf = job.getJobConf();
            
            
                RunningJob rj = null;
                try {
                    rj = jobClient.getJob(JobID.downgrade(job.getJob().getJobID()));
                } catch (IOException e1) {
                    String error = "Unable to get the job statistics from JobClient.";
                    throw new ExecException(error, e1);
                }
                if(rj == null)
                    continue;
                
                Map<String, String> jobStats = new HashMap<String, String>();
                stats.put(JobID.downgrade(job.getJob().getJobID()).toString(), jobStats);
                
                try {
                    PhysicalPlan plan = (PhysicalPlan) ObjectSerializer.deserialize(jobConf.get("pig.mapPlan"));
                    jobStats.put("PIG_STATS_MAP_PLAN", plan.toString());
                    plan = (PhysicalPlan) ObjectSerializer.deserialize(jobConf.get("pig.combinePlan"));
                    if(plan != null) {
                        jobStats.put("PIG_STATS_COMBINE_PLAN", plan.toString());
                    }
                    plan = (PhysicalPlan) ObjectSerializer.deserialize(jobConf.get("pig.reducePlan"));
                    if(plan != null) {
                        jobStats.put("PIG_STATS_REDUCE_PLAN", plan.toString());
                    }
                } catch (IOException e2) {
                    String error = "Error deserializing plans from the JobConf.";
                    throw new RuntimeException(error, e2);
                }
                
                Counters counters = null;
                try {
                    counters = rj.getCounters();
                    // This code checks if the counters is null, if it is, then all the stats are unknown.
                    // We use -1 to indicate unknown counter. In fact, Counters should not be null, it is
                    // a hadoop bug, once this bug is fixed in hadoop, the null handling code should never be hit.
                    // See Pig-943
                    if (counters!=null)
                    {
                        Counters.Group taskgroup = counters.getGroup("org.apache.hadoop.mapred.Task$Counter");
                        Counters.Group hdfsgroup = counters.getGroup("FileSystemCounters");
                        jobStats.put("PIG_STATS_MAP_INPUT_RECORDS", (Long.valueOf(taskgroup.getCounterForName("MAP_INPUT_RECORDS").getCounter())).toString());
                        jobStats.put("PIG_STATS_MAP_OUTPUT_RECORDS", (Long.valueOf(taskgroup.getCounterForName("MAP_OUTPUT_RECORDS").getCounter())).toString());
                        jobStats.put("PIG_STATS_REDUCE_INPUT_RECORDS", (Long.valueOf(taskgroup.getCounterForName("REDUCE_INPUT_RECORDS").getCounter())).toString());
                        jobStats.put("PIG_STATS_REDUCE_OUTPUT_RECORDS", (Long.valueOf(taskgroup.getCounterForName("REDUCE_OUTPUT_RECORDS").getCounter())).toString());
                        jobStats.put("PIG_STATS_BYTES_WRITTEN", (Long.valueOf(hdfsgroup.getCounterForName("HDFS_BYTES_WRITTEN").getCounter())).toString());
                        jobStats.put("PIG_STATS_SMM_SPILL_COUNT", (Long.valueOf(counters.findCounter(PigCounters.SPILLABLE_MEMORY_MANAGER_SPILL_COUNT).getCounter())).toString() );
                        jobStats.put("PIG_STATS_PROACTIVE_SPILL_COUNT", (Long.valueOf(counters.findCounter(PigCounters.PROACTIVE_SPILL_COUNT).getCounter())).toString() );

                    }
                    else
                    {
                        jobStats.put("PIG_STATS_MAP_INPUT_RECORDS", "-1");
                        jobStats.put("PIG_STATS_MAP_OUTPUT_RECORDS", "-1");
                        jobStats.put("PIG_STATS_REDUCE_INPUT_RECORDS", "-1");
                        jobStats.put("PIG_STATS_REDUCE_OUTPUT_RECORDS", "-1");
                        jobStats.put("PIG_STATS_BYTES_WRITTEN", "-1");
                        jobStats.put("PIG_STATS_SMM_SPILL_COUNT", "-1");
                        jobStats.put("PIG_STATS_PROACTIVE_SPILL_COUNT", "-1");
                    }
                    
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    String error = "Unable to get the counters.";
                    throw new ExecException(error, e);
                }
        }
        
        getLastJobIDs(jc.getSuccessfulJobs());
        
        return stats;
    }
    

    private void getLastJobIDs(List<Job> jobs) {
        rootJobIDs.clear();
         Set<Job> temp = new HashSet<Job>();
         for(Job job : jobs) {
             if(job.getDependingJobs() != null && job.getDependingJobs().size() > 0)
                 temp.addAll(job.getDependingJobs());
         }
         
         //difference between temp and jobs would be the set of leaves
         //we can safely assume there would be only one leaf
         for(Job job : jobs) {
             if(temp.contains(job)) continue;
             else rootJobIDs.add(JobID.downgrade(job.getJob().getJobID()).toString());
         }
    }
    
    public List<String> getRootJobIDs() {
        return rootJobIDs;
    }
    
    public Map<String, Map<String, String>> getPigStats() {
        return stats;
    }
    
    public long getRecordsWritten() {
        if(mode == ExecType.LOCAL)
            return getRecordsCountLocal();
        else if(mode == ExecType.MAPREDUCE)
            return getRecordsCountMR();
        else
            throw new RuntimeException("Unrecognized mode. Either MapReduce or Local mode expected.");
    }
    
    private long getRecordsCountLocal() {
        //System.out.println(getPhysicalPlan());
        //because of the nature of the parser, there will always be only one store

        for(PhysicalOperator op : php.getLeaves()) {
            return Long.parseLong(stats.get(op.toString()).get("PIG_STATS_LOCAL_OUTPUT_RECORDS"));
        }
        return 0;
    }
    
    /**
     * Returns the no. of records written by the pig script in MR mode
     * @return
     */
    private long getRecordsCountMR() {
        long records = 0;
        for (String jid : rootJobIDs) {
            Map<String, String> jobStats = stats.get(jid);
            if (jobStats == null) continue;
            String reducePlan = jobStats.get("PIG_STATS_REDUCE_PLAN");
        	if(reducePlan == null) {
        	    if (Long.parseLong(jobStats.get("PIG_STATS_MAP_OUTPUT_RECORDS"))==-1L)
                {
        	        records = -1;
                    break;
                }
        	    else
        	        records += Long.parseLong(jobStats.get("PIG_STATS_MAP_OUTPUT_RECORDS"));
        	} else {
        	    if (Long.parseLong(jobStats.get("PIG_STATS_REDUCE_OUTPUT_RECORDS"))==-1L)
                {
                    records = -1;
                    break;
                }
                else
                    records += Long.parseLong(jobStats.get("PIG_STATS_REDUCE_OUTPUT_RECORDS"));
        	}
        }
    	return records;
    }
    
    public long getBytesWritten() {
        if(mode == ExecType.LOCAL) {           
            return getLocalBytesWritten(); 
    	} else if( mode == ExecType.MAPREDUCE ) {
    	    return getMapReduceBytesWritten();
    	} else {
    		throw new RuntimeException("Unrecognized mode. Either MapReduce or Local mode expected.");
    	}
    	
    }
    
    public long getSMMSpillCount() {
        long spillCount = 0;
        for (String jid : rootJobIDs) {
            Map<String, String> jobStats = stats.get(jid);
            if (jobStats == null) continue;
            if (Long.parseLong(jobStats.get("PIG_STATS_SMM_SPILL_COUNT"))==-1L)
            {
                spillCount = -1L;
                break;
            }
            spillCount += Long.parseLong(jobStats.get("PIG_STATS_SMM_SPILL_COUNT"));
        }
        return spillCount;
    }
    
    public long getProactiveSpillCount() {
        long spillCount = 0;
        for (String jid : rootJobIDs) {
            Map<String, String> jobStats = stats.get(jid);
            if (jobStats == null) continue;
            if (Long.parseLong(jobStats.get("PIG_STATS_PROACTIVE_SPILL_COUNT"))==-1L)
            {
                spillCount = -1L;
                break;
            }
            spillCount += Long.parseLong(jobStats.get("PIG_STATS_PROACTIVE_SPILL_COUNT"));
        }
        return spillCount;
    }
    
    private long getLocalBytesWritten() {
    	for(PhysicalOperator op : php.getLeaves())
    		return Long.parseLong(stats.get(op.toString()).get("PIG_STATS_LOCAL_BYTES_WRITTEN"));
    	return 0;
    }
    
    private long getMapReduceBytesWritten() {
        long bytesWritten = 0;
        for (String jid : rootJobIDs) {
            Map<String, String> jobStats = stats.get(jid);
            if (jobStats == null) continue;
            if (Long.parseLong(jobStats.get("PIG_STATS_BYTES_WRITTEN"))==-1L)
            {
                bytesWritten = -1L;
                break;
            }
            bytesWritten += Long.parseLong(jobStats.get("PIG_STATS_BYTES_WRITTEN"));
        }
        return bytesWritten;
    }
    
}
