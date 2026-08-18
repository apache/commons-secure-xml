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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Checks that a caller-supplied resolver cannot remove the hardened deny-all floor on any factory.
 *
 * <p>The observable contract on every hardened factory is the same: a resource the caller resolves (returns a non-null value) is allowed, but anything the
 * caller does not resolve is denied instead of fetched, so a resolver that resolves nothing leaves the block in place. Most factories enforce this with a
 * {@link FallbackDenyEntityResolver2}-style floor that consults the caller and denies on a {@code null} return; Saxon enforces the equivalent through its
 * {@code ALLOWED_PROTOCOLS} restrictor. Every resolver channel is exercised: the SAX/DOM {@link EntityResolver}, the StAX {@link XMLResolver}, the schema
 * {@link LSResourceResolver} and the XSLT {@link URIResolver}.</p>
 */
class EntityResolverFloorTest {

    /** systemId the allow-list resolvers permit (its content carries {@link AttackTestSupport#LEAKED_MARKER}). */
    private static final String ALLOWED = AttackTestSupport.resourceUrl("referenced.txt").toString();

    /** systemId the allow-list resolvers do not resolve (and the floor must deny). */
    private static final String UNLISTED = AttackTestSupport.resourceUrl("referenced.xml").toString();

    // ---- Entity channel (DOM / SAX) ----------------------------------------------------------------------------------------------------------------------

    /** Resolves only {@link #ALLOWED}; returns {@code null} for anything else. */
    private static final EntityResolver ENTITY_ALLOW_LIST = (publicId, systemId) ->
            ALLOWED.equals(systemId) ? new InputSource(new URL(systemId).openStream()) : null;

    private static String entityPayload(final String entitySystemId) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [\n  <!ENTITY xxe SYSTEM \"" + entitySystemId + "\">\n]>\n"
                + "<root>&xxe;</root>";
    }

    private static DocumentBuilder hardenedBuilder() throws Exception {
        final DocumentBuilder builder = XmlFactories.newDocumentBuilderFactory().newDocumentBuilder();
        builder.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        return builder;
    }

    private static XMLReader hardenedReader() throws Exception {
        final XMLReader reader = XmlFactories.newSAXParserFactory().newSAXParser().getXMLReader();
        reader.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        return reader;
    }

    @Test
    @Tag("dom")
    void domResolvesAllowListed() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final DocumentBuilder builder = hardenedBuilder();
        builder.setEntityResolver(ENTITY_ALLOW_LIST);
        final Document doc = builder.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)));
        assertTrue(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
    }

    @Test
    @Tag("dom")
    void domDeniesUnlisted() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final DocumentBuilder builder = hardenedBuilder();
        builder.setEntityResolver(ENTITY_ALLOW_LIST);
        assertThrows(SAXException.class, () -> builder.parse(AttackTestSupport.inputSource(entityPayload(UNLISTED))));
    }

    @Test
    @Tag("sax")
    void saxReaderResolvesAllowListed() throws Exception {
        final XMLReader reader = hardenedReader();
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        final StringBuilder text = new StringBuilder();
        reader.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                text.append(ch, start, length);
            }
        });
        reader.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)));
        assertTrue(text.toString().contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
    }

    @Test
    @Tag("sax")
    void saxReaderDeniesUnlisted() throws Exception {
        final XMLReader reader = hardenedReader();
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        reader.setContentHandler(new DefaultHandler());
        assertThrows(SAXException.class, () -> reader.parse(AttackTestSupport.inputSource(entityPayload(UNLISTED))));
    }

    @Test
    @Tag("sax")
    void saxParseWithHandlerDoesNotBypass() throws Exception {
        // SAXParser.parse(source, handler) installs the handler as the reader's entity resolver; the handler does not resolve it (returns null), so the
        // deny-all floor must still block the external entity rather than letting the parser fetch it.
        final SAXParser parser = XmlFactories.newSAXParserFactory().newSAXParser();
        final StringBuilder text = new StringBuilder();
        final DefaultHandler handler = new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                text.append(ch, start, length);
            }
        };
        try {
            parser.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)), handler);
        } catch (final SAXException e) {
            return; // blocked at parse: acceptable
        }
        assertFalse(text.toString().contains(AttackTestSupport.LEAKED_MARKER), "parse(source, handler) leaked the external entity:\n" + text);
    }

    // ---- Entity channel: relative XInclude href (DOM / SAX) ----------------------------------------------------------------------------------------------

    /**
     * Allow-all resolver: it denies nothing, resolving whatever {@code systemId} it is handed by opening it as a URL. It nonetheless cannot resolve a bare
     * relative reference such as {@code referenced.xml}, because a plain {@link EntityResolver} (unlike {@link org.xml.sax.ext.EntityResolver2}) is given no
     * base URI and the SAX2 contract promises it an already-absolutized {@code systemId}. So the resolution fails not from any deny decision but because the
     * resolver was never handed the whole URL: it succeeds only if the floor absolutizes the XInclude href against the base before consulting the caller.
     */
    private static final EntityResolver RESOLVE_ALL = (publicId, systemId) -> {
        final InputSource source = new InputSource(new URL(systemId).openStream());
        source.setSystemId(systemId);
        return source;
    };

    @Test
    @Tag("dom")
    void domResolvesRelativeXIncludeSibling() throws Exception {
        final DocumentBuilder builder = xIncludeAwareBuilder();
        builder.setEntityResolver(RESOLVE_ALL);
        final Document doc = builder.parse(XINCLUDE_HOST);
        assertTrue(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER),
                "relative XInclude sibling should resolve through the caller's resolver after the floor absolutizes the href");
    }

    @Test
    @Tag("sax")
    void saxResolvesRelativeXIncludeSibling() throws Exception {
        final XMLReader reader = xIncludeAwareReader();
        reader.setEntityResolver(RESOLVE_ALL);
        final StringBuilder text = new StringBuilder();
        reader.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                text.append(ch, start, length);
            }
        });
        reader.parse(XINCLUDE_HOST);
        assertTrue(text.toString().contains(AttackTestSupport.LEAKED_MARKER),
                "relative XInclude sibling should resolve through the caller's resolver after the floor absolutizes the href");
    }

    /** Absolute URL of the host document whose {@code xi:include} references {@code referenced.xml} by a relative href. */
    private static final String XINCLUDE_HOST = AttackTestSupport.resourceUrl("with-xinclude.xml").toString();

    private static DocumentBuilder xIncludeAwareBuilder() throws Exception {
        final DocumentBuilderFactory factory = XmlFactories.newDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setXIncludeAware(true));
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        return builder;
    }

    private static XMLReader xIncludeAwareReader() throws Exception {
        final SAXParserFactory factory = XmlFactories.newSAXParserFactory();
        factory.setNamespaceAware(true);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setXIncludeAware(true));
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        return reader;
    }

    // ---- Entity channel (StAX) ---------------------------------------------------------------------------------------------------------------------------

    /** Resolves only {@link #ALLOWED} to its content stream; returns {@code null} for anything else. */
    private static final XMLResolver STAX_ALLOW_LIST = (publicID, systemID, baseURI, namespace) -> {
        if (!ALLOWED.equals(systemID)) {
            return null;
        }
        try {
            return new URL(systemID).openStream();
        } catch (final java.io.IOException e) {
            throw new XMLStreamException(e);
        }
    };

    private static XMLInputFactory externalEntityStaxFactory() {
        final XMLInputFactory factory = XmlFactories.newXMLInputFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, true);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        return factory;
    }

    private static String readStaxText(final XMLInputFactory factory, final String payload) throws XMLStreamException {
        final StringBuilder text = new StringBuilder();
        final XMLStreamReader stream = factory.createXMLStreamReader(new StringReader(payload));
        try {
            while (stream.hasNext()) {
                final int event = stream.next();
                if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    text.append(stream.getText());
                }
            }
        } finally {
            stream.close();
        }
        return text.toString();
    }

    @Test
    @Tag("stax")
    void staxResolvesAllowListed() throws Exception {
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver(STAX_ALLOW_LIST);
        assertTrue(readStaxText(factory, entityPayload(ALLOWED)).contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
    }

    @Test
    @Tag("stax")
    void staxDeniesUnlisted() {
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver(STAX_ALLOW_LIST);
        assertThrows(XMLStreamException.class, () -> readStaxText(factory, entityPayload(UNLISTED)));
    }

    @Test
    @Tag("stax")
    void staxCallerCannotRemoveFloor() {
        // A caller resolver that resolves nothing must not re-open external fetches: the floor still denies.
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
        assertThrows(XMLStreamException.class, () -> readStaxText(factory, entityPayload(ALLOWED)));
    }

    @Test
    @Tag("stax")
    void staxGetXMLResolverReportsCallerUnwrapped() {
        final XMLInputFactory factory = XmlFactories.newXMLInputFactory();
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        factory.setXMLResolver(caller);
        assertSame(caller, factory.getXMLResolver(), "getXMLResolver should report the caller's resolver, not the floor wrapper");
    }

    // ---- Schema channel (LSResourceResolver) -------------------------------------------------------------------------------------------------------------

    /** Absolute location of the imported schema the allow-list resolver permits. */
    private static final String ALLOWED_SCHEMA = AttackTestSupport.resourceUrl("included.xsd").toString();

    /** Resolves only the {@code included.xsd} import; returns {@code null} for anything else. */
    private static final LSResourceResolver SCHEMA_ALLOW_LIST = (type, namespaceURI, publicId, systemId, baseURI) ->
            systemId != null && systemId.endsWith("included.xsd") ? lsInput(ALLOWED_SCHEMA) : null;

    private static LSInput lsInput(final String systemId) {
        try {
            final DOMImplementationLS ls = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
            final LSInput input = ls.createLSInput();
            input.setByteStream(new URL(systemId).openStream());
            input.setSystemId(systemId);
            return input;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to build LSInput for " + systemId, e);
        }
    }

    @Test
    @Tag("schema")
    void schemaResolvesAllowListed() {
        // with-import.xsd references an element defined only in the imported included.xsd, so it compiles only if the import is resolved.
        assertParseSucceeds(() -> {
            final SchemaFactory factory = XmlFactories.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver(SCHEMA_ALLOW_LIST);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import via caller resolver");
    }

    @Test
    @Tag("schema")
    void schemaDeniesUnlisted() {
        assertParseFails(() -> {
            final SchemaFactory factory = XmlFactories.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) -> null);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import", SAXException.class, SecurityException.class);
    }

    // ---- XSLT channel (URIResolver) ----------------------------------------------------------------------------------------------------------------------

    /** Resolves only the {@code included.xsl} import; returns {@code null} for anything else. */
    private static final URIResolver XSL_ALLOW_LIST = (href, base) ->
            href != null && href.endsWith("included.xsl") ? AttackTestSupport.resourceSource("included.xsl") : null;

    @Test
    @Tag("trax")
    void transformerResolvesAllowListed() {
        // with-import.xsl imports included.xsl, so it compiles only if the import is resolved.
        final TransformerFactory factory = hardenedTransformerFactory();
        factory.setURIResolver(XSL_ALLOW_LIST);
        assertParseSucceeds(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")), "Stylesheet import via caller resolver");
    }

    @Test
    @Tag("trax")
    void transformerDeniesUnlisted() {
        final TransformerFactory factory = hardenedTransformerFactory();
        factory.setURIResolver((href, base) -> null);
        assertParseFails(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")), "Stylesheet import", TransformerException.class);
    }

    /**
     * A hardened {@link TransformerFactory} with a re-throwing error listener. XSLTC and Xalan enforce the deny through the
     * {@link FallbackDenyURIResolver} floor; Saxon enforces it through its {@code ALLOWED_PROTOCOLS} restrictor. Either way a caller-set resolver that
     * returns {@code null} cannot re-open the fetch. The strict listener is required because interpretive Xalan routes a blocked {@code xsl:import} through the
     * error listener and would otherwise recover and compile instead of throwing (XSLTC and Saxon throw regardless).
     */
    private static TransformerFactory hardenedTransformerFactory() {
        final TransformerFactory factory = XmlFactories.newTransformerFactory();
        factory.setErrorListener(AttackTestSupport.STRICT_REPORTER);
        return factory;
    }
}
