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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.DocumentHandler;
import org.xml.sax.Parser;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

@Tag("sax")
class SecureSAXParserTest {

    private static final class ParserSecureReader extends SecureXMLReader implements Parser {

        ParserSecureReader(final XMLReader reader) {
            super(reader);
        }

        @Override
        public void setDocumentHandler(final DocumentHandler handler) {
        }

        @Override
        public void setLocale(final Locale locale) {
        }
    }

    private static final class ReaderSAXParser extends SAXParser {

        private final XMLReader reader;

        ReaderSAXParser(final XMLReader reader) {
            this.reader = reader;
        }

        @Override
        public Parser getParser() {
            return (Parser) reader;
        }

        @Override
        public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return reader.getProperty(name);
        }

        @Override
        public Schema getSchema() {
            return null;
        }

        @Override
        public XMLReader getXMLReader() {
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
        public void setProperty(final String name, final Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            reader.setProperty(name, value);
        }
    }

    @Test
    void cachesSecureViewsThenRecreatesThemAfterReset() throws Exception {
        final SecureSAXParser parser = new SecureSAXParser(SAXParserFactory.newInstance().newSAXParser());
        final XMLReader firstReader = parser.getXMLReader();
        final Parser firstParser = parser.getParser();
        assertSame(firstReader, parser.getXMLReader());
        assertSame(firstParser, parser.getParser());
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", null);
        assertNull(parser.getProperty("http://xml.org/sax/properties/lexical-handler"));
        parser.reset();
        assertNotSame(firstReader, parser.getXMLReader());
        assertNotSame(firstParser, parser.getParser());
    }

    @Test
    void exposesSecureParserViewsAndState() throws Exception {
        final SecureSAXParser parser = new SecureSAXParser(SAXParserFactory.newInstance().newSAXParser());
        assertNotNull(parser.getXMLReader());
        assertNotNull(parser.getParser());
        parser.isNamespaceAware();
        parser.isValidating();
        if (AttackTestSupport.SAX_SUPPORTS_SCHEMA) {
            parser.getSchema();
        }
        if (AttackTestSupport.SAX_SUPPORTS_XINCLUDE) {
            parser.isXIncludeAware();
        }
        if (AttackTestSupport.SAX_SUPPORTS_RESET) {
            parser.reset();
        }
    }

    @Test
    void reusesAReaderThatAlreadyImplementsSax1Parser() throws Exception {
        final ParserSecureReader reader = new ParserSecureReader(SAXParserFactory.newInstance().newSAXParser().getXMLReader());
        final SecureSAXParser parser = new SecureSAXParser(new ReaderSAXParser(reader));
        assertSame(reader, parser.getParser());
    }
}
