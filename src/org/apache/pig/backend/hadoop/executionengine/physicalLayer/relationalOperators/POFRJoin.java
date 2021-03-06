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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.pig.ExecType;
import org.apache.pig.PigException;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigMapReduce;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.ConstantExpression;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhyPlanVisitor;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.util.MapRedUtil;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataType;
import org.apache.pig.data.NonSpillableDataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.io.FileSpec;
import org.apache.pig.impl.plan.NodeIdGenerator;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.PlanException;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.util.ObjectSerializer;

/**
 * The operator models the join keys using the Local Rearrange operators which
 * are configured with the plan specified by the user. It also sets up one
 * Hashtable per replicated input which maps the Key(k) stored as a Tuple to a
 * DataBag which holds all the values in the input having the same key(k) The
 * getNext() reads an input from its predecessor and separates them into key &
 * value. It configures a foreach operator with the databags obtained from each
 * Hashtable for the key and also with the value for the fragment input. It then
 * returns tuples returned by this foreach operator.
 */

// We intentionally skip type checking in backend for performance reasons
@SuppressWarnings("unchecked")
public class POFRJoin extends PhysicalOperator {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    static private Log log = LogFactory.getLog(POFRJoin.class);          
    // The number in the input list which denotes the fragmented input
    private int fragment;
    // There can be n inputs each being a List<PhysicalPlan>
    // Ex. join A by ($0+$1,$0-$1), B by ($0*$1,$0/$1);
    private List<List<PhysicalPlan>> phyPlanLists;
    // The key type for each Local Rearrange operator
    private List<List<Byte>> keyTypes;
    // The Local Rearrange operators modeling the join key
    private POLocalRearrange[] LRs;
    // The set of files that represent the replicated inputs
    private FileSpec[] replFiles;
    // Used to configure the foreach operator
    private ConstantExpression[] constExps;
    // Used to produce the cross product of various bags
    private POForEach fe;
    // The array of Hashtables one per replicated input. replicates[fragment] =
    // null
    // fragment is the input which is fragmented and not replicated.
    private Map<Tuple, List<Tuple>> replicates[];
    // varaible which denotes whether we are returning tuples from the foreach
    // operator
    private boolean processingPlan;
    // A dummy tuple
    private Tuple dumTup = TupleFactory.getInstance().newTuple(1);
    // An instance of tuple factory
    private transient TupleFactory mTupleFactory;
    private boolean setUp;
    // A Boolean variable which denotes if this is a LeftOuter Join or an Inner
    // Join
    private boolean isLeftOuterJoin;

    // This list contains nullTuples according to schema of various inputs 
    private DataBag nullBag;

    public POFRJoin(OperatorKey k, int rp, List<PhysicalOperator> inp,
            List<List<PhysicalPlan>> ppLists, List<List<Byte>> keyTypes,
            FileSpec[] replFiles, int fragment, boolean isLeftOuter,
            Tuple nullTuple)
            throws ExecException {
        super(k, rp, inp);

        phyPlanLists = ppLists;
        this.fragment = fragment;
        this.keyTypes = keyTypes;
        this.replFiles = replFiles;
        replicates = new Map[ppLists.size()];
        LRs = new POLocalRearrange[ppLists.size()];
        constExps = new ConstantExpression[ppLists.size()];
        createJoinPlans(k);
        processingPlan = false;
        mTupleFactory = TupleFactory.getInstance();
        List<Tuple> tupList = new ArrayList<Tuple>();
        tupList.add(nullTuple);
        nullBag = new NonSpillableDataBag(tupList);
        this.isLeftOuterJoin = isLeftOuter;
    }

    public List<List<PhysicalPlan>> getJoinPlans() {
        return phyPlanLists;
    }

    private OperatorKey genKey(OperatorKey old) {
        return new OperatorKey(old.scope, NodeIdGenerator.getGenerator()
                .getNextNodeId(old.scope));
    }

    /**
     * Configures the Local Rearrange operators & the foreach operator
     * 
     * @param old
     * @throws ExecException
     */
    private void createJoinPlans(OperatorKey old) throws ExecException {
        List<PhysicalPlan> fePlans = new ArrayList<PhysicalPlan>();
        List<Boolean> flatList = new ArrayList<Boolean>();

        int i = -1;
        for (List<PhysicalPlan> ppLst : phyPlanLists) {
            ++i;
            POLocalRearrange lr = new POLocalRearrange(genKey(old));
            lr.setIndex(i);
            lr.setResultType(DataType.TUPLE);
            lr.setKeyType(keyTypes.get(i).size() > 1 ? DataType.TUPLE
                    : keyTypes.get(i).get(0));
            try {
                lr.setPlans(ppLst);
            } catch (PlanException pe) {
                int errCode = 2071;
                String msg = "Problem with setting up local rearrange's plans.";
                throw new ExecException(msg, errCode, PigException.BUG, pe);
            }
            LRs[i] = lr;
            ConstantExpression ce = new ConstantExpression(genKey(old));
            ce.setResultType((i == fragment) ? DataType.TUPLE : DataType.BAG);
            constExps[i] = ce;
            PhysicalPlan pp = new PhysicalPlan();
            pp.add(ce);
            fePlans.add(pp);
            flatList.add(true);
        }
        // The ForEach operator here is used for generating a Cross-Product
        // It is given a set of constant expressions with
        // Tuple,(Bag|Tuple),(...)
        // It does a cross product on that and produces output.
        fe = new POForEach(genKey(old), -1, fePlans, flatList);
    }

    @Override
    public void visit(PhyPlanVisitor v) throws VisitorException {
        v.visitFRJoin(this);
    }

    @Override
    public String name() {
        return "FRJoin[" + DataType.findTypeName(resultType) + "]" + " - "
                + mKey.toString();
    }

    @Override
    public boolean supportsMultipleInputs() {
        return true;
    }

    @Override
    public boolean supportsMultipleOutputs() {
        return false;
    }

    @Override
    public Result getNext(Tuple t) throws ExecException {
        Result res = null;
        Result inp = null;
        if (!setUp) {
            setUpHashMap();
            setUp = true;
        }
        if (processingPlan) {
            // Return tuples from the for each operator
            // Assumes that it is configured appropriately with
            // the bags for the current key.
            while (true) {
                res = fe.getNext(dummyTuple);

                if (res.returnStatus == POStatus.STATUS_OK) {
                    return res;
                }
                if (res.returnStatus == POStatus.STATUS_EOP) {
                    // We have completed all cross-products now its time to move
                    // to next tuple of left side
                    processingPlan = false;                    
                    break;
                }
                if (res.returnStatus == POStatus.STATUS_ERR) {
                    return res;
                }
                if (res.returnStatus == POStatus.STATUS_NULL) {
                    continue;
                }
            }
        }
        while (true) {
            // Process the current input
            inp = processInput();
            if (inp.returnStatus == POStatus.STATUS_EOP
                    || inp.returnStatus == POStatus.STATUS_ERR)
                return inp;
            if (inp.returnStatus == POStatus.STATUS_NULL) {
                continue;
            }

            // Separate Key & Value using the fragment's LR operator
            POLocalRearrange lr = LRs[fragment];
            lr.attachInput((Tuple) inp.result);
            Result lrOut = lr.getNext(dummyTuple);
            if (lrOut.returnStatus != POStatus.STATUS_OK) {
                log
                        .error("LocalRearrange isn't configured right or is not working");
                return new Result();
            }
            Tuple lrOutTuple = (Tuple) lrOut.result;
            Tuple key = TupleFactory.getInstance().newTuple(1);
            key.set(0, lrOutTuple.get(1));
            Tuple value = getValueTuple(lr, lrOutTuple);

            // Configure the for each operator with the relevant bags
            int i = -1;
            boolean noMatch = false;
            for (ConstantExpression ce : constExps) {
                ++i;
                if (i == fragment) {
                    // We set the first CE as the tuple from fragmented Left
                    ce.setValue(value);
                    continue;
                }
                Map<Tuple, List<Tuple>> replicate = replicates[i];
                if (!replicate.containsKey(key)) {
                    if (isLeftOuterJoin) {
                        ce.setValue(nullBag);
                    }
                    noMatch = true;
                    break;
                }
                ce.setValue(new NonSpillableDataBag(replicate.get(key)));
            }

            // If this is not LeftOuter Join and there was no match we
            // skip the processing of this left tuple and move ahead
            if (!isLeftOuterJoin && noMatch)
                continue;
            fe.attachInput(dumTup);
            processingPlan = true;

            // We are all set, we call getNext (this function) which will call
            // getNext on ForEach
            // And that will return one tuple of Cross-Product between set
            // constant Expressions
            // All subsequent calls ( by parent ) to this function will return
            // next tuple of crossproduct
            Result gn = getNext(dummyTuple);

            return gn;
        }
    }

    /**
     * Builds the HashMaps by reading each replicated input from the DFS using a
     * Load operator
     * 
     * @throws ExecException
     */
    private void setUpHashMap() throws ExecException {
        int i = -1;
        long time1 = System.currentTimeMillis();
        for (FileSpec replFile : replFiles) {
            ++i;

            if (i == fragment) {
                replicates[i] = null;
                continue;
            }

            POLoad ld = new POLoad(new OperatorKey("Repl File Loader", 1L),
                    replFile);
            
            Properties props = new Properties();                                          
            props.setProperty(MapRedUtil.FILE_SYSTEM_NAME, "file:///");
                         
            PigContext pc = new PigContext(ExecType.LOCAL, props);   
            ld.setPc(pc);
            // We use LocalRearrange Operator to seperate Key and Values
            // eg. ( a, b, c ) would generate a, ( a, b, c )
            // And we use 'a' as the key to the HashMap
            // The rest '( a, b, c )' is added to HashMap as value
            // We could have manually done this, but LocalRearrange does the
            // same thing, so utilizing its functionality
            POLocalRearrange lr = LRs[i];
            lr.setInputs(Arrays.asList((PhysicalOperator) ld));
            Map<Tuple, List<Tuple>> replicate = new HashMap<Tuple, List<Tuple>>(
                    1000);
            log.debug("Completed setup. Trying to build replication hash table");
            int cnt = 0;
            for (Result res = lr.getNext(dummyTuple);res.returnStatus != POStatus.STATUS_EOP;res = lr.getNext(dummyTuple)) {
                ++cnt;
                if (reporter != null)
                    reporter.progress();               
                Tuple tuple = (Tuple) res.result;
                Tuple key = mTupleFactory.newTuple(1);
                key.set(0, tuple.get(1));
                Tuple value = getValueTuple(lr, tuple);
                if (!replicate.containsKey(key))
                    replicate.put(key, new ArrayList<Tuple>());
                replicate.get(key).add(value);
            }
            replicates[i] = replicate;

        }
        long time2 = System.currentTimeMillis();
        log.debug("Hash Table built. Time taken: " + (time2 - time1));
    }

    private void readObject(ObjectInputStream is) throws IOException,
            ClassNotFoundException, ExecException {
        is.defaultReadObject();
        mTupleFactory = TupleFactory.getInstance();
        // setUpHashTable();
    }

    /*
     * Extracts the value tuple from the LR operator's output tuple
     */
    private Tuple getValueTuple(POLocalRearrange lr, Tuple tuple)
            throws ExecException {
        Tuple val = (Tuple) tuple.get(2);
        Tuple retTup = null;
        boolean isProjectStar = lr.isProjectStar();
        Map<Integer, Integer> keyLookup = lr.getProjectedColsMap();
        int keyLookupSize = keyLookup.size();
        Object key = tuple.get(1);
        boolean isKeyTuple = lr.isKeyTuple();
        Tuple keyAsTuple = isKeyTuple ? (Tuple) tuple.get(1) : null;
        if (keyLookupSize > 0) {

            // we have some fields of the "value" in the
            // "key".
            retTup = mTupleFactory.newTuple();
            int finalValueSize = keyLookupSize + val.size();
            int valIndex = 0; // an index for accessing elements from
            // the value (val) that we have currently
            for (int i = 0; i < finalValueSize; i++) {
                Integer keyIndex = keyLookup.get(i);
                if (keyIndex == null) {
                    // the field for this index is not in the
                    // key - so just take it from the "value"
                    // we were handed
                    retTup.append(val.get(valIndex));
                    valIndex++;
                } else {
                    // the field for this index is in the key
                    if (isKeyTuple) {
                        // the key is a tuple, extract the
                        // field out of the tuple
                        retTup.append(keyAsTuple.get(keyIndex));
                    } else {
                        retTup.append(key);
                    }
                }
            }

        } else if (isProjectStar) {

            // the whole "value" is present in the "key"
            retTup = mTupleFactory.newTuple(keyAsTuple.getAll());

        } else {

            // there is no field of the "value" in the
            // "key" - so just make a copy of what we got
            // as the "value"
            retTup = mTupleFactory.newTuple(val.getAll());

        }
        return retTup;
    }

    public int getFragment() {
        return fragment;
    }

    public void setFragment(int fragment) {
        this.fragment = fragment;
    }

    public FileSpec[] getReplFiles() {
        return replFiles;
    }

    public void setReplFiles(FileSpec[] replFiles) {
        this.replFiles = replFiles;
    }
}
