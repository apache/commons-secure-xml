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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

@Tag("schema")
class SecureSchemaFactoryTest {

    private static final class PropertySchemaFactory extends SchemaFactory {

        private final SchemaFactory delegate = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        @Override
        public ErrorHandler getErrorHandler() {
            return delegate.getErrorHandler();
        }

        @Override
        public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getFeature(name);
        }

        @Override
        public Object getProperty(final String name) {
            return "value";
        }

        @Override
        public LSResourceResolver getResourceResolver() {
            return delegate.getResourceResolver();
        }

        @Override
        public boolean isSchemaLanguageSupported(final String language) {
            return delegate.isSchemaLanguageSupported(language);
        }

        @Override
        public Schema newSchema() throws SAXException {
            return delegate.newSchema();
        }

        @Override
        public Schema newSchema(final Source[] sources) throws SAXException {
            return delegate.newSchema(sources);
        }

        @Override
        public void setErrorHandler(final ErrorHandler handler) {
            delegate.setErrorHandler(handler);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setFeature(name, value);
        }

        @Override
        public void setProperty(final String name, final Object value) {
        }

        @Override
        public void setResourceResolver(final LSResourceResolver resolver) {
            delegate.setResourceResolver(resolver);
        }
    }

    @Test
    void createsSecureSchemas() throws Exception {
        assertInstanceOf(SecureSchema.class, SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema());
        assertInstanceOf(SecureSchema.class, SecureSchemaFactory.newDefaultInstance().newSchema());
    }

    @Test
    void forwardsSchemaFactoryConfigurationAndAccessors() throws Exception {
        final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final DefaultHandler errorHandler = new DefaultHandler();
        final LSResourceResolver resolver = (type, namespace, publicId, systemId, base) -> null;
        factory.setErrorHandler(errorHandler);
        factory.setResourceResolver(resolver);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(TestConstants.EXTERNAL_SCHEMA_LOCATION, "urn:example schema.xsd");
        assertEquals("urn:example schema.xsd", factory.getProperty(TestConstants.EXTERNAL_SCHEMA_LOCATION));
        assertSame(errorHandler, factory.getErrorHandler());
        assertSame(resolver, factory.getResourceResolver());
        assertTrue(factory.isSchemaLanguageSupported(XMLConstants.W3C_XML_SCHEMA_NS_URI));
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertThrows(SAXNotRecognizedException.class, () -> factory.getProperty("foo"));
    }

    @Test
    void returnsPropertiesFromTheDelegate() throws Exception {
        final SchemaFactory factory = SecureSchemaFactory.secure(new PropertySchemaFactory());
        assertEquals("value", factory.getProperty("test"));
    }
}
