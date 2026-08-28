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
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Public-API smoke tests for {@link org.apache.commons.xml}.
 * <p>
 * Attack tests live in the {@code attacks} sub-package; this file only verifies that new factories are returned, that they report safe defaults, and that a
 * benign document still parses successfully.
 * </p>
 */
class HardeningFactoriesSmokeTest {

    private static final String BENIGN_XML = "<?xml version=\"1.0\"?>\n<root><child>hello</child></root>\n";

    /**
     * The public classes must not extend their JAXP factory type: extending it would inherit the JAXP static factory methods, letting a caller obtain a
     * non-hardened factory through an inherited method such as {@code newInstance(String, ClassLoader)} or {@code newDefaultInstance()}.
     */
    @Test
    void publicClassesDoNotExtendTheirJaxpFactoryType() {
        assertFalse(DocumentBuilderFactory.class.isAssignableFrom(SecureDocumentBuilderFactory.class));
        assertFalse(SAXParserFactory.class.isAssignableFrom(SecureSAXParserFactory.class));
        assertFalse(SchemaFactory.class.isAssignableFrom(HardeningSchemaFactory.class));
        assertFalse(TransformerFactory.class.isAssignableFrom(HardeningTransformerFactory.class));
        assertFalse(XMLInputFactory.class.isAssignableFrom(HardeningXMLInputFactory.class));
        assertFalse(XPathFactory.class.isAssignableFrom(HardeningXPathFactory.class));
    }

    @Test
    void benignDocumentParses() throws Exception {
        final Document doc = SecureDocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML)));
        assertNotNull(doc);
        assertNotNull(doc.getDocumentElement());
    }

    @Test
    void newDocumentBuilderFactoryDisablesXIncludeAndValidation() {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        assertFalse(factory.isXIncludeAware(), "XInclude must be off by default");
        assertFalse(factory.isValidating(), "Validation must be off by default");
    }

    @Test
    void newDocumentBuilderFactoryEnablesSecureProcessing() throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING), "FEATURE_SECURE_PROCESSING must be on");
    }

    @Test
    void newDocumentBuilderFactoryReturnsFreshInstance() {
        final DocumentBuilderFactory a = SecureDocumentBuilderFactory.newInstance();
        final DocumentBuilderFactory b = SecureDocumentBuilderFactory.newInstance();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    void newSAXParserFactoryReturnsFreshInstance() {
        final SAXParserFactory a = SecureSAXParserFactory.newInstance();
        final SAXParserFactory b = SecureSAXParserFactory.newInstance();
        assertNotSame(a, b);
        assertFalse(a.isValidating());
        assertFalse(a.isXIncludeAware());
    }

    @Test
    void newSchemaFactoryReturnsFreshInstance() throws Exception {
        final SchemaFactory a = HardeningSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final SchemaFactory b = HardeningSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newTransformerFactoryReturnsFreshInstance() {
        final TransformerFactory a = HardeningTransformerFactory.newInstance();
        final TransformerFactory b = HardeningTransformerFactory.newInstance();
        assertNotSame(a, b);
    }

    @Test
    void newXMLInputFactoryReturnsFreshInstance() {
        final XMLInputFactory a = HardeningXMLInputFactory.newInstance();
        final XMLInputFactory b = HardeningXMLInputFactory.newInstance();
        assertNotSame(a, b);
        assertEquals(Boolean.TRUE, a.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(Boolean.FALSE, a.getProperty(XMLInputFactory.IS_VALIDATING));
    }

    @Test
    void newXPathFactoryReturnsFreshInstance() throws Exception {
        final XPathFactory a = HardeningXPathFactory.newInstance();
        final XPathFactory b = HardeningXPathFactory.newInstance();
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    // The explicit-class-name tests discover the runtime default implementation through the raw JAXP factory,
    // so they stay portable across the JAXP implementations of the surefire matrix.
    @Test
    void explicitClassNameDocumentBuilderFactoryIsHardened() throws Exception {
        final Class<?> impl = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameSAXParserFactoryIsHardened() throws Exception {
        final Class<?> impl = SAXParserFactory.newInstance().getClass();
        final SAXParserFactory factory = SecureSAXParserFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameSchemaFactoryIsHardened() throws Exception {
        final Class<?> impl = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass();
        final SchemaFactory factory = HardeningSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI, impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameTransformerFactoryIsHardened() {
        final Class<?> impl = TransformerFactory.newInstance().getClass();
        final TransformerFactory factory = HardeningTransformerFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameXPathFactoryIsHardened() throws Exception {
        final Class<?> impl = XPathFactory.newInstance().getClass();
        final XPathFactory factory = HardeningXPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newFactoryReturnsFreshInstance() {
        final XMLInputFactory a = HardeningXMLInputFactory.newFactory();
        final XMLInputFactory b = HardeningXMLInputFactory.newFactory();
        assertNotSame(a, b);
        assertEquals(Boolean.TRUE, a.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    @Test
    void factoryIdXMLInputFactoryIsHardened() {
        final String factoryId = "org.apache.commons.xml.test.staxFactory";
        // XMLInputFactory.newInstance, not newFactory: Android's StAX API predates newFactory, and this file also compiles against android.jar.
        System.setProperty(factoryId, XMLInputFactory.newInstance().getClass().getName());
        try {
            final XMLInputFactory factory = HardeningXMLInputFactory.newFactory(factoryId, getClass().getClassLoader());
            assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
        } finally {
            System.clearProperty(factoryId);
        }
    }

    @Test
    void unknownFactoryClassNameThrows() {
        assertThrows(FactoryConfigurationError.class, () -> SecureDocumentBuilderFactory.newInstance("no.such.FactoryClass", null));
    }

    // The newDefault* methods resolve the Java 9 JAXP method at runtime and fall back to the JDK's built-in implementation on Java 8. The dom and sax
    // variants also run on Android, whose JAXP predates newDefaultInstance and carries no JDK-internal fallback: the lookup miss surfaces there as the
    // factory's own FactoryConfigurationError, like any newInstance miss.
    @Test
    @Tag("dom")
    void newDefaultInstanceDocumentBuilderFactoryIsUsable() throws Exception {
        if (AttackTestSupport.IS_ANDROID) {
            assertThrows(FactoryConfigurationError.class, SecureDocumentBuilderFactory::newDefaultInstance);
            return;
        }
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newDefaultInstance();
        assertNotNull(factory.newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML))).getDocumentElement());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("sax")
    void newDefaultInstanceSAXParserFactoryIsUsable() throws Exception {
        if (AttackTestSupport.IS_ANDROID) {
            assertThrows(FactoryConfigurationError.class, SecureSAXParserFactory::newDefaultInstance);
            return;
        }
        final SAXParserFactory factory = SecureSAXParserFactory.newDefaultInstance();
        factory.newSAXParser().parse(new InputSource(new StringReader(BENIGN_XML)), new DefaultHandler());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    // The newNSInstance family (Java 13) falls back to enabling namespace awareness on the corresponding newInstance lookup, the behavior the JAXP methods
    // are specified to have, so the non-default variants work on every platform including Android.
    @Test
    @Tag("dom")
    void newNSInstanceDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newNSInstance();
        assertTrue(factory.isNamespaceAware());
        assertNotNull(factory.newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML))).getDocumentElement());
        if (!AttackTestSupport.IS_ANDROID) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("dom")
    void newDefaultNSInstanceDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        if (AttackTestSupport.IS_ANDROID) {
            assertThrows(FactoryConfigurationError.class, SecureDocumentBuilderFactory::newDefaultNSInstance);
            return;
        }
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newDefaultNSInstance();
        assertTrue(factory.isNamespaceAware());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    @Tag("sax")
    void newNSInstanceSAXParserFactoryIsNamespaceAware() throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newNSInstance();
        assertTrue(factory.isNamespaceAware());
        factory.newSAXParser().parse(new InputSource(new StringReader(BENIGN_XML)), new DefaultHandler());
        if (!AttackTestSupport.IS_ANDROID) {
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }

    @Test
    @Tag("sax")
    void newDefaultNSInstanceSAXParserFactoryIsNamespaceAware() throws Exception {
        if (AttackTestSupport.IS_ANDROID) {
            assertThrows(FactoryConfigurationError.class, SecureSAXParserFactory::newDefaultNSInstance);
            return;
        }
        final SAXParserFactory factory = SecureSAXParserFactory.newDefaultNSInstance();
        assertTrue(factory.isNamespaceAware());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameNSDocumentBuilderFactoryIsNamespaceAware() throws Exception {
        final Class<?> impl = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newNSInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.isNamespaceAware());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameNSSAXParserFactoryIsNamespaceAware() throws Exception {
        final Class<?> impl = SAXParserFactory.newInstance().getClass();
        final SAXParserFactory factory = SecureSAXParserFactory.newNSInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.isNamespaceAware());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newDefaultInstanceSchemaFactoryIsHardened() throws Exception {
        final SchemaFactory factory = HardeningSchemaFactory.newDefaultInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newDefaultInstanceTransformerFactoryIsHardened() {
        final TransformerFactory factory = HardeningTransformerFactory.newDefaultInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newDefaultFactoryXMLInputFactoryIsHardened() {
        final XMLInputFactory factory = HardeningXMLInputFactory.newDefaultFactory();
        assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    @Test
    void newDefaultInstanceXPathFactoryIsHardened() throws Exception {
        final XPathFactory factory = HardeningXPathFactory.newDefaultInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }
}
