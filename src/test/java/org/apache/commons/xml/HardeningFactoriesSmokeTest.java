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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Public-API smoke tests for {@link org.apache.commons.xml}.
 * <p>
 * Attack tests live in the {@code attacks} sub-package; this file only verifies that new factories are returned, that they report safe defaults, and that a
 * benign document still parses successfully.
 * </p>
 */
class HardeningFactoriesSmokeTest {

    private static final String BENIGN_XML = "<?xml version=\"1.0\"?>\n<root><child>hello</child></root>\n";

    @Test
    void benignDocumentParses() throws Exception {
        final Document doc = HardeningDocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(BENIGN_XML)));
        assertNotNull(doc);
        assertNotNull(doc.getDocumentElement());
    }

    @Test
    void newDocumentBuilderFactoryDisablesXIncludeAndValidation() {
        final DocumentBuilderFactory factory = HardeningDocumentBuilderFactory.newInstance();
        assertFalse(factory.isXIncludeAware(), "XInclude must be off by default");
        assertFalse(factory.isValidating(), "Validation must be off by default");
    }

    @Test
    void newDocumentBuilderFactoryEnablesSecureProcessing() throws Exception {
        final DocumentBuilderFactory factory = HardeningDocumentBuilderFactory.newInstance();
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING), "FEATURE_SECURE_PROCESSING must be on");
    }

    @Test
    void newDocumentBuilderFactoryReturnsFreshInstance() {
        final DocumentBuilderFactory a = HardeningDocumentBuilderFactory.newInstance();
        final DocumentBuilderFactory b = HardeningDocumentBuilderFactory.newInstance();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    void newSAXParserFactoryReturnsFreshInstance() {
        final SAXParserFactory a = HardeningSAXParserFactory.newInstance();
        final SAXParserFactory b = HardeningSAXParserFactory.newInstance();
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
        final XPathFactory a = HardeningXPathFactory.newXPathFactory();
        final XPathFactory b = HardeningXPathFactory.newXPathFactory();
        assertNotSame(a, b);
        assertTrue(a.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }
}
