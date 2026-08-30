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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Tag("sax")
class SecureSAXParserFactoryTest {

    @Test
    void createsSecureParsersFromEveryStaticEntryPoint() throws Exception {
        assertNotNull(SecureSAXParserFactory.newInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newDefaultInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newNSInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newDefaultNSInstance().newSAXParser());
    }

    @Test
    void forwardsFactoryConfigurationAndCreatesNamespaceAwareParsers() throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        if (AttackTestSupport.SAX_SUPPORTS_XINCLUDE) {
            factory.setXIncludeAware(false);
            assertFalse(factory.isXIncludeAware());
        }
        if (AttackTestSupport.SAX_SUPPORTS_SCHEMA) {
            factory.setSchema(null);
            assertNull(factory.getSchema());
        }
        if (AttackTestSupport.SAX_SUPPORTS_SECURE_PROCESSING) {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
        assertTrue(factory.isNamespaceAware());
        assertFalse(factory.isValidating());
        assertInstanceOf(SecureSAXParser.class, factory.newSAXParser());
    }

    @Test
    void respectsDefaultParserSelectionAndLeavesReadersSecureOnlyOnce() throws Exception {
        final String factoryId = "SAXParserFactory";
        final String previous = System.getProperty(factoryId);
        try {
            System.setProperty(factoryId, SAXParserFactory.newInstance().getClass().getName());
            assertTrue(SecureSAXParserFactory.newNSInstance(false).isNamespaceAware());
        } finally {
            if (previous == null) {
                System.clearProperty(factoryId);
            } else {
                System.setProperty(factoryId, previous);
            }
        }
        assertTrue(SecureSAXParserFactory.newNSInstance(true).isNamespaceAware());
        final XMLReader reader = SecureSAXParserFactory.newXMLReader(false);
        assertSame(reader, SecureSAXParserFactory.secure(reader));
    }

    @Test
    void securesOnlySourcesThatNeedAReader() throws Exception {
        final StreamSource stream = new StreamSource(new StringReader("<root/>"));
        final Source securedStream = SecureSAXParserFactory.secure(stream, false);
        assertInstanceOf(SAXSource.class, securedStream);
        assertInstanceOf(SecureXMLReader.class, ((SAXSource) securedStream).getXMLReader());
        final SAXSource readerless = new SAXSource(new InputSource(new StringReader("<root/>")));
        assertInstanceOf(SAXSource.class, SecureSAXParserFactory.secure(readerless, true));
        final SAXSource empty = new SAXSource();
        assertSame(empty, SecureSAXParserFactory.secure(empty, false));
        final DOMSource dom = new DOMSource();
        assertSame(dom, SecureSAXParserFactory.secure(dom, false));
        final SAXSource suppliedReader = new SAXSource(SecureSAXParserFactory.newXMLReader(false), new InputSource());
        assertSame(suppliedReader, SecureSAXParserFactory.secure(suppliedReader, false));
    }
}
