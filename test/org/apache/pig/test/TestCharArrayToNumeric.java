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

package org.apache.pig.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.POCast;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.expressionOperators.POProject;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.plan.NodeIdGenerator;
import org.apache.pig.impl.plan.OperatorKey;

public class TestCharArrayToNumeric extends TestCase {

	private Double dummyDouble = null;

	private Float dummyFloat = null;

	private Long dummyLong = null;

	private Integer dummyInteger = null;

	private Double MaxDouble = Double.MIN_VALUE;

	private Double MinDouble = Double.MIN_VALUE;

	private Float MaxFloat = Float.MAX_VALUE;

	private Float MinFloat = Float.MIN_VALUE;

	private Long MaxLong = Long.MAX_VALUE;

	private Long MinLong = Long.MIN_VALUE;

	private Integer MaxInteger = Integer.MAX_VALUE;

	private Integer MinInteger = Integer.MIN_VALUE;
	
	MiniCluster cluster = MiniCluster.buildCluster();
	PigServer pig;
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
	    pig = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());
	}

	public static OperatorKey newOperatorKey() {
		long newId = NodeIdGenerator.getGenerator().getNextNodeId("scope");
		return new OperatorKey("scope", newId);
	}

	public void testCast() throws ExecException {

		POCast cast = new POCast(newOperatorKey(), -1);
		POProject proj = new POProject(newOperatorKey(), -1, 0);
		proj.setResultType(DataType.CHARARRAY);
		List<PhysicalOperator> inputs = new ArrayList<PhysicalOperator>();
		inputs.add(proj);
		cast.setInputs(inputs);

		// cast to double
		String[] items = { "12.0", "-13.2", "0.1f", "1.3e2", "zjf",
				MaxDouble.toString(), MinDouble.toString() };
		Double[] doubleExpected = { 12.0, -13.2, 0.1, 1.3e2, null, MaxDouble,
				MinDouble };
		for (int i = 0; i < items.length; ++i) {
			Tuple tuple = TupleFactory.getInstance().newTuple(1);
			tuple.set(0, items[i]);
			proj.attachInput(tuple);
			Double actual = (Double) cast.getNext(dummyDouble).result;
			if (doubleExpected[i] != null) {
				assertEquals(doubleExpected[i], actual, 1e-6);
			} else {
				assertNull(actual);
			}
		}

		// cast to float
		items = new String[] { "12.0", "-13.2", "0.1f", "1.3e2",
				MaxFloat.toString(), MinFloat.toString(), "zjf" };
		Float[] floatExpected = { 12.0f, -13.2f, 0.1f, 1.3e2f, MaxFloat,
				MinFloat, null };
		for (int i = 0; i < items.length; ++i) {
			Tuple tuple = TupleFactory.getInstance().newTuple(1);
			tuple.set(0, items[i]);
			proj.attachInput(tuple);
			Float actual = (Float) cast.getNext(dummyFloat).result;
			if (floatExpected[i] != null) {
				assertEquals(floatExpected[i], actual, 1e-6);
			} else {
				assertNull(actual);
			}
		}

		// cast to long
		items = new String[] { "1", "-1", "12.2", "12.8", MaxLong.toString(),
				MinLong.toString(), "df1.2" };
		Long[] longExpected = { 1L, -1L, 12L, 12L, MaxLong, MinLong, null };
		for (int i = 0; i < items.length; ++i) {
			Tuple tuple = TupleFactory.getInstance().newTuple(1);
			tuple.set(0, items[i]);
			proj.attachInput(tuple);
			Long actual = (Long) cast.getNext(dummyLong).result;
			if (longExpected[i] != null) {
				assertEquals(longExpected[i], actual);
			} else {
				assertNull(actual);
			}
		}

		// cast to int
		items = new String[] { "1", "-1", "12.2", "12.8",
				MaxInteger.toString(), MinInteger.toString(), "ff4332" };
		Integer[] intExpected = { 1, -1, 12, 12, MaxInteger, MinInteger, null };
		for (int i = 0; i < items.length; ++i) {
			Tuple tuple = TupleFactory.getInstance().newTuple(1);
			tuple.set(0, items[i]);
			proj.attachInput(tuple);
			Integer actual = (Integer) cast.getNext(dummyInteger).result;
			if (intExpected[i] != null) {
				assertEquals(intExpected[i], actual);
			} else {
				assertNull(actual);
			}
		}
	}


	public void testCharArray2FloatAndDoubleScript() throws IOException {

		// create a input file with format (key,value)
		int size = 100;
		String[] numbers = new String[size + 1];
		Random rand = new Random();
		Map<Integer, Double> map = new HashMap<Integer, Double>();

		try {
			for (int i = 0; i < numbers.length; ++i) {
				int key = i;
				double value = rand.nextDouble() * 100;
				numbers[i] = (key + "\t" + value);
				map.put(key, value);
			}
			// append a null at the last line, to test string which can not been
			// cast
			numbers[numbers.length - 1] = (numbers.length + "\t" + "null");
			map.put(numbers.length, null);
		    Util.createInputFile(cluster, "pig_jira_893-input1.txt", numbers);
			byte[] numericTypes = new byte[] { DataType.DOUBLE, DataType.FLOAT, };
			for (byte type : numericTypes) {
				pig.registerQuery("A = Load 'pig_jira_893-input1.txt' AS " +
						"(key:int,value:chararray);");
				pig.registerQuery("B = FOREACH A GENERATE key,("
						+ DataType.findTypeName(type) + ")value;");
				Iterator<Tuple> iter = pig.openIterator("B");
				while (iter.hasNext()) {
					Tuple tuple = iter.next();
					Integer key = (Integer) tuple.get(0);
					String value = null;
					if (tuple.get(1) != null) {
						value = tuple.get(1).toString();
					}

					if (type == DataType.DOUBLE) {
						Double expected = map.get(key);
						if (value != null) {
							assertEquals(expected, Double.parseDouble(value));
						} else {
							assertEquals(expected, null);
						}

					}
					if (type == DataType.FLOAT) {
						Float expected = null;
						if (map.get(key) != null) {
							expected = map.get(key).floatValue();
						}
						if (value != null) {
							assertEquals(expected, Float.parseFloat(value));
						} else {
							assertEquals(expected, null);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		} finally {
			Util.deleteFile(cluster, "pig_jira_893-input1.txt");
		}
	}

	public void testCharArrayToIntAndLongScript() throws IOException {

		// create a input file with format (key,value)
		int size = 100;
		String[] numbers = new String[size + 1];
		Random rand = new Random();
		Map<Integer, Long> map = new HashMap<Integer, Long>();
		File inputFile = null;

		try {
			for (int i = 0; i < numbers.length; ++i) {
				int key = i;
				long value = rand.nextInt(100);
				numbers[i] = (key + "\t" + value);
				map.put(key, value);
			}
			// append a null at the last line, to test string which can not been
			// cast
			numbers[numbers.length - 1] = (numbers.length + "\t" + "null");
			map.put(numbers.length, null);
            Util.createInputFile(cluster, "pig_jira_893-input2.txt", numbers);
			byte[] numericTypes = new byte[] { DataType.INTEGER, DataType.LONG, };
			for (byte type : numericTypes) {
				pig.registerQuery("A = Load 'pig_jira_893-input2.txt' AS " +
						"(key:int,value:chararray);");
				pig.registerQuery("B = FOREACH A GENERATE key,("
						+ DataType.findTypeName(type) + ")value;");
				Iterator<Tuple> iter = pig.openIterator("B");
				while (iter.hasNext()) {
					Tuple tuple = iter.next();
					Integer key = (Integer) tuple.get(0);
					String value = null;
					if (tuple.get(1) != null) {
						value = tuple.get(1).toString();
					}

					if (type == DataType.LONG) {
						Long expected = map.get(key);
						if (value != null) {
							Long actual = Long.parseLong(value);
							assertEquals(expected, actual);
						} else {
							assertEquals(expected, null);
						}
					}
					if (type == DataType.INTEGER) {
						Integer expected = null;
						if (map.get(key) != null) {
							expected = map.get(key).intValue();
						}
						if (value != null) {
							Integer actual = Integer.parseInt(value);
							assertEquals(expected, actual);
						} else {
							assertEquals(expected, null);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		} finally {
			Util.deleteFile(cluster, "pig_jira_893-input2.txt");
		}
	}
}
