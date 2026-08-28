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

import static org.apache.commons.xml.AttackTestSupport.assertParseFails;
import static org.apache.commons.xml.AttackTestSupport.assertParseSucceeds;
import static org.apache.commons.xml.AttackTestSupport.inputSource;
import static org.apache.commons.xml.AttackTestSupport.resourceUrl;
import static org.apache.commons.xml.AttackTestSupport.strictDocumentBuilder;
import static org.apache.commons.xml.AttackTestSupport.strictXMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Checks that a hardened, schema-validating parser does not fetch a schema named only through a Xerces schema-location
 * property: {@code external-noNamespaceSchemaLocation} (no-namespace schema) and {@code external-schemaLocation}
 * (namespaced schema).
 *
 * <p>The fixtures declare the instance's root element, so a parser that fetches the schema validates the instance
 * cleanly and one that does not cannot. The permissive controls prove the external schema is reachable in principle, so
 * the secure side throwing means the fetch was refused, not merely misconfigured. The stock JDK refuses it through
 * {@code accessExternalSchema=""}; external Apache Xerces, which ignores that property, refuses it through the ignore-all
 * entity-resolver floor.</p>
 *
 * <p>Not every parser supports these schema-validation knobs (Android's KXmlParser and Expat do not), so the whole
 * configuration runs through {@link #configureOrSkip}: a parser that rejects validation, the schema language, or the
 * schema-location property skips the test rather than failing it.</p>
 */
@Tag("schema")
class SchemaLocationPropertyTest {

    private static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    private static final String SCHEMA_FEATURE = "http://apache.org/xml/features/validation/schema";
    private static final String EXTERNAL_NO_NS = "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation";
    private static final String EXTERNAL_SCHEMA_LOCATION = "http://apache.org/xml/properties/schema/external-schemaLocation";

    /** Instance whose root, {@code <root>}, is declared by {@code no-namespace.xsd}. */
    private static final String NO_NS_INSTANCE = "<root>x</root>";

    private static final String LEAKED_NS = "http://example.org/leaked";

    /** Instance whose root, {@code l:leaked}, is declared by {@code included.xsd} in the {@value #LEAKED_NS} namespace. */
    private static final String NAMESPACED_INSTANCE = "<l:leaked xmlns:l=\"" + LEAKED_NS + "\">x</l:leaked>";

    /**
     * Runs the parser setup, skipping the test (rather than failing it) on parsers that do not accept these
     * schema-validation features/properties, such as Android's KXmlParser and Expat.
     */
    private static <T> T configureOrSkip(final ThrowingSupplier<T> setup) {
        try {
            return setup.get();
        } catch (final Throwable t) {
            return Assumptions.abort("Parser does not support schema validation through these features/properties: " + t);
        }
    }

    private static DocumentBuilder secureValidatingDom(final String property, final String value) {
        return configureOrSkip(() -> {
            final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setAttribute(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setAttribute(property, value);
            return strictDocumentBuilder(factory);
        });
    }

    private static XMLReader secureValidatingSax(final String property, final String value) {
        return configureOrSkip(() -> {
            final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            final SAXParser parser = factory.newSAXParser();
            parser.setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            parser.setProperty(property, value);
            return strictXMLReader(parser.getXMLReader());
        });
    }

    private static String namespacedLocation() {
        return LEAKED_NS + " " + resourceUrl("included.xsd");
    }

    private static String noNamespaceLocation() {
        return resourceUrl("no-namespace.xsd").toString();
    }

    private static DocumentBuilder permissiveValidatingDom(final String property, final String value) {
        return configureOrSkip(() -> {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            factory.setAttribute(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setAttribute(property, value);
            return strictDocumentBuilder(factory);
        });
    }

    private static XMLReader permissiveValidatingSax(final String property, final String value) {
        return configureOrSkip(() -> {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            factory.setFeature(SCHEMA_FEATURE, true);
            final SAXParser parser = factory.newSAXParser();
            parser.setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            parser.setProperty(property, value);
            return strictXMLReader(parser.getXMLReader());
        });
    }

    @Test
    void secureDomRefusesNoNamespaceSchemaLocation() {
        final DocumentBuilder builder = secureValidatingDom(EXTERNAL_NO_NS, noNamespaceLocation());
        assertParseFails(() -> builder.parse(inputSource(NO_NS_INSTANCE)), "DOM external-noNamespaceSchemaLocation", SAXException.class);
    }

    @Test
    void secureDomRefusesSchemaLocation() {
        final DocumentBuilder builder = secureValidatingDom(EXTERNAL_SCHEMA_LOCATION, namespacedLocation());
        assertParseFails(() -> builder.parse(inputSource(NAMESPACED_INSTANCE)), "DOM external-schemaLocation", SAXException.class);
    }

    @Test
    void secureSaxRefusesNoNamespaceSchemaLocation() {
        final XMLReader reader = secureValidatingSax(EXTERNAL_NO_NS, noNamespaceLocation());
        assertParseFails(() -> reader.parse(inputSource(NO_NS_INSTANCE)), "SAX external-noNamespaceSchemaLocation", SAXException.class);
    }

    @Test
    void secureSaxRefusesSchemaLocation() {
        final XMLReader reader = secureValidatingSax(EXTERNAL_SCHEMA_LOCATION, namespacedLocation());
        assertParseFails(() -> reader.parse(inputSource(NAMESPACED_INSTANCE)), "SAX external-schemaLocation", SAXException.class);
    }

    @Test
    void permissiveDomFetchesNoNamespaceSchemaLocation() {
        final DocumentBuilder builder = permissiveValidatingDom(EXTERNAL_NO_NS, noNamespaceLocation());
        assertParseSucceeds(() -> builder.parse(inputSource(NO_NS_INSTANCE)), "DOM external-noNamespaceSchemaLocation (permissive)");
    }

    @Test
    void permissiveDomFetchesSchemaLocation() {
        final DocumentBuilder builder = permissiveValidatingDom(EXTERNAL_SCHEMA_LOCATION, namespacedLocation());
        assertParseSucceeds(() -> builder.parse(inputSource(NAMESPACED_INSTANCE)), "DOM external-schemaLocation (permissive)");
    }

    @Test
    void permissiveSaxFetchesNoNamespaceSchemaLocation() {
        final XMLReader reader = permissiveValidatingSax(EXTERNAL_NO_NS, noNamespaceLocation());
        assertParseSucceeds(() -> reader.parse(inputSource(NO_NS_INSTANCE)), "SAX external-noNamespaceSchemaLocation (permissive)");
    }

    @Test
    void permissiveSaxFetchesSchemaLocation() {
        final XMLReader reader = permissiveValidatingSax(EXTERNAL_SCHEMA_LOCATION, namespacedLocation());
        assertParseSucceeds(() -> reader.parse(inputSource(NAMESPACED_INSTANCE)), "SAX external-schemaLocation (permissive)");
    }
}
