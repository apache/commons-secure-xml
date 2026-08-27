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

import static org.apache.commons.xml.AttackTestSupport.LEAKED_MARKER;
import static org.apache.commons.xml.AttackTestSupport.resourceUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Checks that a hardened {@link SAXParserFactory} performing JAXP 1.2 XSD validation does not fetch an external schema named by an
 * {@code xsi:noNamespaceSchemaLocation} hint in the instance document.
 *
 * <p>This is the SAX counterpart of {@link SchemaLocationDomTest}. The instance is empty {@code <root/>}; the referenced schema declares a default {@code leak}
 * attribute carrying {@link AttackTestSupport#LEAKED_MARKER}. A parser that fetches the schema augments the element's attributes with that default (the
 * permissive control observes it in {@link DefaultHandler#startElement}), while a hardened parser resolves the schema reference to empty content instead. Either
 * the empty schema makes the validating parse fail, or the parse completes but the default is never augmented onto the element; either way the marker is never
 * observed.</p>
 *
 * <p>The test runs only where the implementation supports JAXP 1.2 schema-language XSD validation (the stock JDK and external Xerces do; Android does not), so it
 * skips on parsers without it.</p>
 */
@Tag("sax")
class SchemaLocationSaxTest {

    /** Captures the root element's schema-defaulted {@code leak} attribute, the SAX-visible signal that the external schema was fetched. */
    private static final class LeakCapturingHandler extends DefaultHandler {
        private String leak;

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("root".equals(localName) || "root".equals(qName)) {
                leak = attributes.getValue("leak");
            }
        }
    }

    /** JAXP 1.2 property selecting the schema language used by {@link SAXParserFactory#setValidating(boolean)}. */
    private static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    private static final String INSTANCE = "schema-location-instance.xml";

    private static SAXParser newValidatingParser(final SAXParserFactory factory) throws Exception {
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        final SAXParser parser = factory.newSAXParser();
        parser.setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return parser;
    }

    private static void parse(final SAXParser parser, final DefaultHandler handler) throws Exception {
        // Drive the XMLReader directly rather than SAXParser.parse(InputSource, DefaultHandler): the latter calls reader.setEntityResolver(handler), which would
        // clobber the hardened ignore-all resolver that external Xerces relies on to block the schemaLocation fetch. Reuse AttackTestSupport's shared strict
        // reporter as the error handler so a blocked fetch surfaces as a thrown exception rather than a silent recovery.
        final XMLReader reader = parser.getXMLReader();
        reader.setContentHandler(handler);
        AttackTestSupport.strictXMLReader(reader);
        reader.parse(new InputSource(resourceUrl(INSTANCE).toString()));
    }

    private static boolean supportsSchemaLanguage() {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.newSAXParser().setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    @Test
    void hardenedDoesNotFetchExternalSchema() throws Exception {
        assumeTrue(supportsSchemaLanguage(), "parser does not support JAXP 1.2 schema-language XSD validation");
        final SAXParser parser = newValidatingParser(SafeSAXParserFactory.newInstance());
        // The schemaLocation reference resolves to empty rather than being fetched. Either the empty schema fails the validating parse (acceptable), or the
        // parse completes but the schema's default leak attribute is never augmented onto the element. Either way the marker must not be observed.
        final LeakCapturingHandler handler = new LeakCapturingHandler();
        try {
            parse(parser, handler);
        } catch (final Exception blocked) {
            // Acceptable: the empty schema was rejected at parse time, so nothing was fetched or augmented.
        }
        assertNull(handler.leak, "Hardened parse must not augment the external schema's default attribute onto the element.");
    }

    @Test
    void unconfiguredFetchesExternalSchema() throws Exception {
        assumeTrue(supportsSchemaLanguage(), "parser does not support JAXP 1.2 schema-language XSD validation");
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        final SAXParser parser = newValidatingParser(factory);
        // Positive control: without hardening the external schema is fetched and its default attribute is augmented onto the root element.
        final LeakCapturingHandler handler = new LeakCapturingHandler();
        parse(parser, handler);
        assertEquals(LEAKED_MARKER, handler.leak,
                "Permissive parse should have fetched the external schema and augmented its default attribute onto the element.");
    }
}
