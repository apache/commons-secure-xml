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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Public-API smoke tests for {@link org.apache.commons.xml.secure}.
 * <p>
 * Attack tests live in the {@code attacks} sub-package; this file only verifies that new factories are returned, that they report safe defaults, and that a
 * benign document still parses successfully.
 * </p>
 */
class SecureFactoriesSmokeTest {

    private static final String BENIGN_XML = "<?xml version=\"1.0\"?>\n<root><child>hello</child></root>\n";

    @Test
    @Tag("dom")
    void benignDocumentParses() throws Exception {
        final Document doc = SecureDocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML)));
        assertNotNull(doc);
        assertNotNull(doc.getDocumentElement());
    }

    // The explicit-class-name tests discover the runtime default implementation through the raw JAXP factory,
    // so they stay portable across the JAXP implementations of the surefire matrix.
    @Test
    @Tag("dom")
    void explicitClassNameDocumentBuilderFactoryIsSecure() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING, "platform DOM does not support FEATURE_SECURE_PROCESSING");
        final Class<?> impl = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("dom")
    void explicitClassNameNSDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        final Class<?> impl = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newNSInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.isNamespaceAware());
        if (AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void explicitClassNameNSSAXParserFactoryIsNamespaceAware() throws Exception {
        final Class<?> impl = SAXParserFactory.newInstance().getClass();
        final SAXParserFactory factory = SecureSAXParserFactory.newNSInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.isNamespaceAware());
        if (AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void explicitClassNameSAXParserFactoryIsSecure() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING, "platform SAX does not support FEATURE_SECURE_PROCESSING");
        final Class<?> impl = SAXParserFactory.newInstance().getClass();
        final SAXParserFactory factory = SecureSAXParserFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("schema")
    void explicitClassNameSchemaFactoryIsSecure() throws Exception {
        final Class<?> impl = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass();
        final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI, impl.getName(), impl.getClassLoader());
        // Schema securing is the resolver floor plus wrapped products; FEATURE_SECURE_PROCESSING stays untouched (the secure sub-parsers carry it).
        assertInstanceOf(SecureSchema.class, factory.newSchema());
    }

    @Test
    @Tag("trax")
    void explicitClassNameTransformerFactoryIsSecure() {
        final Class<?> impl = TransformerFactory.newInstance().getClass();
        final TransformerFactory factory = SecureTransformerFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("xpath")
    void explicitClassNameXPathFactoryIsSecure() throws Exception {
        final Class<?> impl = XPathFactory.newInstance().getClass();
        final XPathFactory factory = SecureXPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("stax")
    void factoryIdXMLInputFactoryIsSecure() {
        final String factoryId = "org.apache.commons.xml.secure.test.staxFactory";
        // XMLInputFactory.newInstance, not newFactory: Android's StAX API predates newFactory, and this file also compiles against android.jar.
        System.setProperty(factoryId, XMLInputFactory.newInstance().getClass().getName());
        try {
            final XMLInputFactory factory = SecureXMLInputFactory.newFactory(factoryId, getClass().getClassLoader());
            assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
        } finally {
            System.clearProperty(factoryId);
        }
    }

    @Test
    @Tag("stax")
    void newDefaultFactoryXMLInputFactoryIsSecure() {
        final XMLInputFactory factory = SecureXMLInputFactory.newDefaultFactory();
        assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    // The newDefault* methods resolve the Java 9 JAXP method at runtime and fall back to the JDK's built-in implementation on Java 8. The dom and sax
    // variants also run on Android, whose JAXP predates newDefaultInstance and carries no JDK-internal fallback: the methods degrade there to the standard
    // lookup, which Android pins to the platform implementation.
    @Test
    @Tag("dom")
    void newDefaultInstanceDocumentBuilderFactoryIsUsable() throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newDefaultInstance();
        assertNotNull(factory.newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML))).getDocumentElement());
        if (AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void newDefaultInstanceSAXParserFactoryIsUsable() throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newDefaultInstance();
        factory.newSAXParser().parse(new InputSource(new StringReader(BENIGN_XML)), new DefaultHandler());
        if (AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("schema")
    void newDefaultInstanceSchemaFactoryIsSecure() throws Exception {
        final SchemaFactory factory = SecureSchemaFactory.newDefaultInstance();
        // Schema securing is the resolver floor plus wrapped products; FEATURE_SECURE_PROCESSING stays untouched (the secure sub-parsers carry it).
        assertInstanceOf(SecureSchema.class, factory.newSchema());
    }

    @Test
    @Tag("trax")
    void newDefaultInstanceTransformerFactoryIsSecure() {
        // TrAX is outside the Android newDefaultInstance degradation: the platform provides neither the method nor the JDK class, so the miss still throws.
        if (AttackTestSupport.IS_ANDROID) {
            assertThrows(TransformerFactoryConfigurationError.class, SecureTransformerFactory::newDefaultInstance);
            return;
        }
        final TransformerFactory factory = SecureTransformerFactory.newDefaultInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("xpath")
    void newDefaultInstanceXPathFactoryIsSecure() throws Exception {
        final XPathFactory factory = SecureXPathFactory.newDefaultInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("dom")
    void newDefaultNSInstanceDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newDefaultNSInstance();
        assertTrue(factory.isNamespaceAware());
        if (AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void newDefaultNSInstanceSAXParserFactoryIsNamespaceAware() throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newDefaultNSInstance();
        assertTrue(factory.isNamespaceAware());
        if (AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("dom")
    void newDocumentBuilderFactoryDisablesXIncludeAndValidation() {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        if (AttackTestSupport.DOM_SUPPORTS_XINCLUDE) {
            assertFalse(factory.isXIncludeAware(), "XInclude must be off by default");
        }
        assertFalse(factory.isValidating(), "Validation must be off by default");
    }

    @Test
    @Tag("dom")
    void newDocumentBuilderFactoryEnablesSecureProcessing() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING, "platform DOM does not support FEATURE_SECURE_PROCESSING");
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING), "FEATURE_SECURE_PROCESSING must be on");
    }

    @Test
    @Tag("dom")
    void newDocumentBuilderFactoryReturnsFreshInstance() {
        final DocumentBuilderFactory a = SecureDocumentBuilderFactory.newInstance();
        final DocumentBuilderFactory b = SecureDocumentBuilderFactory.newInstance();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    @Tag("stax")
    void newFactoryReturnsFreshInstance() {
        final XMLInputFactory a = SecureXMLInputFactory.newFactory();
        final XMLInputFactory b = SecureXMLInputFactory.newFactory();
        assertNotSame(a, b);
        assertEquals(Boolean.TRUE, a.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    // The newNSInstance family (Java 13) falls back to enabling namespace awareness on the corresponding newInstance lookup, the behavior the JAXP methods
    // are specified to have, so the non-default variants work on every platform including Android.
    @Test
    @Tag("dom")
    void newNSInstanceDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newNSInstance();
        assertTrue(factory.isNamespaceAware());
        assertNotNull(factory.newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML))).getDocumentElement());
        if (AttackTestSupport.DOM_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void newNSInstanceSAXParserFactoryIsNamespaceAware() throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newNSInstance();
        assertTrue(factory.isNamespaceAware());
        factory.newSAXParser().parse(new InputSource(new StringReader(BENIGN_XML)), new DefaultHandler());
        if (AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void newSAXParserFactoryReturnsFreshInstance() {
        final SAXParserFactory a = SecureSAXParserFactory.newInstance();
        final SAXParserFactory b = SecureSAXParserFactory.newInstance();
        assertNotSame(a, b);
        assertFalse(a.isValidating());
        if (AttackTestSupport.SAX_SUPPORTS_XINCLUDE) {
            assertFalse(a.isXIncludeAware());
        }
    }

    @Test
    @Tag("schema")
    void newSchemaFactoryReturnsFreshInstance() throws Exception {
        final SchemaFactory a = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final SchemaFactory b = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertNotSame(a, b);
        // Schema securing is the resolver floor plus wrapped products; FEATURE_SECURE_PROCESSING stays untouched (the secure sub-parsers carry it).
        assertInstanceOf(SecureSchema.class, a.newSchema());
    }

    @Test
    @Tag("trax")
    void newTransformerFactoryReturnsFreshInstance() {
        final TransformerFactory a = SecureTransformerFactory.newInstance();
        final TransformerFactory b = SecureTransformerFactory.newInstance();
        assertNotSame(a, b);
    }

    @Test
    @Tag("stax")
    void newXMLInputFactoryReturnsFreshInstance() {
        final XMLInputFactory a = SecureXMLInputFactory.newInstance();
        final XMLInputFactory b = SecureXMLInputFactory.newInstance();
        assertNotSame(a, b);
        assertEquals(Boolean.TRUE, a.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(Boolean.FALSE, a.getProperty(XMLInputFactory.IS_VALIDATING));
    }

    @Test
    @Tag("xpath")
    void newXPathFactoryReturnsFreshInstance() throws Exception {
        final XPathFactory a = SecureXPathFactory.newInstance();
        final XPathFactory b = SecureXPathFactory.newInstance();
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    /**
     * The public classes must not extend their JAXP factory type: extending it would inherit the JAXP static factory methods, letting a caller obtain an
     * unsecured factory through an inherited method such as {@code newInstance(String, ClassLoader)} or {@code newDefaultInstance()}.
     */
    @Test
    @Tag("dom")
    @Tag("sax")
    @Tag("stax")
    @Tag("trax")
    @Tag("xpath")
    @Tag("schema")
    void publicClassesDoNotExtendTheirJaxpFactoryType() {
        assertFalse(DocumentBuilderFactory.class.isAssignableFrom(SecureDocumentBuilderFactory.class));
        assertFalse(SAXParserFactory.class.isAssignableFrom(SecureSAXParserFactory.class));
        assertFalse(SchemaFactory.class.isAssignableFrom(SecureSchemaFactory.class));
        assertFalse(TransformerFactory.class.isAssignableFrom(SecureTransformerFactory.class));
        assertFalse(XMLInputFactory.class.isAssignableFrom(SecureXMLInputFactory.class));
        assertFalse(XPathFactory.class.isAssignableFrom(SecureXPathFactory.class));
    }

    @Test
    @Tag("dom")
    void unknownFactoryClassNameThrows() {
        assertThrows(FactoryConfigurationError.class, () -> SecureDocumentBuilderFactory.newInstance("no.such.FactoryClass", null));
    }
}
