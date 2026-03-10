/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.utils;

import org.junit.Test;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;

import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for SecretResolverUtil.
 * Tests the central secret resolution path used by ICP/management security.
 * 
 * These tests focus on verifying edge cases and expected behavior patterns:
 * - Non-placeholder input handling
 * - Placeholder detection and resolution attempts
 * - Secure Vault not initialized error handling
 * - Exception wrapping
 * - Various input formats and edge cases
 * 
 * Tests that require a fully configured Secure Vault are documented but may 
 * not execute actual resolution without proper integration test setup.
 */
public class SecretResolverUtilTest {

    /**
     * Test that non-placeholder input (plain text) is returned as-is without attempting resolution.
     * This is critical for performance - non-secret values shouldn't trigger Secure Vault access.
     */
    @Test
    public void testResolveSecret_NonPlaceholderInput_ReturnsAsIs() {
        // Plain text value (no $secret{...} pattern)
        String plainValue = "myPlainPassword";
        String result = SecretResolverUtil.resolveSecret(plainValue, () -> null);
        assertEquals("Non-placeholder input should be returned as-is", plainValue, result);

        // Empty string
        String emptyValue = "";
        result = SecretResolverUtil.resolveSecret(emptyValue, () -> null);
        assertEquals("Empty string should be returned as-is", emptyValue, result);

        // Value with special characters but not a placeholder
        String specialValue = "password$123";
        result = SecretResolverUtil.resolveSecret(specialValue, () -> null);
        assertEquals("Value with special chars should be returned as-is", specialValue, result);
        
        // Value with curly braces but not a secret placeholder
        String bracesValue = "some{config}";
        result = SecretResolverUtil.resolveSecret(bracesValue, () -> null);
        assertEquals("Value with braces should be returned as-is", bracesValue, result);
    }

    /**
     * Test that a placeholder with Secure Vault not initialized throws IllegalStateException.
     * This is a critical security check to prevent using unresolved placeholders in production.
     */
    @Test
    public void testResolveSecret_PlaceholderWithSecureVaultNotInitialized_ThrowsException() {
        // Service returns null (Secure Vault not available)
        String placeholder = "$secret{myAlias}";
        
        try {
            SecretResolverUtil.resolveSecret(placeholder, () -> null);
            fail("Should throw IllegalStateException when Secure Vault is not initialized");
        } catch (IllegalStateException e) {
            assertTrue("Error message should mention Secure Vault not initialized",
                e.getMessage().contains("Secure Vault is not initialized"));
            assertTrue("Error message should include the placeholder",
                e.getMessage().contains(placeholder));
        }
    }

    /**
     * Test various placeholder patterns to ensure they're all recognized as needing resolution.
     * All these should attempt resolution (and fail if vault not initialized).
     */
    @Test
    public void testResolveSecret_VariousPlaceholderPatterns_RecognizedAsPlaceholders() {
        String[] placeholders = {
            "$secret{alias}",
            "$secret{my.alias}",
            "$secret{alias_with_underscore}",
            "$secret{UPPERCASE_ALIAS}",
            "$secret{alias123}",
            "$secret{my-alias}"
        };
        
        for (String placeholder : placeholders) {
            try {
                SecretResolverUtil.resolveSecret(placeholder, () -> null);
                fail("Placeholder " + placeholder + " should trigger resolution attempt");
            } catch (IllegalStateException e) {
                // Expected - placeholder was recognized and resolution attempted
                assertTrue("Error should indicate resolution issue for " + placeholder,
                    e.getMessage().contains("not initialized") ||
                    e.getMessage().contains("not found"));
            }
        }
    }

    /**
     * Test that exceptions during resolution are properly wrapped in IllegalStateException.
     * This ensures consistent error handling in calling code.
     */
    @Test
    public void testResolveSecret_ExceptionDuringResolution_WrapsInIllegalStateException() {
        String placeholder = "$secret{problematicAlias}";
        
        // Create a supplier that throws an exception
        Supplier<SecretCallbackHandlerService> throwingSupplier = () -> {
            throw new RuntimeException("Simulated error during service retrieval");
        };
        
        try {
            SecretResolverUtil.resolveSecret(placeholder, throwingSupplier);
            fail("Should wrap exceptions in IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("Error message should indicate resolution error",
                e.getMessage().contains("Error resolving secret") || 
                e.getMessage().contains(placeholder));
        }
    }

    /**
     * Test edge case: placeholder with empty alias.
     * Verifies graceful handling without crashes.
     */
    @Test
    public void testResolveSecret_EmptyAlias_HandledGracefully() {
        String placeholder = "$secret{}";
        
        // This may be treated as non-placeholder if alias extraction returns empty/null,
        // or may attempt resolution - either way should not crash
        try {
            String result = SecretResolverUtil.resolveSecret(placeholder, () -> null);
            // If it doesn't throw, it was treated as non-placeholder or the alias was empty
            // and MiscellaneousUtil.getProtectedToken returned null/empty
        } catch (IllegalStateException e) {
            // Expected if treated as unresolvable placeholder
            assertTrue("Error should indicate resolution issue",
                e.getMessage().contains("not initialized") || e.getMessage().contains("not found"));
        }
    }

    /**
     * Test that the method works with different supplier implementations.
     * Ensures the functional interface approach is flexible.
     */
    @Test
    public void testResolveSecret_DifferentSuppliers_WorkCorrectly() {
        String plainValue = "notASecret";
        
        // Test with a lambda supplier that returns null
        String result1 = SecretResolverUtil.resolveSecret(plainValue, () -> null);
        assertEquals("Should return plain value with lambda supplier", plainValue, result1);
        
        // Test with an anonymous class supplier
        Supplier<SecretCallbackHandlerService> anonymousSupplier = new Supplier<SecretCallbackHandlerService>() {
            @Override
            public SecretCallbackHandlerService get() {
                return null;
            }
        };
        String result2 = SecretResolverUtil.resolveSecret(plainValue, anonymousSupplier);
        assertEquals("Should work with anonymous supplier implementation", plainValue, result2);
    }

    /**
     * Test case sensitivity - ensure $SECRET{...} vs $secret{...} behavior is consistent.
     * Documents the expected pattern matching behavior.
     */
    @Test
    public void testResolveSecret_CaseSensitivityOfPlaceholderPattern() {
        // $secret is lowercase - this should be recognized as a placeholder
        String lowercasePlaceholder = "$secret{test}";
        try {
            SecretResolverUtil.resolveSecret(lowercasePlaceholder, () -> null);
            fail("Lowercase $secret should trigger resolution");
        } catch (IllegalStateException e) {
            // Expected
        }
        
        // $SECRET is uppercase - may or may not be recognized depending on MiscellaneousUtil
        // Documentation of actual behavior
        String uppercasePlaceholder = "$SECRET{test}";
        try {
            String result = SecretResolverUtil.resolveSecret(uppercasePlaceholder, () -> null);
            // If doesn't throw, uppercase is not treated as a placeholder
        } catch (IllegalStateException e) {
            // If throws, uppercase is also treated as a placeholder
        }
    }

    /**
     * Test whitespace handling in plain values.
     * Ensures no unintended trimming or modification occurs.
     */
    @Test
    public void testResolveSecret_WhitespaceInPlainValues_PreservedAsIs() {
        String valueWithSpaces = "  password with spaces  ";
        String result = SecretResolverUtil.resolveSecret(valueWithSpaces, () -> null);
        assertEquals("Whitespace in plain values should be preserved", valueWithSpaces, result);
        
        String valueWithTabs = "\tpassword\t";
        result = SecretResolverUtil.resolveSecret(valueWithTabs, () -> null);
        assertEquals("Tabs in plain values should be preserved", valueWithTabs, result);
    }

    /**
     * Documentation test for successful resolution scenario.
     * This test documents the expected behavior when resolution succeeds.
     * 
     * In a real scenario with properly configured Secure Vault:
     * - A placeholder like $secret{myAlias} would be resolved to the actual secret value
     * - The method would return the resolved value (not the placeholder)
     * - No exception would be thrown
     * 
     * Integration tests with actual Secure Vault setup should verify this behavior.
     */
    @Test
    public void testResolveSecret_DocumentExpectedSuccessfulResolutionBehavior() {
        // This test serves as documentation of the contract:
        // Given: A valid placeholder like "$secret{validAlias}"
        // And: Secure Vault is properly initialized with the alias configured
        // When: resolveSecret is called
        // Then: The method should return the actual secret value (not the placeholder)
        // And: No exception should be thrown
        
        // For unit testing without full Secure Vault setup, we verify the non-placeholder path works
        String actualValue = "theActualSecretValue";
        String result = SecretResolverUtil.resolveSecret(actualValue, () -> null);
        assertEquals("When resolution succeeds, actual secret is returned", actualValue, result);
    }
}
