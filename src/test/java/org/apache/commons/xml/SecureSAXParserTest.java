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
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;

class SecureSAXParserTest {

    private static final class ParserSecureReader extends SecureXMLReader implements org.xml.sax.Parser {

        ParserSecureReader(final org.xml.sax.XMLReader reader) {
            super(reader);
        }

        @Override
        public void setDocumentHandler(final org.xml.sax.DocumentHandler handler) {
        }

        @Override
        public void setLocale(final java.util.Locale locale) {
        }
    }

    private static final class ReaderSAXParser extends javax.xml.parsers.SAXParser {

        private final org.xml.sax.XMLReader reader;

        ReaderSAXParser(final org.xml.sax.XMLReader reader) {
            this.reader = reader;
        }

        @Override
        public org.xml.sax.Parser getParser() {
            return (org.xml.sax.Parser) reader;
        }

        @Override
        public Object getProperty(final String name) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException {
            return reader.getProperty(name);
        }

        @Override
        public javax.xml.validation.Schema getSchema() {
            return null;
        }

        @Override
        public org.xml.sax.XMLReader getXMLReader() {
            return reader;
        }

        @Override
        public boolean isNamespaceAware() {
            return false;
        }

        @Override
        public boolean isValidating() {
            return false;
        }

        @Override
        public boolean isXIncludeAware() {
            return false;
        }

        @Override
        public void reset() {
        }

        @Override
        public void setProperty(final String name, final Object value) throws org.xml.sax.SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException {
            reader.setProperty(name, value);
        }
    }

    @Test
    void cachesSecureViewsThenRecreatesThemAfterReset() throws Exception {
        final SecureSAXParser parser = new SecureSAXParser(SAXParserFactory.newInstance().newSAXParser());
        final org.xml.sax.XMLReader firstReader = parser.getXMLReader();
        final org.xml.sax.Parser firstParser = parser.getParser();
        org.junit.jupiter.api.Assertions.assertSame(firstReader, parser.getXMLReader());
        org.junit.jupiter.api.Assertions.assertSame(firstParser, parser.getParser());
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", null);
        org.junit.jupiter.api.Assertions.assertNull(parser.getProperty("http://xml.org/sax/properties/lexical-handler"));
        parser.reset();
        org.junit.jupiter.api.Assertions.assertNotSame(firstReader, parser.getXMLReader());
        org.junit.jupiter.api.Assertions.assertNotSame(firstParser, parser.getParser());
    }

    @Test
    void exposesSecureParserViewsAndState() throws Exception {
        final SecureSAXParser parser = new SecureSAXParser(SAXParserFactory.newInstance().newSAXParser());
        assertNotNull(parser.getXMLReader());
        assertNotNull(parser.getParser());
        parser.getSchema();
        parser.isNamespaceAware();
        parser.isValidating();
        parser.isXIncludeAware();
        parser.reset();
    }

    @Test
    void reusesAReaderThatAlreadyImplementsSax1Parser() throws Exception {
        final ParserSecureReader reader = new ParserSecureReader(SAXParserFactory.newInstance().newSAXParser().getXMLReader());
        final SecureSAXParser parser = new SecureSAXParser(new ReaderSAXParser(reader));
        org.junit.jupiter.api.Assertions.assertSame(reader, parser.getParser());
    }
}
