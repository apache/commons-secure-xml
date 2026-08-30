/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * Checks that the document parse behind {@code XPath.evaluate(String, InputSource)} (and its compiled {@code XPathExpression} counterpart) cannot pull in an
 * external general entity.
 *
 * <p>The stock JDK and Apache Xalan implement the {@link InputSource}-taking {@code evaluate} entry points by provisioning an internal document
 * parser that {@code FEATURE_SECURE_PROCESSING} on the {@link XPathFactory} does not reach. The {@link SecureXPathFactory} wrapper parses the input
 * through a secure {@code DocumentBuilder} instead, so the external reference resolves to empty on the floor, while the
 * evaluation itself still works. Tagged {@code xpath}, so it runs under test-stockjdk, test-jdk-xerces, test-xalan and test-xalan-xerces; the Saxon engine takes the separate
 * {@code SaxonProvider} path covered by {@code SaxonXPathExternalCallsTest}.</p>
 */
@Tag("xpath")
class XPathInputSourceTest {

    private static final String EXPRESSION = "string(/root/child)";

    /** {@link AttackTestSupport#xmlBody} content whose single entity reference resolves to {@link AttackTestSupport#LEAKED_MARKER} if the DTD is fetched. */
    private static String entityPayload() {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [\n  <!ENTITY xxe SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.txt") + "\">\n]>\n"
                + AttackTestSupport.xmlBody("&xxe;");
    }

    @Test
    void secureXPathEvaluateDoesNotLeak() throws Exception {
        // Deterministic on every engine: the entity is declared in the internal subset and the floor resolves only its
        // external content — to empty replacement text — so the pre-parse completes and the reference expands to nothing.
        final String result = SecureXPathFactory.newInstance().newXPath().evaluate(EXPRESSION, AttackTestSupport.inputSource(entityPayload()));
        assertFalse(result.contains(AttackTestSupport.LEAKED_MARKER), "external entity leaked into the XPath result: " + result);
    }

    @Test
    void secureXPathEvaluatesPlainDocument() throws Exception {
        // Positive control: the secure pre-parse still evaluates an entity-free document end to end.
        final String result = SecureXPathFactory.newInstance().newXPath().evaluate(EXPRESSION,
                AttackTestSupport.inputSource(AttackTestSupport.xmlBody("plain text")));
        assertEquals("plain text", result, "secured XPath should evaluate a plain document");
    }

    @Test
    void secureXPathExpressionEvaluateDoesNotLeak() throws Exception {
        // Same declared-entity outcome as above on the compiled-expression entry point.
        final String result = SecureXPathFactory.newInstance().newXPath().compile(EXPRESSION).evaluate(AttackTestSupport.inputSource(entityPayload()));
        assertFalse(result.contains(AttackTestSupport.LEAKED_MARKER), "external entity leaked into the compiled XPath result: " + result);
    }

    @Test
    void unconfiguredXPathEvaluateLeaks() throws Exception {
        // Leak control: the unconfigured engine's internal parser resolves the entity, which is exactly what the wrapper exists to prevent.
        final String result = XPathFactory.newInstance().newXPath().evaluate(EXPRESSION, AttackTestSupport.inputSource(entityPayload()));
        assertTrue(result.contains(AttackTestSupport.LEAKED_MARKER), "unconfigured XPath was expected to resolve the external entity, got: " + result);
    }
}
