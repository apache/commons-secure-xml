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

package org.apache.commons.xml.secure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests whether Saxon's XSLT 3.0 URI-fetching functions can pull external resources into a transform result through a secure {@code TransformerFactory}.
 *
 * <p>The XPath 3.1 {@code unparsed-text} family and {@code json-doc} do not go through the JAXP {@code URIResolver} that governs {@code document()} and
 * {@code xsl:include}/{@code xsl:import}: Saxon routes them through the {@code Configuration}'s resource resolver. This test is the TrAX-side companion of
 * {@link SaxonXPathExternalCallsTest}, confirming that the floor {@code SaxonProvider} installs on the transformer path also closes these functions when they
 * are called from a stylesheet.</p>
 *
 * <p>The three content functions ({@code unparsed-text}, {@code unparsed-text-lines}, {@code json-doc}) are checked as a leak pair: an unconfigured Saxon
 * factory resolves the URI and copies {@link AttackTestSupport#LEAKED_MARKER} into the output, while the secure factory must not. {@code unparsed-text-available}
 * discloses no content, so it is checked as an existence oracle: the unconfigured factory distinguishes an existing fixture from a missing one, and the secure
 * factory must not.</p>
 *
 * <p>Saxon is instantiated reflectively and every test skips when it is absent, so under the surefire group filters the checks are effective on the test-saxon
 * and test-saxon-xerces executions.</p>
 */
@Tag("trax")
class SaxonTransformerExternalCallsTest {

    private static final String SAXON_TRANSFORMER_FACTORY_CLASS = "net.sf.saxon.TransformerFactoryImpl";

    /** Runs the expression through the secure Saxon factory; a throw is an acceptable block, otherwise the marker must be absent. */
    private static void assertSecureDoesNotLeak(final String expression) {
        try {
            final String result = transform(SecureTransformerFactory.secure(saxonFactory()), expression);
            assertFalse(result.contains(AttackTestSupport.LEAKED_MARKER), "secure Saxon transform leaked through " + expression + ":\n" + result);
        } catch (final TransformerException blocked) {
            // Throwing also prevents the leak.
        }
    }

    /** Runs the expression through the unconfigured Saxon factory and asserts the marker is resolved into the output (leak control). */
    private static void assertUnconfiguredLeaks(final String expression) throws TransformerException {
        final String result = transform(saxonFactory(), expression);
        assertTrue(result.contains(AttackTestSupport.LEAKED_MARKER), "unconfigured Saxon was expected to resolve " + expression + ", got: " + result);
    }

    private static void assumeSaxonPresent() {
        boolean present;
        try {
            Class.forName(SAXON_TRANSFORMER_FACTORY_CLASS);
            present = true;
        } catch (final ClassNotFoundException e) {
            present = false;
        }
        Assumptions.assumeTrue(present, "Saxon is not on the classpath");
    }

    /** The {@code unparsed-text-available} answer under the secure factory, or {@code "blocked"} when the transform throws. */
    private static String availabilityUnderSecure(final String uri) {
        try {
            return transform(SecureTransformerFactory.secure(saxonFactory()), "unparsed-text-available('" + uri + "')").contains("true") ? "true" : "false";
        } catch (final TransformerException blocked) {
            return "blocked";
        }
    }

    /** URL of a sibling resource that does not exist, so a real fetch fails; used as the negative side of the existence-oracle check. */
    private static String missingUrl() {
        return url("referenced.txt").replaceFirst("referenced\\.txt$", "does-not-exist.txt");
    }

    private static TransformerFactory saxonFactory() {
        try {
            return (TransformerFactory) Class.forName(SAXON_TRANSFORMER_FACTORY_CLASS).getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Cannot instantiate " + SAXON_TRANSFORMER_FACTORY_CLASS, e);
        }
    }

    /** Wraps a single XPath 3.1 expression in an XSLT 3.0 stylesheet that copies its string value into the output. */
    private static String stylesheet(final String expression) {
        return "<?xml version=\"1.0\"?>\n"
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\">\n"
                + "    <leaked><xsl:value-of select=\"" + expression + "\"/></leaked>\n"
                + "  </xsl:template>\n"
                + "</xsl:stylesheet>\n";
    }

    private static String transform(final TransformerFactory factory, final String expression) throws TransformerException {
        final StringWriter sink = new StringWriter();
        factory.newTemplates(AttackTestSupport.streamSource(stylesheet(expression))).newTransformer()
                .transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        return sink.toString();
    }

    /** URL of a fixture that carries {@link AttackTestSupport#LEAKED_MARKER}; {@code name} is a file under {@code src/test/resources/leaked/}. */
    private static String url(final String name) {
        return AttackTestSupport.resourceUrl(name).toString();
    }

    @Test
    void secureTransformerBlocksJsonDoc() {
        assumeSaxonPresent();
        assertSecureDoesNotLeak("json-doc('" + url("referenced.json") + "')?leaked");
    }

    @Test
    void secureTransformerBlocksUnparsedText() {
        assumeSaxonPresent();
        assertSecureDoesNotLeak("unparsed-text('" + url("referenced.txt") + "')");
    }

    @Test
    void secureTransformerBlocksUnparsedTextLines() {
        assumeSaxonPresent();
        assertSecureDoesNotLeak("string-join(unparsed-text-lines('" + url("referenced.txt") + "'), ' ')");
    }

    @Test
    void secureTransformerHidesUnparsedTextAvailability() {
        assumeSaxonPresent();
        // The unconfigured factory is a working existence oracle: true for the fixture, false for a missing sibling.
        final TransformerFactory unconfigured = saxonFactory();
        try {
            assertTrue(transform(unconfigured, "unparsed-text-available('" + url("referenced.txt") + "')").contains("true"),
                    "unconfigured Saxon should report the fixture as available");
            assertTrue(transform(unconfigured, "unparsed-text-available('" + missingUrl() + "')").contains("false"),
                    "unconfigured Saxon should report the missing sibling as unavailable");
        } catch (final TransformerException e) {
            throw new AssertionError("unconfigured Saxon unparsed-text-available control failed", e);
        }
        // The secure factory must not reflect the real filesystem: the answer for the fixture and the missing sibling must match, so it is no oracle.
        final String secureExisting = availabilityUnderSecure(url("referenced.txt"));
        final String secureMissing = availabilityUnderSecure(missingUrl());
        assertNotEquals("true:false", secureExisting + ":" + secureMissing,
                "secure Saxon unparsed-text-available still distinguishes an existing file from a missing one");
    }

    @Test
    void unconfiguredTransformerLeaksJsonDoc() throws TransformerException {
        assumeSaxonPresent();
        assertUnconfiguredLeaks("json-doc('" + url("referenced.json") + "')?leaked");
    }

    @Test
    void unconfiguredTransformerLeaksUnparsedText() throws TransformerException {
        assumeSaxonPresent();
        assertUnconfiguredLeaks("unparsed-text('" + url("referenced.txt") + "')");
    }

    @Test
    void unconfiguredTransformerLeaksUnparsedTextLines() throws TransformerException {
        assumeSaxonPresent();
        assertUnconfiguredLeaks("string-join(unparsed-text-lines('" + url("referenced.txt") + "'), ' ')");
    }
}
