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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
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

/**
 * Checks that a caller-supplied resolver cannot remove the hardened ignore-all floor on any factory.
 *
 * <p>The observable contract on every hardened factory is the same: a resource the caller resolves (returns a non-null value) is allowed, but anything the
 * caller does not resolve is resolved to empty content instead of fetched, so a resolver that resolves nothing leaves the block in place. Most
 * factories enforce this with a {@link FallbackIgnoreEntityResolver2}-style floor that consults the caller and returns empty on a {@code null} return; Saxon
 * enforces the equivalent through an ignore-all {@code ResourceResolver} floor on its {@code Configuration}. Every resolver channel is exercised: the SAX/DOM
 * {@link EntityResolver}, the StAX {@link XMLResolver}, the schema {@link LSResourceResolver} and the XSLT {@link URIResolver}.</p>
 */
class EntityResolverFloorTest {

    /** systemId the allow-list resolvers permit (its content carries {@link AttackTestSupport#LEAKED_MARKER}). */
    private static final String ALLOWED = AttackTestSupport.resourceUrl("referenced.txt").toString();

    /** systemId the allow-list resolvers do not resolve (so the floor resolves it to empty; its content carries {@link AttackTestSupport#LEAKED_MARKER}). */
    private static final String UNLISTED = AttackTestSupport.resourceUrl("referenced.xml").toString();

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
    void domDoesNotLeakUnlisted() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final DocumentBuilder builder = hardenedBuilder();
        builder.setEntityResolver(ENTITY_ALLOW_LIST);
        // The caller returns null for the unlisted entity, so the floor resolves it to empty rather than fetching it: the parse completes (or is rejected)
        // without leaking the entity's content.
        try {
            final Document doc = builder.parse(AttackTestSupport.inputSource(entityPayload(UNLISTED)));
            assertFalse(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER), "unlisted external entity leaked into the DOM");
        } catch (final SAXException blocked) {
            // Acceptable: the reference was rejected at parse rather than resolved to empty.
        }
    }

    @Test
    @Tag("sax")
    void saxReaderResolvesAllowListed() throws Exception {
        final XMLReader reader = hardenedReader();
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        final String text = AttackTestSupport.captureCharacters(reader, entityPayload(ALLOWED));
        assertTrue(text.contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
    }

    @Test
    @Tag("sax")
    void saxReaderDoesNotLeakUnlisted() throws Exception {
        final XMLReader reader = hardenedReader();
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        // The caller returns null for the unlisted entity, so the floor resolves it to empty rather than fetching it.
        final String text;
        try {
            text = AttackTestSupport.captureCharacters(reader, entityPayload(UNLISTED));
        } catch (final SAXException blocked) {
            return; // Acceptable: rejected at parse rather than resolved to empty.
        }
        assertFalse(text.contains(AttackTestSupport.LEAKED_MARKER), "unlisted external entity leaked:\n" + text);
    }

    @Test
    @Tag("sax")
    void saxParseWithHandlerDoesNotBypass() throws Exception {
        // SAXParser.parse(source, handler) installs the handler as the reader's entity resolver; the handler does not resolve it (returns null), so the
        // ignore-all floor must still resolve the external entity to empty rather than letting the parser fetch it.
        final SAXParser parser = XmlFactories.newSAXParserFactory().newSAXParser();
        final StringBuilder text = new StringBuilder();
        try {
            parser.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)), AttackTestSupport.capturingHandler(text));
        } catch (final SAXException e) {
            return; // blocked at parse: acceptable
        }
        assertFalse(text.toString().contains(AttackTestSupport.LEAKED_MARKER), "parse(source, handler) leaked the external entity:\n" + text);
    }

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
        final String text = AttackTestSupport.captureCharacters(reader, new InputSource(XINCLUDE_HOST));
        assertTrue(text.contains(AttackTestSupport.LEAKED_MARKER),
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

    @Test
    @Tag("stax")
    void staxResolvesAllowListed() throws Exception {
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver(STAX_ALLOW_LIST);
        assertTrue(AttackTestSupport.captureStaxEventText(factory, entityPayload(ALLOWED)).contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
    }

    @Test
    @Tag("stax")
    void staxDoesNotLeakUnlisted() throws Exception {
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver(STAX_ALLOW_LIST);
        // The caller returns null for the unlisted entity, so the floor resolves it to empty rather than fetching it.
        try {
            assertFalse(AttackTestSupport.captureStaxEventText(factory, entityPayload(UNLISTED)).contains(AttackTestSupport.LEAKED_MARKER),
                    "unlisted external entity leaked");
        } catch (final XMLStreamException blocked) {
            // Acceptable: rejected at parse rather than resolved to empty.
        }
    }

    @Test
    @Tag("stax")
    void staxCallerCannotRemoveFloor() throws Exception {
        // A caller resolver that resolves nothing must not re-open external fetches: the floor still resolves the reference to empty rather than fetching it.
        final XMLInputFactory factory = externalEntityStaxFactory();
        factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
        try {
            assertFalse(AttackTestSupport.captureStaxEventText(factory, entityPayload(ALLOWED)).contains(AttackTestSupport.LEAKED_MARKER),
                    "floor was bypassed and the entity leaked");
        } catch (final XMLStreamException blocked) {
            // Acceptable: rejected at parse rather than resolved to empty.
        }
    }

    @Test
    @Tag("stax")
    void staxGetXMLResolverReportsCallerUnwrapped() {
        final XMLInputFactory factory = XmlFactories.newXMLInputFactory();
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        factory.setXMLResolver(caller);
        assertSame(caller, factory.getXMLResolver(), "getXMLResolver should report the caller's resolver, not the floor wrapper");
    }

    /** Absolute location of the imported schema the allow-list resolver permits. */
    private static final String ALLOWED_SCHEMA = AttackTestSupport.resourceUrl("included.xsd").toString();

    /** Resolves only the {@code included.xsd} import; returns {@code null} for anything else. */
    private static final LSResourceResolver SCHEMA_ALLOW_LIST = (type, namespaceURI, publicId, systemId, baseURI) ->
            systemId != null && systemId.endsWith("included.xsd") ? lsInput(ALLOWED_SCHEMA) : null;

    private static LSInput lsInput(final String systemId) {
        return assertDoesNotThrow(() -> {
            final LSInput input = identifierOnlyLsInput(systemId);
            input.setByteStream(new URL(systemId).openStream());
            return input;
        }, "Failed to build LSInput for " + systemId);
    }

    /** An {@link LSInput} naming the resource but carrying no content: a redirect the implementation fetches itself, like an identifier-only {@code InputSource}. */
    private static LSInput identifierOnlyLsInput(final String systemId) {
        return assertDoesNotThrow(() -> {
            final DOMImplementationLS ls = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
            final LSInput input = ls.createLSInput();
            input.setSystemId(systemId);
            return input;
        }, "Failed to build identifier-only LSInput for " + systemId);
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

    @Test
    @Tag("schema")
    void schemaFetchesIdentifierOnlyOptIn() {
        // A non-null return is an opt-in even without content: the implementation fetches the named resource itself, mirroring the entity floor's contract.
        assertParseSucceeds(() -> {
            final SchemaFactory factory = XmlFactories.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) ->
                    systemId != null && systemId.endsWith("included.xsd") ? identifierOnlyLsInput(ALLOWED_SCHEMA) : null);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import via identifier-only LSInput");
    }

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
    void transformerDoesNotLeakUnlisted() {
        final TransformerFactory factory = hardenedTransformerFactory();
        factory.setURIResolver((href, base) -> null);
        // Deterministic on every implementation: XSLTC and Xalan compile the empty document the URIResolver floor
        // returns, Saxon the EmptySource its Configuration floor returns, so the import contributes nothing.
        final StringWriter sink = new StringWriter();
        assertDoesNotThrow(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")).newTransformer()
                .transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink)));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "unlisted stylesheet import leaked");
    }

    @Test
    @Tag("trax")
    void transformerParsesOptedInImportHardened() {
        // The opted-in module carries an external DTD reference; parsed on the floor the DTD is empty, so its entity cannot expand into the output.
        final TransformerFactory factory = hardenedTransformerFactory();
        factory.setURIResolver((href, base) ->
                href != null && href.endsWith("included.xsl") ? AttackTestSupport.resourceSource("included-with-entity.xsl") : null);
        // The emptied DTD leaves the entity undeclared — only a validity violation when an external subset is
        // declared — so every non-validating parser skips it and the transform deterministically completes.
        final StringWriter sink = new StringWriter();
        assertDoesNotThrow(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")).newTransformer()
                .transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink)));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "opted-in stylesheet import leaked its external entity");
    }

    @Test
    @Tag("trax")
    void transformerParsesOptedInDocumentHardened() {
        // Same contract on the runtime document() channel, which reaches a different internal reader than the compile-time import.
        final TransformerFactory factory = hardenedTransformerFactory();
        factory.setURIResolver((href, base) ->
                href != null && href.endsWith("referenced.xml") ? AttackTestSupport.resourceSource("referenced-with-entity.xml") : null);
        // Same undeclared-entity outcome as the import above: skipped, never expanded.
        final StringWriter sink = new StringWriter();
        assertDoesNotThrow(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-document.xsl")).newTransformer()
                .transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink)));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "opted-in document() resource leaked its external entity");
    }

    /**
     * A hardened {@link TransformerFactory} with a re-throwing error listener. XSLTC and Xalan enforce the block through the
     * {@link FallbackIgnoreURIResolver} floor; Saxon enforces it through the ignore-all resolver floor on its {@code Configuration}. Either way a caller-set
     * resolver that returns {@code null} cannot re-open the fetch. The strict listener turns any reported-and-recovered error into a test failure, so an
     * implementation cannot quietly recover from a floor resolution while the test asserts clean completion.
     */
    private static TransformerFactory hardenedTransformerFactory() {
        final TransformerFactory factory = XmlFactories.newTransformerFactory();
        factory.setErrorListener(AttackTestSupport.STRICT_REPORTER);
        return factory;
    }
}
