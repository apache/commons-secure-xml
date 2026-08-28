/*
 *
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
import static org.apache.commons.xml.AttackTestSupport.captureCharacters;
import static org.apache.commons.xml.AttackTestSupport.inputSource;
import static org.apache.commons.xml.AttackTestSupport.resourceUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Tests that XInclude resolution is blocked by default on factories from {@link org.apache.commons.xml}, and that callers can
 * allow-list specific resources via an {@link EntityResolver}.
 *
 * <p>Each case is exercised in both {@code parse="xml"} and {@code parse="text"} modes, and for both DOM and SAX
 * paths. XInclude resolution requires namespace-aware processing; the baseline tests set it explicitly, and the
 * secure factory tests rely on the underlying JAXP implementation being namespace-aware enough to recognize elements
 * in the {@code http://www.w3.org/2001/XInclude} namespace.</p>
 */
class XIncludeTest {

    /**
     * Allow-lists the two fixture URLs, returning the appropriate in-memory content for each: {@link #RESOLVED_MARKER}
     * wrapped as XML for {@link #REFERENCED_XML}, and as plain text for {@link #REFERENCED_TEXT}. Anything else returns
     * {@code null} so the hardening's ignore-all floor empties it. Mirrors a caller allow-listing trusted resources.
     */
    private static final class AllowListResolver implements EntityResolver {

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId) {
            final InputSource source;
            if (REFERENCED_XML.equals(systemId)) {
                source = new InputSource(new StringReader("<allowed>" + RESOLVED_MARKER + "</allowed>"));
            } else if (REFERENCED_TEXT.equals(systemId)) {
                source = new InputSource(new StringReader(RESOLVED_MARKER));
            } else {
                return null;
            }
            source.setPublicId(publicId);
            source.setSystemId(systemId);
            return source;
        }
    }

    /** Absolute URL of the XML fixture pulled in by {@code parse="xml"} includes; carries {@link AttackTestSupport#LEAKED_MARKER}. */
    private static final String REFERENCED_XML = resourceUrl("referenced.xml").toString();

    /** Absolute URL of the text fixture pulled in by {@code parse="text"} includes; carries {@link AttackTestSupport#LEAKED_MARKER}. */
    private static final String REFERENCED_TEXT = resourceUrl("referenced.txt").toString();

    /** Content the allow-list resolver returns for an allowed include; its presence proves the caller's resolver was consulted. */
    private static final String RESOLVED_MARKER = "XINCLUDE-RESOLVED-905bbbce-16ee-4a0c-b165-d1f8c663934c";

    /** Resolver that resolves nothing, so the hardening's ignore-all floor must empty every lookup and never leak. */
    private static final EntityResolver NO_OP_RESOLVER = (publicId, systemId) -> null;

    /**
     * Enables XInclude on the factory under test, skipping the test when the platform refuses.
     *
     * <p>On Android{@code setXIncludeAware(true)} always throws {@link UnsupportedOperationException}.</p>
     */
    private static void assumeXIncludeAware(final DocumentBuilderFactory factory) {
        try {
            factory.setXIncludeAware(true);
        } catch (final UnsupportedOperationException e) {
            Assumptions.abort("XInclude not supported on this platform");
        }
    }

    /**
     * Enables XInclude on the factory under test, skipping the test when the platform refuses.
     *
     * <p>On Android{@code setXIncludeAware(true)} always throws {@link UnsupportedOperationException}.</p>
     */
    private static void assumeXIncludeAware(final SAXParserFactory factory) {
        try {
            factory.setXIncludeAware(true);
        } catch (final UnsupportedOperationException e) {
            Assumptions.abort("XInclude not supported on this platform");
        }
    }

    /** XML wrapper for xi:include in the given {@code parse} mode referencing {@code href}. */
    private static String xiIncludeXml(final String href, final String parseMode) {
        return "<?xml version=\"1.0\"?>\n"
                + "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n"
                + "  <xi:include href=\"" + href + "\" parse=\"" + parseMode + "\"/>\n"
                + "</root>";
    }

    //region Baseline: unhardened JAXP resolves the include

    @Test
    @Tag("dom")
    void baselineDomLeaksParseText() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_TEXT, "text"));

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final Document doc = factory.newDocumentBuilder().parse(input);
        final String text = doc.getDocumentElement().getTextContent();
        assertTrue(text != null && text.contains(LEAKED_MARKER),
                "Baseline DOM parse=text should leak marker; got: " + text);
    }

    @Test
    @Tag("dom")
    void baselineDomLeaksParseXml() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final Document doc = factory.newDocumentBuilder().parse(input);
        final String text = doc.getDocumentElement().getTextContent();
        assertEquals(LEAKED_MARKER, text.trim(),
                "Baseline DOM parse=xml should leak marker; got: " + text);
    }

    @Test
    @Tag("sax")
    void baselineSaxLeaksParseText() throws Exception {
        final String input = xiIncludeXml(REFERENCED_TEXT, "text");

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final String captured = captureCharacters(factory.newSAXParser().getXMLReader(), input);
        assertTrue(captured.contains(LEAKED_MARKER),
                "Baseline SAX parse=text should leak marker; got: " + captured);
    }

    @Test
    @Tag("sax")
    void baselineSaxLeaksParseXml() throws Exception {
        final String input = xiIncludeXml(REFERENCED_XML, "xml");

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final String captured = captureCharacters(factory.newSAXParser().getXMLReader(), input);
        assertEquals(LEAKED_MARKER, captured.trim(),
                "Baseline SAX parse=xml should leak marker; got: " + captured);
    }

    @Test
    @Tag("dom")
    void secureDomBlocksParseText() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_TEXT, "text"));

        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final Document doc = factory.newDocumentBuilder().parse(input);
        final String text = doc.getDocumentElement().getTextContent();
        assertFalse(text.contains(LEAKED_MARKER),
                "Secured DOM parse=text must resolve the include to empty, not leak; got: " + text);
    }

    @Test
    @Tag("dom")
    void secureDomBlocksParseXml() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        assertThrows(SAXException.class, () -> {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(input);
        }, "Secured DOM parse=xml should throw");
    }

    @Test
    @Tag("dom")
    void secureDomNullResolverDoesNotLeak() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(NO_OP_RESOLVER);
        assertThrows(SAXException.class, () -> builder.parse(input),
                "a resolver that returns null must not leak: the ignore-all floor blocks the real href");
    }

    @Test
    @Tag("dom")
    void secureDomWithAllowListResolvesParseText() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_TEXT, "text"));

        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new AllowListResolver());
        final Document doc = builder.parse(input);
        assertEquals(RESOLVED_MARKER, doc.getDocumentElement().getTextContent().trim(),
                "DOM parse=text with allow-list should resolve to the resolver's content");
    }

    @Test
    @Tag("dom")
    void secureDomWithAllowListResolvesParseXml() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new AllowListResolver());
        final Document doc = builder.parse(input);
        assertEquals(RESOLVED_MARKER, doc.getDocumentElement().getTextContent().trim(),
                "DOM parse=xml with allow-list should resolve to the resolver's content");
    }

    @Test
    @Tag("sax")
    void secureSaxBlocksParseText() throws Exception {
        final String input = xiIncludeXml(REFERENCED_TEXT, "text");

        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final String captured = captureCharacters(factory.newSAXParser().getXMLReader(), input);
        assertFalse(captured.contains(LEAKED_MARKER),
                "Secured SAX parse=text must resolve the include to empty, not leak; got: " + captured);
    }

    @Test
    @Tag("sax")
    void secureSaxBlocksParseXml() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        assertThrows(SAXException.class, () -> {
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.parse(input);
        }, "Secured SAX parse=xml should throw");
    }

    @Test
    @Tag("sax")
    void secureSaxNullResolverDoesNotLeak() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver(NO_OP_RESOLVER);
        assertThrows(SAXException.class, () -> reader.parse(input),
                "a resolver that returns null must not leak: the ignore-all floor blocks the real href");
    }

    @Test
    @Tag("sax")
    void secureSaxWithAllowListResolvesParseText() throws Exception {
        final String input = xiIncludeXml(REFERENCED_TEXT, "text");

        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver(new AllowListResolver());
        final String captured = captureCharacters(reader, input);
        assertEquals(RESOLVED_MARKER, captured.trim(),
                "SAX parse=text with allow-list should resolve to the resolver's content");
    }

    @Test
    @Tag("sax")
    void secureSaxWithAllowListResolvesParseXml() throws Exception {
        final String input = xiIncludeXml(REFERENCED_XML, "xml");

        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        assumeXIncludeAware(factory);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver(new AllowListResolver());
        final String captured = captureCharacters(reader, input);
        assertEquals(RESOLVED_MARKER, captured.trim(),
                "SAX parse=xml with allow-list should resolve to the resolver's content");
    }

    //endregion

    //region hardenReader: reader with XInclude enabled before securing is blocked (the internal recipe behind the secure factory and SaxonProvider)

    @Test
    @Tag("sax")
    void hardenReaderAllowListResolvesParseXml() throws Exception {
        final String input = xiIncludeXml(REFERENCED_XML, "xml");

        final SAXParserFactory unhardenedFactory = SAXParserFactory.newInstance();
        unhardenedFactory.setNamespaceAware(true);
        assumeXIncludeAware(unhardenedFactory);
        final XMLReader reader = SecureSAXParserFactory.secure(unhardenedFactory.newSAXParser().getXMLReader());
        reader.setEntityResolver(new AllowListResolver());
        final String captured = captureCharacters(reader, input);
        assertEquals(RESOLVED_MARKER, captured.trim(),
                "hardenReader + allow-list should resolve to the resolver's content on a reader with XInclude already enabled");
    }

    @Test
    @Tag("sax")
    void hardenReaderBlocksParseText() throws Exception {
        final String input = xiIncludeXml(REFERENCED_TEXT, "text");

        final SAXParserFactory unhardenedFactory = SAXParserFactory.newInstance();
        unhardenedFactory.setNamespaceAware(true);
        assumeXIncludeAware(unhardenedFactory);
        final XMLReader reader = SecureSAXParserFactory.secure(unhardenedFactory.newSAXParser().getXMLReader());
        final String captured = captureCharacters(reader, input);
        assertFalse(captured.contains(LEAKED_MARKER),
                "hardenReader parse=text must resolve the include to empty, not leak; got: " + captured);
    }

    @Test
    @Tag("sax")
    void hardenReaderBlocksParseXml() throws Exception {
        final InputSource input = inputSource(xiIncludeXml(REFERENCED_XML, "xml"));

        // Reader from an unhardened factory that already has XInclude enabled
        final SAXParserFactory unhardenedFactory = SAXParserFactory.newInstance();
        unhardenedFactory.setNamespaceAware(true);
        assumeXIncludeAware(unhardenedFactory);
        final XMLReader reader = SecureSAXParserFactory.secure(unhardenedFactory.newSAXParser().getXMLReader());
        assertThrows(SAXException.class, () -> reader.parse(input),
                "hardenReader should block XInclude parse=xml on reader with XInclude already enabled");
    }

    //endregion
}
