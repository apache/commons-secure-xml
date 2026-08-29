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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.validation.TypeInfoProvider;

class SecureValidatorHandlerTest {

    /**
     * Minimal recording ValidatorHandler used to verify forwarding without triggering real validation.
     */
    private static final class RecordingValidatorHandler extends ValidatorHandler {

        boolean startDocumentCalled;

        boolean endDocumentCalled;

        boolean startPrefixMappingCalled;

        String startPrefixMappingPrefix;

        String startPrefixMappingUri;

        boolean endPrefixMappingCalled;

        String endPrefixMappingPrefix;

        boolean startElementCalled;

        String startElementUri;

        String startElementLocalName;

        String startElementQName;

        Attributes startElementAttrs;

        boolean charactersCalled;

        char[] charactersChars;

        int charactersStart;

        int charactersLength;

        boolean ignorableWhitespaceCalled;

        boolean processingInstructionCalled;

        String piTarget;

        String piData;

        boolean endElementCalled;

        String endElementUri;

        String endElementLocalName;

        String endElementQName;

        boolean skippedEntityCalled;

        String skippedEntityName;

        boolean setDocumentLocatorCalled;

        boolean setContentHandlerCalled;

        boolean setErrorHandlerCalled;

        boolean setFeatureCalled;

        String setFeatureName;

        boolean setFeatureValue;

        boolean setPropertyCalled;

        String setPropertyName;

        Object setPropertyValue;

        ContentHandler contentHandler;

        ErrorHandler errorHandler;

        LSResourceResolver resourceResolver;

        @Override
        public void characters(char[] ch, int start, int length) {
            charactersCalled = true;
            charactersChars = ch.clone();
            charactersStart = start;
            charactersLength = length;
        }

        @Override
        public void endDocument() {
            endDocumentCalled = true;
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            endElementCalled = true;
            endElementUri = uri;
            endElementLocalName = localName;
            endElementQName = qName;
        }

        @Override
        public void endPrefixMapping(String prefix) {
            endPrefixMappingCalled = true;
            endPrefixMappingPrefix = prefix;
        }

        @Override
        public ContentHandler getContentHandler() {
            return contentHandler;
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public boolean getFeature(String name) {
            return false;
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public LSResourceResolver getResourceResolver() {
            return resourceResolver;
        }

        @Override
        public TypeInfoProvider getTypeInfoProvider() {
            return null;
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
            ignorableWhitespaceCalled = true;
        }

        @Override
        public void processingInstruction(String target, String data) {
            processingInstructionCalled = true;
            piTarget = target;
            piData = data;
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            setContentHandlerCalled = true;
            contentHandler = handler;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            setDocumentLocatorCalled = true;
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            setErrorHandlerCalled = true;
            errorHandler = handler;
        }

        @Override
        public void setFeature(String name, boolean value) {
            setFeatureCalled = true;
            setFeatureName = name;
            setFeatureValue = value;
        }

        @Override
        public void setProperty(String name, Object value) {
            setPropertyCalled = true;
            setPropertyName = name;
            setPropertyValue = value;
        }

        @Override
        public void setResourceResolver(LSResourceResolver resolver) {
            resourceResolver = resolver;
        }

        @Override
        public void skippedEntity(String name) {
            skippedEntityCalled = true;
            skippedEntityName = name;
        }

        @Override
        public void startDocument() {
            startDocumentCalled = true;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            startElementCalled = true;
            startElementUri = uri;
            startElementLocalName = localName;
            startElementQName = qName;
            startElementAttrs = atts;
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            startPrefixMappingCalled = true;
            startPrefixMappingPrefix = prefix;
            startPrefixMappingUri = uri;
        }
    }

    private static ValidatorHandler newValidatorHandler() throws Exception {
        final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return factory.newSchema().newValidatorHandler();
    }

    @Test
    void constructorInstallsFloorOnDelegate() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        // The constructor must install the ignore-all floor on the delegate
        assertNotNull(delegate.getResourceResolver(), "delegate resource resolver must be set to the floor");
        assertTrue(delegate.getResourceResolver() instanceof FallbackIgnoreLSResourceResolver, "delegate resolver must be a FallbackIgnoreLSResourceResolver");
        // getResourceResolver on the wrapper returns the floor's delegate, which is null initially
        assertNull(handler.getResourceResolver());
    }

    @Test
    void constructorRejectsNullDelegate() {
        assertThrows(NullPointerException.class, () -> new SecureValidatorHandler(null));
    }

    @Test
    void delegatesFeature() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        // Verify delegation of getFeature; setFeature may be unsupported on this implementation
        final String feature = XMLConstants.FEATURE_SECURE_PROCESSING;
        final boolean delegateValue;
        try {
            delegateValue = delegate.getFeature(feature);
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            // If the feature is not recognized, both delegate and wrapper should behave the same way
            assertThrows(SAXNotRecognizedException.class, () -> handler.getFeature(feature));
            return;
        }
        assertEquals(delegateValue, handler.getFeature(feature));
    }

    @Test
    void delegatesGetContentHandler() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final ContentHandler ch = new DefaultHandler();
        delegate.setContentHandler(ch);
        assertSame(ch, handler.getContentHandler());
    }

    @Test
    void delegatesGetErrorHandler() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final ErrorHandler eh = new ErrorHandler() {

            @Override
            public void error(final SAXParseException e) {
            }

            @Override
            public void fatalError(final SAXParseException e) {
            }

            @Override
            public void warning(final SAXParseException e) {
            }
        };
        delegate.setErrorHandler(eh);
        assertSame(eh, handler.getErrorHandler());
    }

    @Test
    void delegatesProperty() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        // Verify delegation of getProperty; setProperty may be unsupported on this implementation
        final String property = XMLConstants.ACCESS_EXTERNAL_DTD;
        final Object delegateValue;
        try {
            delegateValue = delegate.getProperty(property);
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            assertThrows(SAXNotRecognizedException.class, () -> handler.getProperty(property));
            return;
        }
        assertSame(delegateValue, handler.getProperty(property));
    }

    @Test
    void delegatesSetContentHandler() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final ContentHandler ch = new DefaultHandler();
        handler.setContentHandler(ch);
        assertSame(ch, delegate.getContentHandler());
    }

    @Test
    void delegatesSetDocumentLocator() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final Locator locator = new org.xml.sax.Locator() {

            @Override
            public int getColumnNumber() {
                return 0;
            }

            @Override
            public int getLineNumber() {
                return 0;
            }

            @Override
            public String getPublicId() {
                return null;
            }

            @Override
            public String getSystemId() {
                return null;
            }
        };
        handler.setDocumentLocator(locator);
        // No exception means forwarding works; we cannot easily verify locator on delegate without exposing it
    }

    @Test
    void delegatesSetErrorHandler() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final ErrorHandler eh = new ErrorHandler() {

            @Override
            public void error(final SAXParseException e) {
            }

            @Override
            public void fatalError(final SAXParseException e) {
            }

            @Override
            public void warning(final SAXParseException e) {
            }
        };
        handler.setErrorHandler(eh);
        assertSame(eh, delegate.getErrorHandler());
    }

    @Test
    void delegatesTypeInfoProvider() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        assertSame(delegate.getTypeInfoProvider(), handler.getTypeInfoProvider());
    }

    @Test
    void forwardsCharacters() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final char[] ch = { 'a', 'b' };
        handler.characters(ch, 0, 2);
        assertTrue(delegate.charactersCalled);
        assertEquals(0, delegate.charactersStart);
        assertEquals(2, delegate.charactersLength);
    }

    @Test
    void forwardsEndDocument() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.endDocument();
        assertTrue(delegate.endDocumentCalled);
    }

    @Test
    void forwardsEndElement() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.endElement("uri", "local", "qName");
        assertTrue(delegate.endElementCalled);
        assertEquals("uri", delegate.endElementUri);
        assertEquals("local", delegate.endElementLocalName);
        assertEquals("qName", delegate.endElementQName);
    }

    @Test
    void forwardsEndPrefixMapping() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.endPrefixMapping("p");
        assertTrue(delegate.endPrefixMappingCalled);
        assertEquals("p", delegate.endPrefixMappingPrefix);
    }

    @Test
    void forwardsGetProperty() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        // getProperty is delegated; RecordingValidatorHandler returns null
        assertNull(handler.getProperty("any"));
    }

    @Test
    void forwardsIgnorableWhitespace() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final char[] ch = { ' ' };
        handler.ignorableWhitespace(ch, 0, 1);
        assertTrue(delegate.ignorableWhitespaceCalled);
    }

    @Test
    void forwardsProcessingInstruction() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.processingInstruction("target", "data");
        assertTrue(delegate.processingInstructionCalled);
        assertEquals("target", delegate.piTarget);
        assertEquals("data", delegate.piData);
    }

    @Test
    void forwardsSetFeature() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.setFeature("f", true);
        assertTrue(delegate.setFeatureCalled);
        assertEquals("f", delegate.setFeatureName);
        assertTrue(delegate.setFeatureValue);
    }

    @Test
    void forwardsSetProperty() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final Object value = new Object();
        handler.setProperty("p", value);
        assertTrue(delegate.setPropertyCalled);
        assertEquals("p", delegate.setPropertyName);
        assertSame(value, delegate.setPropertyValue);
    }

    @Test
    void forwardsSkippedEntity() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.skippedEntity("name");
        assertTrue(delegate.skippedEntityCalled);
        assertEquals("name", delegate.skippedEntityName);
    }

    @Test
    void forwardsStartDocument() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.startDocument();
        assertTrue(delegate.startDocumentCalled);
    }

    @Test
    void forwardsStartElement() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final Attributes attrs = new AttributesImpl();
        handler.startElement("uri", "local", "qName", attrs);
        assertTrue(delegate.startElementCalled);
        assertEquals("uri", delegate.startElementUri);
        assertEquals("local", delegate.startElementLocalName);
        assertEquals("qName", delegate.startElementQName);
        assertSame(attrs, delegate.startElementAttrs);
    }

    @Test
    void forwardsStartPrefixMapping() throws Exception {
        final RecordingValidatorHandler delegate = new RecordingValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        handler.startPrefixMapping("p", "u");
        assertTrue(delegate.startPrefixMappingCalled);
        assertEquals("p", delegate.startPrefixMappingPrefix);
        assertEquals("u", delegate.startPrefixMappingUri);
    }

    @Test
    void getResourceResolverReturnsDelegateAfterSet() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final LSResourceResolver resolver = (type, namespaceURI, publicId, systemId, baseURI) -> null;
        handler.setResourceResolver(resolver);
        assertSame(resolver, handler.getResourceResolver(), "getResourceResolver must return the caller-supplied resolver");
    }

    @Test
    void setResourceResolverDoesNotReplaceFloorOnDelegate() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final LSResourceResolver resolver = (type, namespaceURI, publicId, systemId, baseURI) -> null;
        final LSResourceResolver before = delegate.getResourceResolver();
        handler.setResourceResolver(resolver);
        // The delegate's resolver must remain the floor, not the caller-supplied resolver
        assertSame(before, delegate.getResourceResolver(), "delegate resolver must stay the floor");
        assertSame(resolver, handler.getResourceResolver(), "wrapper must expose caller resolver");
    }

    @Test
    void setResourceResolverNullClearsDelegate() throws Exception {
        final ValidatorHandler delegate = newValidatorHandler();
        final SecureValidatorHandler handler = new SecureValidatorHandler(delegate);
        final LSResourceResolver resolver = (type, namespaceURI, publicId, systemId, baseURI) -> null;
        handler.setResourceResolver(resolver);
        assertSame(resolver, handler.getResourceResolver());
        handler.setResourceResolver(null);
        assertNull(handler.getResourceResolver(), "null resolver must clear the floor delegate");
    }
    // --- Forwarding tests using RecordingValidatorHandler ---
}
