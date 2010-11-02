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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pig.data.DataType;
import org.apache.pig.experimental.logical.expression.LogicalExpressionPlan;
import org.apache.pig.experimental.logical.expression.LogicalExpressionVisitor;
import org.apache.pig.experimental.logical.expression.MapLookupExpression;
import org.apache.pig.experimental.logical.optimizer.AllExpressionVisitor;
import org.apache.pig.experimental.logical.relational.LOFilter;
import org.apache.pig.experimental.logical.relational.LOGenerate;
import org.apache.pig.experimental.logical.relational.LOJoin;
import org.apache.pig.experimental.logical.relational.LOLoad;
import org.apache.pig.experimental.logical.relational.LogicalRelationalOperator;
import org.apache.pig.experimental.logical.relational.LogicalSchema;
import org.apache.pig.experimental.logical.relational.LogicalSchema.LogicalFieldSchema;
import org.apache.pig.experimental.plan.DependencyOrderWalker;
import org.apache.pig.experimental.plan.Operator;
import org.apache.pig.experimental.plan.OperatorPlan;
import org.apache.pig.experimental.plan.OperatorSubPlan;
import org.apache.pig.experimental.plan.ReverseDependencyOrderWalker;

/**
 * This filter Marks every Load Operator which has a Map 
 * with MAP_MARKER_ANNOTATION. The annotation value is 
 * <code>Map<Integer,Set<String>><code> where Integer is the column number 
 * of the field and Set is the set of Keys in this field ( field is a map field only ).
 * 
 * It does this for only the top level schema in load. 
 * 
 * Algorithm:
 *  Traverse the Plan in ReverseDependency order ( ie. Sink to Source )
 *      For LogicalRelationalOperators having MapLookupExpression in their 
 *          expressionPlan collect uid and keys related to it. This is
 *          retained in the visitor
 *      For ForEach having nested LogicalPlan use the same visitor hence
 *          there is no distinction required
 *      At Sources find all the uids provided by this source and annotate this 
 *      LogicalRelationalOperator ( load ) with <code>Map<Integer,Set<String>></code>
 *      containing only the column numbers that this LogicalRelationalOperator generates
 *      
 * NOTE: This is a simple Map Pruner. If a map key is mentioned in the script
 *      then this pruner assumes you need the key. This pruner is not as optimized
 *      as column pruner ( which removes a column if it is mentioned but never used )
 *
 */
public class MapKeysPruneHelper {

    public static final String REQUIRED_MAPKEYS = "MapPruner:RequiredKeys";
    
    private OperatorPlan currentPlan;
    private OperatorSubPlan subplan;
    
    public MapKeysPruneHelper(OperatorPlan currentPlan) {
        this.currentPlan = currentPlan;
        
        if (currentPlan instanceof OperatorSubPlan) {
            subplan = new OperatorSubPlan(((OperatorSubPlan)currentPlan).getBasePlan());
        } else {
            subplan = new OperatorSubPlan(currentPlan);
        }
    }
  

    @SuppressWarnings("unchecked")
    public boolean check() throws IOException {       
        
        // First check if we have a load with a map in it or not
        List<Operator> sources = currentPlan.getSources();
        
        boolean hasMap = false;
        for( Operator source : sources ) {
            LogicalSchema schema = ((LogicalRelationalOperator)source).getSchema();
            // If any of the loads has a null schema we dont know the ramifications here
            // so we skip this optimization
            if( schema == null ) {
                return false;
            }
            if( hasMap( schema ) ) {
                hasMap = true;
            }
        }
                    
        // We dont have any map in the first level of schema
        if( !hasMap ) {
            return false;
        }
        
        
        // Now we check what keys are needed
        MapMarker marker = new MapMarker(currentPlan);
        marker.visit();
        
        // Get all Uids from Sinks
        List<Operator> sinks = currentPlan.getSinks();
        Set<Long> sinkMapUids = new HashSet<Long>();
        for( Operator sink : sinks ) {
            LogicalSchema schema = ((LogicalRelationalOperator)sink).getSchema();
            sinkMapUids.addAll( getMapUids( schema ) );
        }
        
        
        // If we have found specific keys which are needed then we return true;
        // Else if we dont have any specific keys we return false
        boolean hasAnnotation = false;
        for( Operator source : sources ) {
            Map<Integer,Set<String>> annotationValue = 
                (Map<Integer, Set<String>>) ((LogicalRelationalOperator)source).getAnnotation(REQUIRED_MAPKEYS);
            
            // Now for all full maps found in sinks we cannot prune them at source
            if( ! sinkMapUids.isEmpty() && annotationValue != null && 
                    !annotationValue.isEmpty() ) {
                Integer[] annotationKeyArray = annotationValue.keySet().toArray( new Integer[0] );
                LogicalSchema sourceSchema = ((LogicalRelationalOperator)source).getSchema();
                for( Integer col : annotationKeyArray ) {                	
                    if( sinkMapUids.contains(sourceSchema.getField(col).uid)) {
                        annotationValue.remove( col );
                    }
                }
            }
            
            if ( annotationValue != null && annotationValue.isEmpty()) {
                ((LogicalRelationalOperator)source).removeAnnotation(REQUIRED_MAPKEYS);
                annotationValue = null;
            }
            
            // Can we still prune any keys
            if( annotationValue != null ) {
                hasAnnotation = true;
                subplan.add(source);
            }
        }
        
        // If all the sinks dont have any schema, we cant to any optimization
        return hasAnnotation;
    }
    
    /**
     * This function checks if the schema has a map.
     * We dont check for a nested structure.
     * @param schema Schema to be checked
     * @return true if it has a map, else false
     * @throws NullPointerException incase Schema is null
     */
    private boolean hasMap(LogicalSchema schema ) throws NullPointerException {
        for( LogicalFieldSchema field : schema.getFields() ) {
            if( field.type == DataType.MAP ) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * This function returns a set of Uids corresponding to
     * map datatype in the first level of this schema
     * @param schema Schema having fields
     * @return
     */
    private Set<Long> getMapUids(LogicalSchema schema ) {
        Set<Long> uids = new HashSet<Long>();
        if( schema != null ) {
            for( LogicalFieldSchema field : schema.getFields() ) {
                if( field.type == DataType.MAP ) {
                    uids.add( field.uid );
                }
            }
        }
        return uids;
    }

    public OperatorPlan reportChanges() {
        return subplan;
    }

      
    /**
     * This class collects all the information required to create
     * the list of keys required for a map
     */
    static public class MapMarker extends AllExpressionVisitor {
        
        Map<Long,Set<String>> inputUids = null;

        protected MapMarker(OperatorPlan plan) {
            super(plan, new ReverseDependencyOrderWalker(plan));
            inputUids = new HashMap<Long,Set<String>>();
        }
        
        @Override
        public void visitLOLoad(LOLoad load) throws IOException {
            if( load.getSchema() != null ) {
                Map<Integer,Set<String>> annotation = new HashMap<Integer,Set<String>>();
                for( int i=0; i<load.getSchema().size(); i++) {
                    LogicalFieldSchema field = load.getSchema().getField(i);
                    if( inputUids.containsKey( field.uid ) ) {
                        annotation.put(i, inputUids.get( field.uid ) );
                    }
                }
                load.annotate(REQUIRED_MAPKEYS, annotation);
            }
        }

        @Override
        public void visitLOFilter(LOFilter filter) throws IOException {
            currentOp = filter;
            MapExprMarker v = (MapExprMarker) getVisitor(filter.getFilterPlan());
            v.visit();
            mergeUidKeys( v.inputUids );
        }
        
        @Override
        public void visitLOJoin(LOJoin join) throws IOException {
            currentOp = join;
            Collection<LogicalExpressionPlan> c = join.getExpressionPlans();
            for (LogicalExpressionPlan plan : c) {
                MapExprMarker v = (MapExprMarker) getVisitor(plan);
                v.visit();
                mergeUidKeys( v.inputUids );
            }
        }
        
        @Override
        public void visitLOGenerate(LOGenerate gen) throws IOException {
            currentOp = gen;
            Collection<LogicalExpressionPlan> plans = gen.getOutputPlans();
            for( LogicalExpressionPlan plan : plans ) {
                MapExprMarker v = (MapExprMarker) getVisitor(plan);
                v.visit();
                mergeUidKeys( v.inputUids );
            }
        }
        
        private void mergeUidKeys( Map<Long, Set<String> > inputMap ) {
            for( Map.Entry<Long, Set<String>> entry : inputMap.entrySet() ) {
                if( inputUids.containsKey(entry.getKey()) ) {
                    Set<String> mapKeySet = inputUids.get(entry.getKey());
                    mapKeySet.addAll(entry.getValue());
                } else {
                    inputUids.put(entry.getKey(), inputMap.get(entry.getKey()));
                }
            }
        }

        @Override
        protected LogicalExpressionVisitor getVisitor(LogicalExpressionPlan expr) {
            return new MapExprMarker(expr );
        }
        
        static class MapExprMarker extends LogicalExpressionVisitor {

            Map<Long,Set<String>> inputUids = null;
            
            protected MapExprMarker(OperatorPlan p) {
                super(p, new DependencyOrderWalker(p));
                inputUids = new HashMap<Long,Set<String>>();
            }

            public void visitMapLookup(MapLookupExpression op) throws IOException {
                Long uid = op.getMap().getUid();
                String key = op.getLookupKey();
                
                HashSet<String> mapKeySet = null;
                if( inputUids.containsKey(uid) ) {
                    mapKeySet = (HashSet<String>) inputUids.get(uid);                                        
                } else {
                    mapKeySet = new HashSet<String>();
                    inputUids.put(uid, mapKeySet);
                }
                mapKeySet.add(key);
            }
        }
    }
}
