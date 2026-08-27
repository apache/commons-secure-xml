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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Public-API smoke tests for the {@code Safe*} factory classes.
 *
 * <p>Attack tests live in sibling test classes; this file only verifies that new factories are returned, that they report safe defaults, and that
 * a benign document still parses successfully.</p>
 */
class SafeFactoriesTest {

    private static final String BENIGN_XML =
            "<?xml version=\"1.0\"?>\n<root><child>hello</child></root>\n";

    @Test
    void benignDocumentParses() throws Exception {
        final Document doc = SafeDocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML)));
        assertNotNull(doc);
        assertNotNull(doc.getDocumentElement());
    }

    @Test
    void newDocumentBuilderFactoryDisablesXIncludeAndValidation() {
        final DocumentBuilderFactory factory = SafeDocumentBuilderFactory.newInstance();
        assertFalse(factory.isXIncludeAware(), "XInclude must be off by default");
        assertFalse(factory.isValidating(), "Validation must be off by default");
    }

    @Test
    void newDocumentBuilderFactoryEnablesSecureProcessing() throws Exception {
        final DocumentBuilderFactory factory = SafeDocumentBuilderFactory.newInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING),
                "FEATURE_SECURE_PROCESSING must be on");
    }

    @Test
    void newDocumentBuilderFactoryReturnsFreshInstance() {
        final DocumentBuilderFactory a = SafeDocumentBuilderFactory.newInstance();
        final DocumentBuilderFactory b = SafeDocumentBuilderFactory.newInstance();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    void newSAXParserFactoryReturnsFreshInstance() {
        final SAXParserFactory a = SafeSAXParserFactory.newInstance();
        final SAXParserFactory b = SafeSAXParserFactory.newInstance();
        assertNotSame(a, b);
        assertFalse(a.isValidating());
        assertFalse(a.isXIncludeAware());
    }

    @Test
    void newSchemaFactoryReturnsFreshInstance() throws Exception {
        final SchemaFactory a = SafeSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final SchemaFactory b = SafeSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void newTransformerFactoryReturnsFreshInstance() {
        final TransformerFactory a = SafeTransformerFactory.newInstance();
        final TransformerFactory b = SafeTransformerFactory.newInstance();
        assertNotSame(a, b);
    }

    @Test
    void newXMLInputFactoryReturnsFreshInstance() {
        final XMLInputFactory a = SafeXMLInputFactory.newFactory();
        final XMLInputFactory b = SafeXMLInputFactory.newFactory();
        assertNotSame(a, b);
        assertEquals(Boolean.TRUE, a.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(Boolean.FALSE, a.getProperty(XMLInputFactory.IS_VALIDATING));
    }

    @Test
    void newXPathFactoryReturnsFreshInstance() throws Exception {
        final XPathFactory a = SafeXPathFactory.newInstance();
        final XPathFactory b = SafeXPathFactory.newInstance();
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    // The explicit-class-name tests discover the runtime default implementation through the raw JAXP factory,
    // so they stay portable across the JAXP implementations of the surefire matrix.
    @Test
    void explicitClassNameDocumentBuilderFactoryIsHardened() throws Exception {
        final Class<?> impl = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SafeDocumentBuilderFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameSAXParserFactoryIsHardened() throws Exception {
        final Class<?> impl = SAXParserFactory.newInstance().getClass();
        final SAXParserFactory factory = SafeSAXParserFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameSchemaFactoryIsHardened() throws Exception {
        final Class<?> impl = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass();
        final SchemaFactory factory = SafeSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI, impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameTransformerFactoryIsHardened() {
        final Class<?> impl = TransformerFactory.newInstance().getClass();
        final TransformerFactory factory = SafeTransformerFactory.newInstance(impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void explicitClassNameXPathFactoryIsHardened() throws Exception {
        final Class<?> impl = XPathFactory.newInstance().getClass();
        final XPathFactory factory = SafeXPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, impl.getName(), impl.getClassLoader());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void factoryIdXMLInputFactoryIsHardened() {
        final String factoryId = "org.apache.commons.xml.test.staxFactory";
        // XMLInputFactory.newInstance, not newFactory: Android's StAX API predates newFactory, and this file also compiles against android.jar.
        System.setProperty(factoryId, XMLInputFactory.newInstance().getClass().getName());
        try {
            final XMLInputFactory factory = SafeXMLInputFactory.newFactory(factoryId, getClass().getClassLoader());
            assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
        } finally {
            System.clearProperty(factoryId);
        }
    }

    @Test
    void unknownFactoryClassNameThrows() {
        assertThrows(FactoryConfigurationError.class, () -> SafeDocumentBuilderFactory.newInstance("no.such.FactoryClass", null));
    }

}
