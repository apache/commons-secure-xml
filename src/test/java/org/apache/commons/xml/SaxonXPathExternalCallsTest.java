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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks whether Saxon's XPath 3.1 URI-fetching functions can pull external resources into the result.
 *
 * <p>Saxon ships several XPath 3.1 functions that open arbitrary URIs during evaluation. None require a context node; each is triggered purely by the string
 * URI it receives. They are <em>not</em> classified as extension functions in Saxon's vocabulary, so disabling {@code ALLOW_EXTERNAL_FUNCTIONS} is not enough
 * to block them; a complete secure has to close the URI-resolution path.</p>
 *
 * <p>Each fixture under {@code src/test/resources/leaked/} contains the {@link #MARKER} string. The tests dispatch the URI-fetching function at the file's URL
 * and check whether the marker reaches the result.</p>
 *
 * <p>Cases covered, each as a pair (unconfigured Saxon factory expected to leak, secure Saxon factory expected to block):</p>
 *
 * <ul>
 *   <li>{@code doc(uri)} reading {@code referenced.xml}.</li>
 *   <li>{@code json-doc(uri)} reading {@code referenced.json}.</li>
 *   <li>{@code unparsed-text(uri)} reading {@code referenced.txt}.</li>
 * </ul>
 *
 * <p>The JDK's built-in XPathFactory and Xalan have no equivalent vectors. The class is tagged {@code xpath3}, so under the surefire group filters it only
 * runs in the {@code test-saxon} execution where Saxon is on the classpath.</p>
 */
@Tag("xpath3")
class SaxonXPathExternalCallsTest {

    private static final String MARKER = "All your base are belong to us";
    private static final String SAXON_XPATH_FACTORY_CLASS = "net.sf.saxon.xpath.XPathFactoryImpl";

    private static void assertCallExcludesMarker(final XPathFactory factory, final String expression) {
        final String result;
        try {
            result = evaluateAsString(factory, expression);
        } catch (final Exception e) {
            return; // secure blocked at evaluation; acceptable outcome.
        }
        assertFalse(result.contains(MARKER),
                "Securing did not block the external reference; result contained marker '" + MARKER + "'.\nFull result:\n" + result);
    }

    private static void assertCallLeaksMarker(final XPathFactory factory, final String expression) {
        final String result;
        try {
            result = evaluateAsString(factory, expression);
        } catch (final Exception e) {
            fail("Unconfigured Saxon XPath should resolve the external resource, but threw: " + e);
            return;
        }
        assertTrue(result.contains(MARKER),
                "Expected marker '" + MARKER + "' in result, got: " + result);
    }

    private static String docExpression() {
        return "doc('" + AttackTestSupport.resourceUrl("referenced.xml") + "')/leaked";
    }

    private static String evaluateAsString(final XPathFactory factory, final String expression)
            throws ParserConfigurationException, XPathExpressionException {
        return factory.newXPath().evaluate(expression,
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument());
    }

    private static XPathFactory hardenedSaxonXPathFactory() {
        return SaxonProvider.configure(saxonXPathFactory());
    }

    private static String jsonDocExpression() {
        return "json-doc('" + AttackTestSupport.resourceUrl("referenced.json") + "')?leaked";
    }

    /**
     * Instantiates Saxon's {@code XPathFactoryImpl} reflectively.
     *
     * <p>Saxon 12.9 ships no {@code META-INF/services} entry for
     * {@link javax.xml.xpath.XPathFactory}, so {@link XPathFactory#newInstance(String)} cannot find it; direct instantiation bypasses that lookup.</p>
     */
    private static XPathFactory saxonXPathFactory() {
        try {
            return (XPathFactory) Class.forName(SAXON_XPATH_FACTORY_CLASS).getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Cannot instantiate " + SAXON_XPATH_FACTORY_CLASS, e);
        }
    }

    private static String unparsedTextExpression() {
        return "unparsed-text('" + AttackTestSupport.resourceUrl("referenced.txt") + "')";
    }

    @Test
    void secureXPathBlocksDoc() {
        assertCallExcludesMarker(hardenedSaxonXPathFactory(), docExpression());
    }

    @Test
    void secureXPathBlocksJsonDoc() {
        assertCallExcludesMarker(hardenedSaxonXPathFactory(), jsonDocExpression());
    }

    @Test
    void secureXPathBlocksUnparsedText() {
        assertCallExcludesMarker(hardenedSaxonXPathFactory(), unparsedTextExpression());
    }

    @Test
    void unconfiguredXPathLeaksDoc() {
        assertCallLeaksMarker(saxonXPathFactory(), docExpression());
    }

    @Test
    void unconfiguredXPathLeaksJsonDoc() {
        assertCallLeaksMarker(saxonXPathFactory(), jsonDocExpression());
    }

    @Test
    void unconfiguredXPathLeaksUnparsedText() {
        assertCallLeaksMarker(saxonXPathFactory(), unparsedTextExpression());
    }
}
