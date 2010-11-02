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
package org.apache.pig.experimental.logical.rules;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.pig.data.DataType;
import org.apache.pig.experimental.logical.expression.LogicalExpressionPlan;
import org.apache.pig.experimental.logical.expression.ProjectExpression;
import org.apache.pig.experimental.logical.relational.LOFilter;
import org.apache.pig.experimental.logical.relational.LOForEach;
import org.apache.pig.experimental.logical.relational.LOGenerate;
import org.apache.pig.experimental.logical.relational.LogicalPlan;
import org.apache.pig.experimental.logical.relational.LogicalRelationalOperator;
import org.apache.pig.experimental.logical.relational.LogicalSchema;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.OperatorSubPlan;
import org.apache.pig.experimental.plan.optimizer.Rule;
import org.apache.pig.experimental.plan.optimizer.Transformer;
import org.apache.pig.impl.util.Pair;

/**
 * This Rule moves Filter Above Foreach.
 * It checks if uid on which filter works on
 * is present in the predecessor of foreach.
 * If so it transforms it.
 */
public class FilterAboveForeach extends Rule {

    public FilterAboveForeach(String n) {
        super(n);
    }

    @Override
    protected OperatorPlan buildPattern() {
        // the pattern that this rule looks for
        // is foreach -> filter
        LogicalPlan plan = new LogicalPlan();
        LogicalRelationalOperator foreach = new LOForEach(plan);
        LogicalRelationalOperator filter = new LOFilter(plan);
        
        plan.add(foreach);
        plan.add(filter);
        plan.connect(foreach, filter);

        return plan;
    }

    @Override
    public Transformer getNewTransformer() {
        return new FilterAboveForEachTransformer();
    }
    
    public class FilterAboveForEachTransformer extends Transformer {

        LOFilter filter = null;
        LOForEach foreach = null;
        LogicalRelationalOperator forEachPred = null;
        OperatorSubPlan subPlan = null;
        
        @Override
        public boolean check(OperatorPlan matched) throws IOException {
            Iterator<Operator> iter = matched.getOperators();
            while( iter.hasNext() ) {
                Operator op = iter.next();
                if( op instanceof LOForEach ) {
                    foreach = (LOForEach)op;
                    break;
                }
            }
            
            // This would be a strange case
            if( foreach == null ) return false;
            
            iter = matched.getOperators();
            while( iter.hasNext() ) {
                Operator op = iter.next();
                if( ( op instanceof LOFilter ) ) {
                    filter = (LOFilter)op;
                    break;
                }
            }
            
            // This is for cheating, we look up more than one filter in the plan
            while( filter != null ) {
                // Get uids of Filter
                Set<Long> uids = getFilterProjectionUids(filter);


                // See if the previous operators have uids from project
                List<Operator> preds = currentPlan.getPredecessors(foreach);            
                for(int j=0; j< preds.size(); j++) {
                    LogicalRelationalOperator logRelOp = (LogicalRelationalOperator)preds.get(j);
                    if (hasAll( logRelOp, uids) ) {
                        forEachPred = (LogicalRelationalOperator) preds.get(j);
                        return true;
                    }
                }
                
                // Chances are there are filters below this filter which can be
                // moved up. So searching for those filters
                List<Operator> successors = currentPlan.getSuccessors(filter);
                if( successors != null && successors.size() > 0 && 
                        successors.get(0) instanceof LOFilter ) {
                    filter = (LOFilter)successors.get(0);
                } else {
                    filter = null;
                }
            }
            return false;            
        }
        
        /**
         * Get all uids from Projections of this FilterOperator
         * @param filter
         * @return Set of uid
         */
        private Set<Long> getFilterProjectionUids( LOFilter filter ) {
            Set<Long> uids = new HashSet<Long>();
            if( filter != null ) {
                LogicalExpressionPlan filterPlan = filter.getFilterPlan();
                Iterator<Operator> iter = filterPlan.getOperators();            
                Operator op = null;
                while( iter.hasNext() ) {
                    op = iter.next();
                    if( op instanceof ProjectExpression ) {
                        uids.add(((ProjectExpression)op).getUid() );
                    }
                }
            }
            return uids;
        }
        
        /**
         * checks if a relational operator contains all of the specified uids
         * @param op LogicalRelational operator that should contain the uid
         * @param uids Uids to check for
         * @return true if given LogicalRelationalOperator has all the given uids
         */
        private boolean hasAll(LogicalRelationalOperator op, Set<Long> uids) {                        
            Set<Long> all = getAllProjectableUids(op.getSchema());            
            return all.containsAll(uids);
        }
        
        /*
         * Projectable set of uids are uids of fields of type except a bag
         */
        private Set<Long> getAllProjectableUids( LogicalSchema schema ) {
            Set<Long> uids = new HashSet<Long>();
            
            if( schema == null ) {
                return uids;
            }
            
            for( LogicalSchema.LogicalFieldSchema field : schema.getFields() ) {
                if( field.type == DataType.TUPLE ) {
                    uids.addAll( getAllProjectableUids(field.schema ) );
                }
                if( field.type != DataType.BAG ) {
                    uids.add( field.uid );
                }
            }
            return uids;
        }
        
        @Override
        public OperatorPlan reportChanges() {            
            return subPlan;
        }

        @Override
        public void transform(OperatorPlan matched) throws IOException {
            
            List<Operator> opSet = currentPlan.getPredecessors(filter);
            if( ! ( opSet != null && opSet.size() > 0 ) ) {
                return;
            }
            Operator filterPred = opSet.get(0);
            
            opSet = currentPlan.getSuccessors(filter);
            if( ! ( opSet != null && opSet.size() > 0 ) ) {
                return;
            }
            Operator filterSuc = opSet.get(0);
            
            subPlan = new OperatorSubPlan(currentPlan);
            
            // Steps below do the following
            /*
             *          ForEachPred
             *               |
             *            ForEach         
             *               |
             *             Filter*
             *      ( These are filters
             *      which cannot be moved )
             *               |
             *           FilterPred                 
             *         ( is a Filter )
             *               |
             *             Filter
             *        ( To be moved ) 
             *               |
             *            FilterSuc
             *              
             *               |
             *               |
             *        Transforms into 
             *               |
             *              \/            
             *                      
             *            ForEachPred
             *               |
             *            Filter
             *     ( After being Moved )
             *               |
             *            ForEach
             *               |
             *             Filter*
             *       ( These are filters
             *      which cannot be moved )
             *               |
             *           FilterPred                 
             *         ( is a Filter )
             *               |
             *            FilterSuc
             *            
             *  Above plan is assuming we are modifying the filter in middle.
             *  If we are modifying the first filter after ForEach then
             *  -- * (kleene star) becomes zero
             *  -- And ForEach is FilterPred 
             */
            
            Pair<Integer, Integer> forEachPredPlaces = currentPlan.disconnect(forEachPred, foreach);
            Pair<Integer, Integer> filterPredPlaces = currentPlan.disconnect(filterPred, filter);
            Pair<Integer, Integer> filterSucPlaces = currentPlan.disconnect(filter, filterSuc);
            
            currentPlan.connect(forEachPred, forEachPredPlaces.first, filter, filterPredPlaces.second);
            currentPlan.connect(filter, filterSucPlaces.first, foreach, forEachPredPlaces.second);
            currentPlan.connect(filterPred, filterPredPlaces.first, filterSuc, filterSucPlaces.second);
            
            subPlan.add(forEachPred);
            subPlan.add(foreach);
            subPlan.add(filterPred);
            subPlan.add(filter);
            subPlan.add(filterSuc);
        }
    }

}
