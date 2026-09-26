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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.lang.reflect.Field;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

@Tag("sax")
public class SecureSAXParserFactoryTest {

    /**
     * Test JAXP provider that delegates parser creation to a Mockito mock.
     */
    public static final class MockSAXParserFactory extends SAXParserFactory {

        private static SAXParserFactory delegate;

        @Override
        public boolean getFeature(final String name) {
            return false;
        }

        @Override
        public SAXParser newSAXParser() throws ParserConfigurationException, org.xml.sax.SAXException {
            return delegate.newSAXParser();
        }

        @Override
        public void setFeature(final String name, final boolean value) {
            // no-op
        }
    }

    /**
     * System property naming the {@link SAXParserFactory} implementation, the JVM's mechanism for reconfiguring the default parser.
     */
    private static final String FACTORY_ID = "javax.xml.parsers.SAXParserFactory";

    private static final String NAMESPACES_FEATURE = "http://xml.org/sax/features/namespaces";

    /**
     * Asserts every {@code newXMLReader} and {@code newNSXMLReader} helper on the given delegate throws {@link IllegalStateException} with the given cause.
     *
     * @param cause    The checked exception the delegate is stubbed to throw.
     * @param delegate The stubbed factory to route {@link MockSAXParserFactory} to.
     */
    private static void assertNewXmlReaderWraps(final Exception cause, final SAXParserFactory delegate) {
        MockSAXParserFactory.delegate = delegate;
        assertSame(cause, assertThrows(IllegalStateException.class, () -> SecureSAXParserFactory.newXMLReader(false)).getCause());
        assertSame(cause, assertThrows(IllegalStateException.class, () -> SecureSAXParserFactory.newXMLReader(null)).getCause());
        assertSame(cause, assertThrows(IllegalStateException.class, () -> SecureSAXParserFactory.newNSXMLReader(null)).getCause());
    }

    /**
     * Gets the implementation a secure factory delegates to, so the selection tests can observe which parser implementation a lookup picked.
     *
     * @param factory a secure factory returned by one of the {@code new*Instance} methods; never {@code null}.
     * @return The wrapped factory.
     */
    private static SAXParserFactory getDelegate(final SAXParserFactory factory) throws ReflectiveOperationException {
        final Field delegate = factory.getClass().getDeclaredField("delegate");
        delegate.setAccessible(true);
        return (SAXParserFactory) delegate.get(factory);
    }

    /**
     * Sets the {@value #FACTORY_ID} system property to select the implementation {@link SAXParserFactory#newInstance()} returns.
     *
     * @param factoryClassName The implementation class name to install, or {@code null} to clear the property and restore the platform lookup.
     * @return The previous property value, {@code null} if it was not set; pass it back here to restore the original lookup.
     */
    private static String setFactoryIdProperty(final String factoryClassName) {
        final String previous = System.getProperty(FACTORY_ID);
        if (factoryClassName == null) {
            System.clearProperty(FACTORY_ID);
        } else {
            System.setProperty(FACTORY_ID, factoryClassName);
        }
        return previous;
    }

    @Test
    void createsSecureParsersFromEveryStaticEntryPoint() throws Exception {
        assertNotNull(SecureSAXParserFactory.newInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newDefaultInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newNSInstance().newSAXParser());
        assertNotNull(SecureSAXParserFactory.newDefaultNSInstance().newSAXParser());
    }

    @Test
    void createsParsersAndReadersDirectly() throws Exception {
        final SAXParser parser = SecureSAXParserFactory.newSAXParser();
        final SAXParser nsParser = SecureSAXParserFactory.newNSSAXParser();
        assertInstanceOf(SecureSAXParser.class, parser);
        assertInstanceOf(SecureSAXParser.class, nsParser);
        assertFalse(parser.isNamespaceAware());
        assertTrue(nsParser.isNamespaceAware());
        final DefaultHandler handler = new DefaultHandler();
        final XMLReader reader = SecureSAXParserFactory.newXMLReader(handler);
        final XMLReader nsReader = SecureSAXParserFactory.newNSXMLReader(handler);
        assertInstanceOf(SecureXMLReader.class, reader);
        assertInstanceOf(SecureXMLReader.class, nsReader);
        assertSame(handler, reader.getContentHandler());
        assertSame(handler, nsReader.getContentHandler());
        assertFalse(reader.getFeature(NAMESPACES_FEATURE));
        assertTrue(nsReader.getFeature(NAMESPACES_FEATURE));
        assertNull(SecureSAXParserFactory.newXMLReader(null).getContentHandler());
        assertNull(SecureSAXParserFactory.newNSXMLReader(null).getContentHandler());
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
    void leavesReadersSecureOnlyOnce() {
        final XMLReader reader = SecureSAXParserFactory.newXMLReader(false);
        assertSame(reader, SecureSAXParserFactory.secure(reader));
    }

    @Test
    void newNSInstanceFollowsParserSelection() throws Exception {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: parser selection is pinned to the platform implementation");
        final Class<?> discovered = SAXParserFactory.newInstance().getClass();
        // no property: the JDK built-in default, unless an override is requested
        assertEquals(SecureSAXParserFactory.JDK_SAX_PARSER_FACTORY, getDelegate(SecureSAXParserFactory.newNSInstance(false)).getClass().getName());
        assertEquals(discovered, getDelegate(SecureSAXParserFactory.newNSInstance(true)).getClass());
        // the factory id property is the JDK's own default reconfiguration; both selections honor it
        final String previous = setFactoryIdProperty(discovered.getName());
        try {
            assertEquals(discovered, getDelegate(SecureSAXParserFactory.newNSInstance(false)).getClass());
            assertEquals(discovered, getDelegate(SecureSAXParserFactory.newNSInstance(true)).getClass());
        } finally {
            setFactoryIdProperty(previous);
        }
    }

    @Test
    void newXmlReaderFollowsParserSelection() throws Exception {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: parser selection is pinned to the platform implementation");
        final Class<?> discovered = SAXParserFactory.newInstance().newSAXParser().getXMLReader().getClass();
        assertEquals(discovered, ((SecureXMLReader) SecureSAXParserFactory.newXMLReader(true)).getDelegate().getClass());
        final Class<?> jdkReader =
                SAXParserFactory.newInstance(SecureSAXParserFactory.JDK_SAX_PARSER_FACTORY, null).newSAXParser().getXMLReader().getClass();
        assertEquals(jdkReader, ((SecureXMLReader) SecureSAXParserFactory.newXMLReader(false)).getDelegate().getClass());
        final String previous = setFactoryIdProperty(SAXParserFactory.newInstance().getClass().getName());
        try {
            assertEquals(discovered, ((SecureXMLReader) SecureSAXParserFactory.newXMLReader(false)).getDelegate().getClass());
        } finally {
            setFactoryIdProperty(previous);
        }
    }

    @Test
    // Mockito generates the mock classes and its plugin proxies at run time — impossible in a closed-world native image,
    // so the stubbed factories these error paths need cannot be built there.
    @DisabledInNativeImage
    void newXmlReaderWrapsDeclaredExceptions() throws Exception {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: parser selection is pinned to the platform implementation");
        final String previous = setFactoryIdProperty(MockSAXParserFactory.class.getName());
        try {
            // SAXParserFactory.newSAXParser() declares ParserConfigurationException and SAXException
            final ParserConfigurationException notConfigurable = new ParserConfigurationException("test");
            SAXParserFactory factory = mock(SAXParserFactory.class);
            when(factory.newSAXParser()).thenThrow(notConfigurable);
            assertNewXmlReaderWraps(notConfigurable, factory);
            assertSame(notConfigurable, assertThrows(IllegalStateException.class, SecureSAXParserFactory::newSAXParser).getCause());
            assertSame(notConfigurable, assertThrows(IllegalStateException.class, SecureSAXParserFactory::newNSSAXParser).getCause());
            final SAXException noParser = new SAXException("test");
            factory = mock(SAXParserFactory.class);
            when(factory.newSAXParser()).thenThrow(noParser);
            assertNewXmlReaderWraps(noParser, factory);
            assertSame(noParser, assertThrows(IllegalStateException.class, SecureSAXParserFactory::newSAXParser).getCause());
            // SAXParser.getXMLReader() declares SAXException
            final SAXException noReader = new SAXException("test");
            factory = mock(SAXParserFactory.class);
            final SAXParser parser = mock(SAXParser.class);
            when(factory.newSAXParser()).thenReturn(parser);
            when(parser.getXMLReader()).thenThrow(noReader);
            assertNewXmlReaderWraps(noReader, factory);
        } finally {
            setFactoryIdProperty(previous);
            MockSAXParserFactory.delegate = null;
        }
    }

    @Test
    void securesOnlySourcesThatNeedAReader() {
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
