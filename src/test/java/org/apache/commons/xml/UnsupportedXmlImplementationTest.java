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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Verifies that an implementation which does not honor the required secure-processing feature surfaces {@link IllegalStateException} with a message naming the
 * class.
 */
class UnsupportedXmlImplementationTest {

    /**
     * A stand-in factory that rejects the secure-processing feature, like a JAXP implementation that does not recognize it.
     */
    public static final class FakeDocumentBuilderFactory extends DocumentBuilderFactory {

        @Override
        public Object getAttribute(final String name) {
            return null;
        }

        @Override
        public boolean getFeature(final String name) {
            return false;
        }

        @Override
        public DocumentBuilder newDocumentBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            // no-op
        }

        @Override
        public void setFeature(final String name, final boolean value) throws ParserConfigurationException {
            throw new ParserConfigurationException("feature not recognized: " + name);
        }
    }

    /**
     * A stand-in SAX factory that rejects the secure-processing feature, like a JAXP implementation that does not recognize it.
     */
    public static final class FakeSAXParserFactory extends SAXParserFactory {

        @Override
        public boolean getFeature(final String name) throws SAXNotSupportedException {
            throw new SAXNotSupportedException("feature not recognized: " + name);
        }

        @Override
        public SAXParser newSAXParser() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException {
            throw new SAXNotRecognizedException("feature not recognized: " + name);
        }
    }

    @Test
    void hardenRejectsUnsecurableFactory() {
        final IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> SecureDocumentBuilderFactory.harden(new FakeDocumentBuilderFactory()));
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains(FakeDocumentBuilderFactory.class.getName()),
                "Exception message must name the unsupported class: " + thrown.getMessage());
    }

    @Test
    void hardenRejectsUnsecurableSaxFactory() {
        final IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> SecureSAXParserFactory.harden(new FakeSAXParserFactory()));
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains(FakeSAXParserFactory.class.getName()),
                "Exception message must name the unsupported class: " + thrown.getMessage());
    }
}
