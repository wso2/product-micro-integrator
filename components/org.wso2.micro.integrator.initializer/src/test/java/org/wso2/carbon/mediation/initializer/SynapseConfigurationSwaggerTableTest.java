/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.mediation.initializer;

import org.apache.synapse.api.API;
import org.apache.synapse.config.SynapseConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link SynapseConfiguration#validateSwaggerTable()}.
 *
 * The bug (Issue #3643) was that when a CApp deployment failed partway through,
 * the swagger definition added during {@code searchArtifacts()} was left in
 * {@code swaggerTable} even after the API was rolled back from {@code apiTable}.
 * On re-deployment {@code addSwaggerDefinition()} threw a
 * "Duplicate swagger definition" {@link org.apache.synapse.SynapseException},
 * requiring a server restart.
 *
 * The fix introduced {@code validateSwaggerTable()}, which removes every swagger
 * entry whose corresponding API key is absent from {@code apiTable}. These tests
 * verify that method in isolation.
 */
public class SynapseConfigurationSwaggerTableTest {

    private SynapseConfiguration synapseConfig;

    @Before
    public void setUp() {
        synapseConfig = new SynapseConfiguration();
    }

    /**
     * An orphaned swagger entry — one with no matching API in apiTable — must be
     * removed by validateSwaggerTable().  This is the core bug scenario: the faulty
     * CApp added a swagger then the API deploy failed, leaving the swagger stranded.
     */
    @Test
    public void testValidateSwaggerTable_removesOrphanedSwagger() {
        synapseConfig.addSwaggerDefinition("OrphanAPI", "{swagger: '2.0'}");

        synapseConfig.validateSwaggerTable();

        assertNull("Orphaned swagger entry should have been removed from swaggerTable",
                synapseConfig.getSwaggerOfTheAPI("OrphanAPI"));
    }

    /**
     * A swagger entry whose API key is present in apiTable must NOT be removed.
     * This verifies the method does not over-aggressively clean up valid deployments.
     */
    @Test
    public void testValidateSwaggerTable_retainsSwaggerWithMatchingAPI() throws Exception {
        synapseConfig.addSwaggerDefinition("ValidAPI", "{swagger: '2.0'}");
        insertIntoApiTable(synapseConfig, "ValidAPI", "/valid");

        synapseConfig.validateSwaggerTable();

        assertNotNull("Swagger with a matching API should be retained in swaggerTable",
                synapseConfig.getSwaggerOfTheAPI("ValidAPI"));
    }

    /**
     * Multiple orphaned swagger entries must all be removed in a single
     * validateSwaggerTable() call.  This exercises the Iterator-based removal
     * loop (commit 047c0c4dd) that replaced a simple for-each to prevent
     * ConcurrentModificationException when more than one swagger is removed.
     */
    @Test
    public void testValidateSwaggerTable_removesMultipleOrphanedSwaggers() {
        synapseConfig.addSwaggerDefinition("OrphanAPI1", "{swagger: '2.0'}");
        synapseConfig.addSwaggerDefinition("OrphanAPI2", "{swagger: '2.0'}");
        synapseConfig.addSwaggerDefinition("OrphanAPI3", "{swagger: '2.0'}");

        // No corresponding APIs added — all three are orphans.
        synapseConfig.validateSwaggerTable();

        assertNull("OrphanAPI1 swagger should be removed",
                synapseConfig.getSwaggerOfTheAPI("OrphanAPI1"));
        assertNull("OrphanAPI2 swagger should be removed",
                synapseConfig.getSwaggerOfTheAPI("OrphanAPI2"));
        assertNull("OrphanAPI3 swagger should be removed",
                synapseConfig.getSwaggerOfTheAPI("OrphanAPI3"));
    }

    /**
     * When the swaggerTable contains a mix of entries — some with a matching API
     * and some without — only the orphaned ones are removed while the valid ones
     * are kept intact.
     */
    @Test
    public void testValidateSwaggerTable_retainsPresentAndRemovesAbsent() throws Exception {
        synapseConfig.addSwaggerDefinition("PresentAPI", "{swagger: '2.0', info: {title: 'Present'}}");
        synapseConfig.addSwaggerDefinition("AbsentAPI", "{swagger: '2.0', info: {title: 'Absent'}}");

        // Only "PresentAPI" has a backing API entry.
        insertIntoApiTable(synapseConfig, "PresentAPI", "/present");

        synapseConfig.validateSwaggerTable();

        assertNotNull("Swagger for PresentAPI (has matching API) should be retained",
                synapseConfig.getSwaggerOfTheAPI("PresentAPI"));
        assertNull("Swagger for AbsentAPI (no matching API) should be removed",
                synapseConfig.getSwaggerOfTheAPI("AbsentAPI"));
    }

    /**
     * Calling validateSwaggerTable() on a completely empty SynapseConfiguration
     * must not throw any exception.
     */
    @Test
    public void testValidateSwaggerTable_noExceptionOnEmptyTables() {
        // Neither swaggerTable nor apiTable has any entries — must complete silently.
        synapseConfig.validateSwaggerTable();
    }

    /**
     * After a full faulty-CApp redeployment cycle (the Issue #3643 scenario):
     * 1. Swagger added, then API deploy fails → swagger orphaned.
     * 2. validateSwaggerTable() cleans up the orphan.
     * 3. A fresh addSwaggerDefinition() for the same name succeeds without throwing
     *    "Duplicate swagger definition".
     */
    @Test
    public void testFaultyCAppRedeployScenario_noExceptionOnSecondAddSwagger() {
        // Step 1: First (faulty) deploy adds swagger but leaves no matching API.
        synapseConfig.addSwaggerDefinition("PetstoreAPI", "{swagger: '2.0', title: 'Petstore'}");

        // Step 2: handleDeployException calls validateSwaggerTable to clean up.
        synapseConfig.validateSwaggerTable();

        assertNull("Orphaned swagger must be cleared before re-deploy",
                synapseConfig.getSwaggerOfTheAPI("PetstoreAPI"));

        // Step 3: Second (corrected) deploy — must not throw SynapseException.
        synapseConfig.addSwaggerDefinition("PetstoreAPI", "{swagger: '2.0', title: 'Petstore'}");

        assertNotNull("Swagger from corrected re-deploy should be present",
                synapseConfig.getSwaggerOfTheAPI("PetstoreAPI"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Inserts an API entry directly into the private {@code apiTable} via reflection.
     * This deliberately bypasses {@link SynapseConfiguration#addAPI(String, API)} to
     * keep the test focused on {@code validateSwaggerTable()} behaviour and avoid
     * observer notifications and table-reconstruction side effects that are irrelevant
     * to this test.
     */
    private static void insertIntoApiTable(SynapseConfiguration config,
                                           String apiName,
                                           String context) throws Exception {
        Field apiTableField = SynapseConfiguration.class.getDeclaredField("apiTable");
        apiTableField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> apiTable = (Map<String, Object>) apiTableField.get(config);
        apiTable.put(apiName, new API(apiName, context));
    }
}
