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

package org.apache.pig.experimental.plan;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.pig.impl.util.Pair;

public interface OperatorPlan {
    
    /**
     * Get number of nodes in the plan.
     */
    public int size();

    /**
     * Get all operators in the plan that have no predecessors.
     * @return all operators in the plan that have no predecessors, or
     * an empty list if the plan is empty.
     */
    public List<Operator> getSources();

    /**
     * Get all operators in the plan that have no successors.
     * @return all operators in the plan that have no successors, or
     * an empty list if the plan is empty.
     */
    public List<Operator> getSinks();

    /**
     * For a given operator, get all operators immediately before it in the
     * plan.
     * @param op operator to fetch predecessors of
     * @return list of all operators immediately before op, or an empty list
     * if op is a root.
     * @throws IOException if op is not in the plan.
     */
    public List<Operator> getPredecessors(Operator op) throws IOException;
    
    /**
     * For a given operator, get all operators immediately after it.
     * @param op operator to fetch successors of
     * @return list of all operators immediately after op, or an empty list
     * if op is a leaf.
     * @throws IOException if op is not in the plan.
     */
    public List<Operator> getSuccessors(Operator op) throws IOException;

    /**
     * Add a new operator to the plan.  It will not be connected to any
     * existing operators.
     * @param op operator to add
     */
    public void add(Operator op);

    /**
     * Remove an operator from the plan.
     * @param op Operator to be removed
     * @throws IOException if the remove operation attempts to 
     * remove an operator that is still connected to other operators.
     */
    public void remove(Operator op) throws IOException;
    
    /**
     * Connect two operators in the plan, controlling which position in the
     * edge lists that the from and to edges are placed.
     * @param from Operator edge will come from
     * @param fromPos Position in the array for the from edge
     * @param to Operator edge will go to
     * @param toPos Position in the array for the to edge
     */
    public void connect(Operator from, int fromPos, Operator to, int toPos);
    
    /**
     * Connect two operators in the plan.
     * @param from Operator edge will come from
     * @param to Operator edge will go to
     */
    public void connect(Operator from, Operator to);
    
    /**
     * Disconnect two operators in the plan.
     * @param from Operator edge is coming from
     * @param to Operator edge is going to
     * @return pair of positions, indicating the position in the from and
     * to arrays.
     * @throws IOException if the two operators aren't connected.
     */
    public Pair<Integer, Integer> disconnect(Operator from, Operator to) throws IOException;


    /**
     * Get an iterator of all operators in this plan
     * @return an iterator of all operators in this plan
     */
    public Iterator<Operator> getOperators();
    
    /**
     * This is like a shallow comparison.
     * Two plans are equal if they have equivalent operators and equivalent 
     * structure.
     * @param other  object to compare
     * @return boolean if both the plans are equivalent
     */
    public boolean isEqual( OperatorPlan other );
}
