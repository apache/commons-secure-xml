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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests that Saxon's alternate public {@code TransformerFactory} entry point routes to the same locked-down {@code Configuration} as the registered one.
 *
 * <p>Saxon ships {@code net.sf.saxon.BasicTransformerFactory}, a public subclass of the registered {@code net.sf.saxon.TransformerFactoryImpl}, selectable
 * through the standard TrAX system property. Recognition by package prefix sends it through {@code SaxonProvider}; a name-based recognition would let it fall
 * to the generic recipe. The probe uses {@code fn:collection}, which bypasses Saxon's resource-resolution chain and fetches directly: only the empty
 * {@code CollectionFinder} that {@code SaxonProvider} installs closes it, so the generic recipe (which leaves it open even after wrapping) does not. The
 * factory is instantiated reflectively and the tests skip when Saxon is not on the classpath, so under the surefire group filters the checks are effective on
 * the test-saxon and test-saxon-xerces executions.</p>
 */
@Tag("trax")
class SaxonAlternateFactoryTest {

    private static final String BASIC_FACTORY_CLASS = "net.sf.saxon.BasicTransformerFactory";

    private static void assumeSaxonPresent() {
        boolean present;
        try {
            Class.forName(BASIC_FACTORY_CLASS);
            present = true;
        } catch (final ClassNotFoundException e) {
            present = false;
        }
        Assumptions.assumeTrue(present, "Saxon is not on the classpath");
    }

    /** Instantiates {@code BasicTransformerFactory} reflectively, so this test compiles and loads without Saxon on the classpath. */
    private static TransformerFactory basicSaxonFactory() {
        try {
            return (TransformerFactory) Class.forName(BASIC_FACTORY_CLASS).getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Cannot instantiate " + BASIC_FACTORY_CLASS, e);
        }
    }

    /** A {@code collection()} over the test fixtures whose {@code referenced.xml} carries {@link AttackTestSupport#LEAKED_MARKER}. */
    private static String collectionStylesheet() {
        final String collection = AttackTestSupport.resourceUrl("referenced.xml").toString().replaceFirst("referenced\\.xml$", "?select=referenced.xml");
        return "<?xml version=\"1.0\"?>\n"
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\">\n"
                + "    <leaked><xsl:value-of select=\"string(collection('" + collection + "')/leaked)\"/></leaked>\n"
                + "  </xsl:template>\n"
                + "</xsl:stylesheet>\n";
    }

    private static String transform(final TransformerFactory factory) throws TransformerException {
        final StringWriter sink = new StringWriter();
        factory.newTemplates(AttackTestSupport.streamSource(collectionStylesheet())).newTransformer()
                .transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        return sink.toString();
    }

    @Test
    void secureBasicFactoryDoesNotLeakCollection() {
        assumeSaxonPresent();
        try {
            final String result = transform(SecureTransformerFactory.secure(basicSaxonFactory()));
            assertFalse(result.contains(AttackTestSupport.LEAKED_MARKER), "collection() leaked through the alternate Saxon factory:\n" + result);
        } catch (final TransformerException blocked) {
            // Throwing is an acceptable outcome since it also prevents leaking the marker.
        }
    }

    @Test
    void unconfiguredBasicFactoryLeaksCollection() throws TransformerException {
        assumeSaxonPresent();
        // Leak control: the bare alternate factory resolves the collection, which is exactly what routing it through SaxonProvider exists to prevent.
        final String result = transform(basicSaxonFactory());
        assertTrue(result.contains(AttackTestSupport.LEAKED_MARKER), "bare alternate Saxon factory was expected to resolve collection(), got: " + result);
    }
}
