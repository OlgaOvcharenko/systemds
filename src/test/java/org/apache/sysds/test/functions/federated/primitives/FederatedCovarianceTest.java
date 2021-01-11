/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysds.test.functions.federated.primitives;

import java.util.Arrays;
import java.util.Collection;

import org.apache.sysds.api.DMLScript;
import org.apache.sysds.common.Types.ExecMode;
import org.apache.sysds.runtime.meta.MatrixCharacteristics;
import org.apache.sysds.runtime.util.HDFSTool;
import org.apache.sysds.test.AutomatedTestBase;
import org.apache.sysds.test.TestConfiguration;
import org.apache.sysds.test.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(value = Parameterized.class)
@net.jcip.annotations.NotThreadSafe
public class FederatedCovarianceTest extends AutomatedTestBase {

	private final static String TEST_NAME = "FederatedCovarianceTest";

	private final static String TEST_DIR = "functions/federated/";
	private static final String TEST_CLASS_DIR = TEST_DIR + FederatedCovarianceTest.class.getSimpleName() + "/";

	private final static int blocksize = 1024;
	@Parameterized.Parameter()
	public int rows;
	@Parameterized.Parameter(1)
	public int cols;

	@Parameterized.Parameter(2)
	public boolean rowPartitioned;

	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
			{20, 1, true},
		});
	}

	@Override
	public void setUp() {
		TestUtils.clearAssertionInformation();
		addTestConfiguration(TEST_NAME, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME, new String[] {"S.scalar"}));
	}

	@Test
	public void testCovCP() { runCovTest(ExecMode.SINGLE_NODE); }

	private void runCovTest(ExecMode execMode) {
		boolean sparkConfigOld = DMLScript.USE_LOCAL_SPARK_CONFIG;
		ExecMode platformOld = rtplatform;

		if(rtplatform == ExecMode.SPARK)
			DMLScript.USE_LOCAL_SPARK_CONFIG = true;

		getAndLoadTestConfiguration(TEST_NAME);
		String HOME = SCRIPT_DIR + TEST_DIR;

		// write input matrices
		int r = rows;
		int c = cols / 4;
		if(rowPartitioned) {
			r = rows / 4;
			c = cols;
		}


		// empty script name because we don't execute any script, just start the worker
		fullDMLScriptName = "";


		double[][] X1 = getRandomMatrix(r, c, 1, 5, 1, 3);
		double[][] X2 = getRandomMatrix(r, c, 1, 5, 1, 7);
		double[][] X3 = getRandomMatrix(r, c, 1, 5, 1, 8);
		double[][] X4 = getRandomMatrix(r, c, 1, 5, 1, 9);

		double[][] Y = getRandomMatrix(rows, c, 1, 5, 1, 3);

		//		double[][] Y1 = getRandomMatrix(r, c, 1, 5, 1, 3);
		//		double[][] Y2 = getRandomMatrix(r, c, 1, 5, 1, 7);
		//		double[][] Y3 = getRandomMatrix(r, c, 1, 5, 1, 8);
		//		double[][] Y4 = getRandomMatrix(r, c, 1, 5, 1, 9);

		MatrixCharacteristics mc = new MatrixCharacteristics(r, c, blocksize, r * c);
		writeInputMatrixWithMTD("X1", X1, false, mc);
		writeInputMatrixWithMTD("X2", X2, false, mc);
		writeInputMatrixWithMTD("X3", X3, false, mc);
		writeInputMatrixWithMTD("X4", X4, false, mc);

		writeInputMatrixWithMTD("Y", Y, false, new MatrixCharacteristics(rows, cols, blocksize, r*c));
		//		writeInputMatrixWithMTD("Y1", Y1, false, mc);
		//		writeInputMatrixWithMTD("Y2", Y2, false, mc);
		//		writeInputMatrixWithMTD("Y3", Y3, false, mc);
		//		writeInputMatrixWithMTD("Y4", Y4, false, mc);

		// empty script name because we don't execute any script, just start the worker
		fullDMLScriptName = "";
		int port1 = getRandomAvailablePort();
		int port2 = getRandomAvailablePort();
		int port3 = getRandomAvailablePort();
		int port4 = getRandomAvailablePort();
		Thread t1 = startLocalFedWorkerThread(port1, FED_WORKER_WAIT_S);
		Thread t2 = startLocalFedWorkerThread(port2, FED_WORKER_WAIT_S);
		Thread t3 = startLocalFedWorkerThread(port3, FED_WORKER_WAIT_S);
		Thread t4 = startLocalFedWorkerThread(port4);

		rtplatform = execMode;
		if(rtplatform == ExecMode.SPARK) {
			System.out.println(7);
			DMLScript.USE_LOCAL_SPARK_CONFIG = true;
		}
		TestConfiguration config = availableTestConfigurations.get(TEST_NAME);
		loadTestConfiguration(config);

		// Run reference dml script with normal matrix
		fullDMLScriptName = HOME + TEST_NAME + "Reference.dml";
		programArgs = new String[] {"-stats", "100", "-args", input("X1"), input("X2"), input("X3"), input("X4"),
			input("Y"),
			Boolean.toString(rowPartitioned).toUpperCase(), expected("S")};

		runTest(null);

		fullDMLScriptName = HOME + TEST_NAME + ".dml";
		programArgs = new String[] {"-stats", "100", "-nvargs",
			"in_X1=" + TestUtils.federatedAddress(port1, input("X1")),
			"in_X2=" + TestUtils.federatedAddress(port2, input("X2")),
			"in_X3=" + TestUtils.federatedAddress(port3, input("X3")),
			"in_X4=" + TestUtils.federatedAddress(port4, input("X4")),
			"Y=" + input("Y"),
			"rows=" + rows, "cols=" + cols,
			"rP=" + Boolean.toString(rowPartitioned).toUpperCase(), "out_S=" + output("S")};

		runTest(null);

		// compare via files
		compareResults(1e-9);
		Assert.assertTrue(heavyHittersContainsString("fed_cov"));

		TestUtils.shutdownThreads(t1, t2, t3, t4);
		rtplatform = platformOld;
		DMLScript.USE_LOCAL_SPARK_CONFIG = sparkConfigOld;
	}
}

