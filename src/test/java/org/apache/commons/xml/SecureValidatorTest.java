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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

class SecureValidatorTest {

    private static final class PropertyValidator extends javax.xml.validation.Validator {

        private final javax.xml.validation.Validator delegate;

        PropertyValidator() throws org.xml.sax.SAXException {
            delegate = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema().newValidator();
        }

        @Override
        public org.xml.sax.ErrorHandler getErrorHandler() {
            return delegate.getErrorHandler();
        }

        @Override
        public boolean getFeature(final String name) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException {
            return delegate.getFeature(name);
        }

        @Override
        public Object getProperty(final String name) {
            return "value";
        }

        @Override
        public org.w3c.dom.ls.LSResourceResolver getResourceResolver() {
            return delegate.getResourceResolver();
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public void setErrorHandler(final org.xml.sax.ErrorHandler errorHandler) {
            delegate.setErrorHandler(errorHandler);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException {
            delegate.setFeature(name, value);
        }

        @Override
        public void setProperty(final String name, final Object object) {
            // This test double only needs to supply a property value.
        }

        @Override
        public void setResourceResolver(final org.w3c.dom.ls.LSResourceResolver resourceResolver) {
            delegate.setResourceResolver(resourceResolver);
        }

        @Override
        public void validate(final javax.xml.transform.Source source, final javax.xml.transform.Result result)
                throws org.xml.sax.SAXException, java.io.IOException {
            delegate.validate(source, result);
        }
    }

    @Test
    void getsPropertiesFromTheDelegate() throws Exception {
        assertEquals("value", new SecureValidator(new PropertyValidator(), false).getProperty("property"));
    }

    @Test
    void preservesNonRemovableResolverFloorAndForwardsConfiguration() throws Exception {
        final SecureValidator validator = new SecureValidator(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema().newValidator(), false);
        final DefaultHandler errorHandler = new DefaultHandler();
        final org.w3c.dom.ls.LSResourceResolver resolver = (type, namespace, publicId, systemId, base) -> null;
        validator.setErrorHandler(errorHandler);
        assertSame(errorHandler, validator.getErrorHandler());
        assertNull(validator.getResourceResolver());
        validator.setResourceResolver(resolver);
        validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        assertSame(resolver, validator.getResourceResolver());
        assertTrue(validator.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertThrows(org.xml.sax.SAXNotRecognizedException.class, () -> validator.getProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA));
        validator.reset();
        assertNull(validator.getResourceResolver());
    }
}
