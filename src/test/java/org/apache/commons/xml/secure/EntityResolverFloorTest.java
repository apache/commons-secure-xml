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

import static org.apache.commons.xml.secure.AttackTestSupport.assertParseFails;
import static org.apache.commons.xml.secure.AttackTestSupport.assertParseSucceeds;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
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
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Tests that a caller-supplied resolver cannot remove the secure ignore-all floor on any factory.
 *
 * <p>
 * The observable contract on every secure factory is the same: a resource the caller resolves (returns a non-null value) is allowed, but anything the
 * caller does not resolve is resolved to empty content instead of fetched, so a resolver that resolves nothing leaves the block in place. Most
 * factories enforce this with a {@link FallbackIgnoreEntityResolver2}-style floor that consults the caller and returns empty on a {@code null} return; Saxon
 * enforces the equivalent through an ignore-all {@code ResourceResolver} floor on its {@code Configuration}. Every resolver channel is exercised: the SAX/DOM
 * {@link EntityResolver}, the StAX {@link XMLResolver}, the schema {@link LSResourceResolver} and the XSLT {@link URIResolver}.
 * </p>
 */
class EntityResolverFloorTest {

    /**
     * systemId the allow-list resolvers permit (its content carries {@link AttackTestSupport#LEAKED_MARKER}).
     */
    private static final String ALLOWED = AttackTestSupport.resourceUrl("referenced.txt").toString();

    /**
     * systemId the allow-list resolvers do not resolve (so the floor resolves it to empty; its content carries {@link AttackTestSupport#LEAKED_MARKER}).
     */
    private static final String UNLISTED = AttackTestSupport.resourceUrl("referenced.xml").toString();

    /**
     * Resolves only {@link #ALLOWED}; returns {@code null} for anything else.
     */
    private static final EntityResolver ENTITY_ALLOW_LIST = (publicId, systemId) ->
            ALLOWED.equals(systemId) ? new InputSource(new URL(systemId).openStream()) : null;

    /**
     * Allow-all resolver: it denies nothing, resolving whatever {@code systemId} it is handed by opening it as a URL. It nonetheless cannot resolve a bare
     * relative reference such as {@code referenced.xml}, because a plain {@link EntityResolver} (unlike {@link org.xml.sax.ext.EntityResolver2}) is given no
     * base URI and the SAX 2 contract promises it an already-absolutized {@code systemId}. So the resolution fails not from any deny decision but because the
     * resolver was never handed the whole URL: it succeeds only if the floor absolutizes the XInclude href against the base before consulting the caller.
     */
    private static final EntityResolver RESOLVE_ALL = (publicId, systemId) -> {
        final InputSource source = new InputSource(new URL(systemId).openStream());
        source.setSystemId(systemId);
        return source;
    };

    /**
     * Absolute URL of the host document whose {@code xi:include} references {@code referenced.xml} by a relative href.
     */
    private static final String XINCLUDE_HOST = AttackTestSupport.resourceUrl("with-xinclude.xml").toString();

    /**
     * Resolves only {@link #ALLOWED} to its content stream; returns {@code null} for anything else.
     */
    private static final XMLResolver STAX_ALLOW_LIST = (publicID, systemID, baseURI, namespace) -> {
        if (!ALLOWED.equals(systemID)) {
            return null;
        }
        try {
            return new URL(systemID).openStream();
        } catch (final IOException e) {
            throw new XMLStreamException(e);
        }
    };

    /**
     * Absolute location of the imported schema the allow-list resolver permits.
     */
    private static final String ALLOWED_SCHEMA = AttackTestSupport.resourceUrl("included.xsd").toString();

    /**
     * Resolves only the {@code included.xsd} import; returns {@code null} for anything else.
     */
    private static final LSResourceResolver SCHEMA_ALLOW_LIST = (type, namespaceURI, publicId, systemId, baseURI) ->
            systemId != null && systemId.endsWith("included.xsd") ? lsInput(ALLOWED_SCHEMA) : null;

    /**
     * Resolves only the {@code included.xsl} import; returns {@code null} for anything else.
     */
    private static final URIResolver XSL_ALLOW_LIST = (href, base) ->
            href != null && href.endsWith("included.xsl") ? AttackTestSupport.resourceSource("included.xsl") : null;

    private static String entityPayload(final String entitySystemId) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [\n  <!ENTITY xxe SYSTEM \"" + entitySystemId + "\">\n]>\n"
                + "<root>&xxe;</root>";
    }

    private static XMLInputFactory externalEntityStaxFactory() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, true);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        return factory;
    }

    /**
     * An {@link LSInput} naming the resource but carrying no content: a redirect the implementation fetches itself, like an identifier-only
     * {@code InputSource}.
     */
    private static LSInput identifierOnlyLsInput(final String systemId) {
        return assertDoesNotThrow(() -> {
            final DOMImplementationLS ls = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
            final LSInput input = ls.createLSInput();
            input.setSystemId(systemId);
            return input;
        }, "Failed to build identifier-only LSInput for " + systemId);
    }

    private static LSInput lsInput(final String systemId) {
        return assertDoesNotThrow(() -> {
            final LSInput input = identifierOnlyLsInput(systemId);
            input.setByteStream(new URL(systemId).openStream());
            return input;
        }, "Failed to build LSInput for " + systemId);
    }

    private static DocumentBuilder secureBuilder(Collection<SAXParseException> saxWarnings) throws Exception {
        final DocumentBuilder builder = SecureDocumentBuilderFactory.newInstance().newDocumentBuilder();
        builder.setErrorHandler(new AttackTestSupport.StrictReporter(saxWarnings,  null));
        return builder;
    }

    /**
     * A secure {@link TransformerFactory} with a re-throwing error listener. XSLTC and Xalan enforce the block through the
     * {@link FallbackIgnoreURIResolver} floor; Saxon enforces it through the ignore-all resolver floor on its {@code Configuration}. Either way, a caller-set
     * resolver that returns {@code null} cannot re-open the fetch. The strict listener turns any reported-and-recovered error into a test failure, so an
     * implementation cannot quietly recover from a floor resolution while the test asserts clean completion.
     */
    private static TransformerFactory secureTransformerFactory(Collection<TransformerException> transformerWarnings) {
        final TransformerFactory factory = SecureTransformerFactory.newInstance();
        factory.setErrorListener(new AttackTestSupport.StrictReporter(null, transformerWarnings));
        return factory;
    }

    private static XMLReader secureXMLReader(Collection<SAXParseException> saxWarnings) throws Exception {
        final XMLReader reader = SecureSAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setErrorHandler(new AttackTestSupport.StrictReporter(saxWarnings,  null));
        return reader;
    }

    private static DocumentBuilder xIncludeAwareBuilder(Collection<SAXParseException> saxWarnings) throws Exception {
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setXIncludeAware(true));
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new AttackTestSupport.StrictReporter(saxWarnings,  null));
        return builder;
    }

    private static XMLReader xIncludeAwareReader(Collection<SAXParseException> saxWarnings) throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setXIncludeAware(true));
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setErrorHandler(new AttackTestSupport.StrictReporter(saxWarnings,  null));
        return reader;
    }

    private static void assertIgnoredEntitySax(Collection<SAXParseException> warnings) {
        doAssertIgnoredEntity(false, warnings.stream().map(SAXParseException::getMessage).collect(Collectors.toList()));
    }

    private static void assertIgnoredEntityTransformer(Collection<TransformerException> warnings) {
        doAssertIgnoredEntity(false, warnings.stream().map(TransformerException::getMessage).collect(Collectors.toList()));
    }

    private static void assertNoIgnoredEntitySax(Collection<SAXParseException> warnings) {
        doAssertIgnoredEntity(true, warnings.stream().map(SAXParseException::getMessage).collect(Collectors.toList()));
    }

    private static void assertNoIgnoredEntityTransformer(Collection<TransformerException> warnings) {
        doAssertIgnoredEntity(true, warnings.stream().map(TransformerException::getMessage).collect(Collectors.toList()));
    }

    private static final void doAssertIgnoredEntity(boolean expectNone, Collection<String> messages) {
        final String expected = "External resource fetch forbidden";
        if (expectNone) {
            assertTrue(messages.stream().noneMatch(m -> m.contains(expected)),
                    "Expected no warning for ignored entity found in " + messages);
        } else {
            assertTrue(messages.stream().anyMatch(m -> m.contains(expected)),
                    "Expected at least one warning for ignored entity not found in " + messages);
        }
    }

    @Test
    @Tag("dom")
    void domDoesNotLeakUnlisted() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final DocumentBuilder builder = secureBuilder(warnings);
        builder.setEntityResolver(ENTITY_ALLOW_LIST);
        // The caller returns null for the unlisted entity, so the floor resolves it to empty rather than fetching it: the parse completes (or is rejected)
        // without leaking the entity's content.
        try {
            final Document doc = builder.parse(AttackTestSupport.inputSource(entityPayload(UNLISTED)));
            assertFalse(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER), "unlisted external entity leaked into the DOM");
            assertIgnoredEntitySax(warnings);
        } catch (final SAXException blocked) {
            // Acceptable: the reference was rejected at parse rather than resolved to empty.
        }
    }

    @Test
    @Tag("dom")
    void domResolvesAllowListed() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final DocumentBuilder builder = secureBuilder(warnings);
        builder.setEntityResolver(ENTITY_ALLOW_LIST);
        final Document doc = builder.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)));
        assertTrue(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
        assertNoIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("dom")
    void domResolvesRelativeXIncludeSibling() throws Exception {
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final DocumentBuilder builder = xIncludeAwareBuilder(warnings);
        builder.setEntityResolver(RESOLVE_ALL);
        final Document doc = builder.parse(XINCLUDE_HOST);
        assertTrue(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER),
                "relative XInclude sibling should resolve through the caller's resolver after the floor absolutizes the href");
        assertNoIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("sax")
    void saxParseWithHandlerDoesNotBypass() throws Exception {
        // SAXParser.parse(source, handler) installs the handler as the reader's entity resolver; the handler does not resolve it (returns null), so the
        // ignore-all floor must still resolve the external entity to empty rather than letting the parser fetch it.
        final SAXParser parser = SecureSAXParserFactory.newInstance().newSAXParser();
        final StringBuilder text = new StringBuilder();
        final Collection<SAXParseException> warnings = new ArrayList<>();
        try {
            parser.parse(AttackTestSupport.inputSource(entityPayload(ALLOWED)), AttackTestSupport.capturingHandler(text, warnings));
        } catch (final SAXException e) {
            return; // blocked at parse: acceptable
        }
        assertFalse(text.toString().contains(AttackTestSupport.LEAKED_MARKER), "parse(source, handler) leaked the external entity:\n" + text);
        assertIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("sax")
    void saxReaderDoesNotLeakUnlisted() throws Exception {
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final XMLReader reader = secureXMLReader(warnings);
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        // The caller returns null for the unlisted entity, so the floor resolves it to empty rather than fetching it.
        final String text;
        try {
            text = AttackTestSupport.captureCharacters(reader, entityPayload(UNLISTED));
        } catch (final SAXException blocked) {
            return; // Acceptable: rejected at parse rather than resolved to empty.
        }
        assertFalse(text.contains(AttackTestSupport.LEAKED_MARKER), "unlisted external entity leaked:\n" + text);
        assertIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("sax")
    void saxReaderResolvesAllowListed() throws Exception {
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final XMLReader reader = secureXMLReader(warnings);
        reader.setEntityResolver(ENTITY_ALLOW_LIST);
        final String text = AttackTestSupport.captureCharacters(reader, entityPayload(ALLOWED));
        assertTrue(text.contains(AttackTestSupport.LEAKED_MARKER),
                "allow-listed external entity should resolve through the caller's resolver");
        assertNoIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("sax")
    void saxResolvesRelativeXIncludeSibling() throws Exception {
        final Collection<SAXParseException> warnings = new ArrayList<>();
        final XMLReader reader = xIncludeAwareReader(warnings);
        reader.setEntityResolver(RESOLVE_ALL);
        final String text = AttackTestSupport.captureCharacters(reader, new InputSource(XINCLUDE_HOST));
        assertTrue(text.contains(AttackTestSupport.LEAKED_MARKER),
                "relative XInclude sibling should resolve through the caller's resolver after the floor absolutizes the href");
        assertNoIgnoredEntitySax(warnings);
    }

    @Test
    @Tag("schema")
    void schemaDeniesUnlisted() {
        assertParseFails(() -> {
            final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) -> null);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import", SAXException.class, SecurityException.class);
    }

    @Test
    @Tag("schema")
    void schemaFetchesIdentifierOnlyOptIn() {
        // A non-null return is an opt-in even without content: the implementation fetches the named resource itself, mirroring the entity floor's contract.
        assertParseSucceeds(() -> {
            final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) ->
                    systemId != null && systemId.endsWith("included.xsd") ? identifierOnlyLsInput(ALLOWED_SCHEMA) : null);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import via identifier-only LSInput");
    }

    @Test
    @Tag("schema")
    void schemaResolvesAllowListed() {
        // with-import.xsd references an element defined only in the imported included.xsd, so it compiles only if the import is resolved.
        assertParseSucceeds(() -> {
            final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver(SCHEMA_ALLOW_LIST);
            factory.newSchema(AttackTestSupport.resourceSource("with-import.xsd"));
        }, "Schema import via caller resolver");
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
    void staxGetXMLResolverReportsCallerUnwrapped() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        factory.setXMLResolver(caller);
        assertSame(caller, factory.getXMLResolver(), "getXMLResolver should report the caller's resolver, not the floor wrapper");
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
    @Tag("trax")
    void transformerDoesNotLeakUnlisted() throws Exception {
        final Collection<TransformerException> warnings = new ArrayList<>();
        final TransformerFactory factory = secureTransformerFactory(warnings);
        factory.setURIResolver((href, base) -> null);
        // Deterministic on every implementation: XSLTC and Xalan compile the empty document the URIResolver floor
        // returns, Saxon the EmptySource its Configuration floor returns, so the import contributes nothing.
        final StringWriter sink = new StringWriter();
        factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")).newTransformer().transform(AttackTestSupport.streamSource("<root/>"),
                new StreamResult(sink));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "unlisted stylesheet import leaked");
        assertIgnoredEntityTransformer(warnings);
    }

    @Test
    @Tag("trax")
    void transformerParsesOptedInDocumentSecured() throws Exception {
        final Collection<TransformerException> warnings = new ArrayList<>();
        // Same contract on the runtime document() channel, which reaches a different internal reader than the compile-time import.
        final TransformerFactory factory = secureTransformerFactory(warnings);
        factory.setURIResolver(
                (href, base) -> href != null && href.endsWith("referenced.xml") ? AttackTestSupport.resourceSource("referenced-with-entity.xml") : null);
        // Same undeclared-entity outcome as the import above: skipped, never expanded.
        final StringWriter sink = new StringWriter();
        final Templates templates = factory.newTemplates(AttackTestSupport.resourceSource("with-document.xsl"));
        final Transformer transformer = templates.newTransformer();
        transformer.transform(AttackTestSupport.streamSource("<root/>"),
                new StreamResult(sink));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "opted-in document() resource leaked its external entity");
        // entity expansion happens at run time
        if (isEmittingWarnings(true, templates)) {
            assertIgnoredEntityTransformer(warnings);
        }
    }

    @Test
    @Tag("trax")
    void transformerParsesOptedInImportSecured() throws Exception {
        final Collection<TransformerException> warnings = new ArrayList<>();
        // The opted-in module carries an external DTD reference; parsed on the floor the DTD is empty, so its entity cannot expand into the output.
        final TransformerFactory factory = secureTransformerFactory(warnings);
        factory.setURIResolver(
                (href, base) -> href != null && href.endsWith("included.xsl") ? AttackTestSupport.resourceSource("included-with-entity.xsl") : null);
        // The emptied DTD leaves the entity undeclared — only a validity violation when an external subset is
        // declared — so every non-validating parser skips it and the transform deterministically completes.
        final StringWriter sink = new StringWriter();
        final Templates templates = factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl"));
        templates.newTransformer().transform(AttackTestSupport.streamSource("<root/>"),
                new StreamResult(sink));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "opted-in stylesheet import leaked its external entity");
        // entity expansion happens at compile time
        if (isEmittingWarnings(false, templates)) {
            assertIgnoredEntityTransformer(warnings);
        }
    }

    static boolean isEmittingWarnings(boolean isRuntime, final Templates templates) {
        if (templates instanceof SecureTemplates) {
            Templates wrappedTemplates = ((SecureTemplates) templates).getDelegate();
            if (wrappedTemplates.getClass().getName().equals("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl")) {
                // uses either ErrorHandlerProxy at compile time which throws on warnings 
                // (https://github.com/openjdk/jdk/blob/440be4ff6ce97d9a125c2c7c49ace140a8ddc0b7/src/java.xml/share/classes/com/sun/org/apache/xalan/internal/xsltc/compiler/Parser.java#L435)
                // or at runtime uses com.sun.org.apache.xml.internal.dtm.ref.sax2dtm.SAX2DTM2#warning(...) which just emits to System.err
                return false;
            } else if (wrappedTemplates.getClass().getName().startsWith("org.apache.xalan.internal.xsltc.trax.TemplatesImpl")) {
                // warnings swallowed at compile time
                return isRuntime;
            } else if (wrappedTemplates.getClass().getName().startsWith("net.sf.saxon")) {
                // net.sf.saxon.lib.StandardErrorHandler used which swallows warnings during run time.
                return !isRuntime;
            }
        } else {
            throw new IllegalArgumentException("Expected SecureTemplates, got " + templates.getClass().getName());
        }
        return false;
    }

    @Test
    @Tag("trax")
    void transformerResolvesAllowListed() {
        final Collection<TransformerException> warnings = new ArrayList<>();
        // with-import.xsl imports included.xsl, so it compiles only if the import is resolved.
        final TransformerFactory factory = secureTransformerFactory(warnings);
        factory.setURIResolver(XSL_ALLOW_LIST);
        assertParseSucceeds(() -> factory.newTemplates(AttackTestSupport.resourceSource("with-import.xsl")), "Stylesheet import via caller resolver");
        assertNoIgnoredEntityTransformer(warnings);
    }
}
