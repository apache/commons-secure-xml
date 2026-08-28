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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.xml.sax.XMLReader;

/**
 * Checks that {@code jdk.xml.overrideDefaultParser} selects which hardened parser family performs the source rewrites on factories that recognize the feature.
 *
 * <p>The wrapped implementations' internal parsers are never used — the wrappers parse every source themselves — so instead of configuring the delegate the
 * wrappers read the feature: {@code false} (the JDK's default) pins the platform's built-in parser, {@code true} (or a delegate that does not recognize the
 * feature) keeps the pluggable lookup. Both choices are hardened, so the feature carries no security weight. The tests pin the JDK implementations through
 * {@code newDefaultInstance()}, so they discriminate in every JVM execution; under test-jdk-xerces the two parser families genuinely differ.</p>
 */
@Tag("trax")
@Tag("xpath")
@Tag("schema")
class OverrideDefaultParserTest {

    private static final String FEATURE = SecureSAXParserFactory.OVERRIDE_DEFAULT_PARSER;

    /** Package prefix of the JDK's built-in parsers, the family a {@code false} feature value pins. */
    private static final String JDK_INTERNAL_PREFIX = "com.sun.org.apache.xerces.internal.";

    private static String transform(final TransformerFactory factory, final String text) throws Exception {
        final Transformer transformer = factory.newTransformer(AttackTestSupport.streamSource(AttackTestSupport.xsltBody(text)));
        final StringWriter out = new StringWriter();
        transformer.transform(AttackTestSupport.streamSource(AttackTestSupport.xmlBody("ignored")), new StreamResult(out));
        return out.toString();
    }

    @Test
    void hardenedReaderFollowsFlag() throws Exception {
        assumeFalse(AttackTestSupport.IS_ANDROID);
        final XMLReader pinned = ((SecureXMLReader) SecureSAXParserFactory.newHardenedReader(false)).getDelegate();
        assertTrue(pinned.getClass().getName().startsWith(JDK_INTERNAL_PREFIX), pinned.getClass().getName());
        final XMLReader pluggable = ((SecureXMLReader) SecureSAXParserFactory.newHardenedReader(true)).getDelegate();
        final XMLReader lookedUp = ((SecureXMLReader) SecureSAXParserFactory.newNSInstance().newSAXParser().getXMLReader()).getDelegate();
        assertEquals(lookedUp.getClass(), pluggable.getClass());
        if (xercesOnClasspath()) {
            // The two families genuinely differ only where a third-party parser wins the lookup (the test-jdk-xerces execution).
            assertNotEquals(pinned.getClass(), pluggable.getClass());
        }
    }

    @Test
    void schemaFactoryReadsFeatureAtCreation() throws Exception {
        assumeFalse(AttackTestSupport.IS_ANDROID);
        final SchemaFactory factory = SecureSchemaFactory.newDefaultInstance();
        assertFalse(factory.getFeature(FEATURE));
        assertFalse(((SecureSchema) factory.newSchema(AttackTestSupport.streamSource(AttackTestSupport.BENIGN_SCHEMA))).overrideDefaultParser);
        factory.setFeature(FEATURE, true);
        assertTrue(((SecureSchema) factory.newSchema(AttackTestSupport.streamSource(AttackTestSupport.BENIGN_SCHEMA))).overrideDefaultParser);
    }

    @Test
    void transformerFactoryReadsFeatureAtCreation() throws Exception {
        assumeFalse(AttackTestSupport.IS_ANDROID);
        final TransformerFactory factory = SecureTransformerFactory.newDefaultInstance();
        assertFalse(factory.getFeature(FEATURE));
        assertFalse(((SecureTemplates) factory.newTemplates(AttackTestSupport.streamSource(AttackTestSupport.xsltBody("probe")))).overrideDefaultParser);
        factory.setFeature(FEATURE, true);
        assertTrue(((SecureTemplates) factory.newTemplates(AttackTestSupport.streamSource(AttackTestSupport.xsltBody("probe")))).overrideDefaultParser);
    }

    @Test
    // The JDK default TrAX pinned by newDefaultInstance() is XSLTC, which defines the compiled translet class at run time — impossible in a closed-world
    // native image (the reason the native profile substitutes Xalan). The capture tests above stay enabled: newTemplates never loads the translet.
    @DisabledInNativeImage
    void transformSucceedsUnderBothParserFamilies() throws Exception {
        assumeFalse(AttackTestSupport.IS_ANDROID);
        final TransformerFactory factory = SecureTransformerFactory.newDefaultInstance();
        // Feature false (the JDK's default): stylesheet and source parse through the pinned platform parser.
        assertTrue(transform(factory, "pinned").contains("pinned"));
        factory.setFeature(FEATURE, true);
        // Feature true: same result through the pluggable lookup.
        assertTrue(transform(factory, "pluggable").contains("pluggable"));
    }

    private static boolean xercesOnClasspath() {
        try {
            Class.forName("org.apache.xerces.jaxp.SAXParserFactoryImpl");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    void xPathFactoryReadsFeatureAtCreation() throws Exception {
        assumeFalse(AttackTestSupport.IS_ANDROID);
        final XPathFactory factory = HardeningXPathFactory.newDefaultInstance();
        assertFalse(factory.getFeature(FEATURE));
        assertFalse(((SecureXPath) factory.newXPath()).overrideDefaultParser);
        factory.setFeature(FEATURE, true);
        assertTrue(((SecureXPath) factory.newXPath()).overrideDefaultParser);
    }
}
