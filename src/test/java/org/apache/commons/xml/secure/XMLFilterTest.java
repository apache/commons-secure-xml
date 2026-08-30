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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link XMLFilter} products of the secure factory: the input document is parsed by a secure reader (never a self-provisioned permissive one), and the
 * transformation runs on the resolver floor, so neither an external entity in the input nor a stylesheet's runtime {@code document()} fetches. The
 * unconfigured controls prove both vectors leak without the securing. The {@code Templates} overload doubles as a regression test for handing the factory a
 * wrapped {@code Templates}.
 */
@Tag("trax")
class XMLFilterTest {

    /** Copies the input through unchanged, so external-entity content in the input would surface in the filter's output. */
    private static final String IDENTITY_XSLT = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
            + "  <xsl:template match=\"/\"><xsl:copy-of select=\".\"/></xsl:template>\n"
            + "</xsl:stylesheet>";

    /** Asserts no SAXException is buried beneath the thrown one, proving {@code parse} rethrows originals instead of re-wrapping them. */
    private static void assertNotReWrapped(final SAXException thrown) {
        for (Throwable cause = causeOf(thrown); cause != null; cause = causeOf(cause)) {
            assertFalse(cause instanceof SAXException, "original SAXException should be rethrown, not re-wrapped: " + thrown);
        }
    }

    /** Follows {@link SAXException#getException()} where present: Android's SAXException does not link the embedded exception into {@code getCause()}. */
    private static Throwable causeOf(final Throwable throwable) {
        if (throwable instanceof SAXException && ((SAXException) throwable).getException() != null) {
            return ((SAXException) throwable).getException();
        }
        return throwable.getCause();
    }

    private static String entityPayload() {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [\n"
                + "  <!ENTITY xxe SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.txt") + "\">\n"
                + "]>\n"
                + AttackTestSupport.xmlBody("&xxe;") + "\n";
    }

    private static String filterAndCapture(final XMLFilter filter, final String input) throws Exception {
        final StringBuilder text = new StringBuilder();
        filter.setContentHandler(AttackTestSupport.capturingHandler(text));
        filter.parse(new InputSource(new StringReader(input)));
        return text.toString();
    }

    /**
     * On Android, hand the unconfigured filter a permissive parent so Xalan's {@code TrAXFilter} uses it instead of self-provisioning an Expat reader on which
     * it enables {@code namespace-prefixes}, a feature Android's libexpat accepts but fails on mid-parse. The parent stays permissive (no floor), so the leak
     * these controls assert still occurs.
     */
    private static void setPermissiveParentOnAndroid(final XMLFilter filter) {
        if (AttackTestSupport.IS_ANDROID) {
            filter.setParent(AttackTestSupport.permissiveReader());
        }
    }

    @Test
    void secureFilterDoesNotLeakDocument() throws Exception {
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.resourceSource("with-document.xsl"));
        assertFalse(filterAndCapture(filter, "<root/>").contains(AttackTestSupport.LEAKED_MARKER), "document() through XMLFilter leaked");
    }

    @Test
    void secureFilterDoesNotLeakExternalEntity() throws Exception {
        // The f003 vector: with no caller-set parent, the input must be parsed by a secure reader, not a self-provisioned permissive one.
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        assertFalse(filterAndCapture(filter, entityPayload()).contains(AttackTestSupport.LEAKED_MARKER), "external entity through XMLFilter leaked");
    }

    @Test
    void secureFilterDoesNotReWrapParseError() throws Exception {
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        filter.setContentHandler(AttackTestSupport.capturingHandler(new StringBuilder()));
        filter.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        final SAXException e = assertThrows(SAXException.class, () -> filter.parse(new InputSource(new StringReader("<root>"))));
        assertNotReWrapped(e);
    }

    @Test
    void secureFilterFromTemplatesDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.secureFactory();
        final Templates templates = factory.newTemplates(AttackTestSupport.resourceSource("with-document.xsl"));
        assertNotNull(templates, "stylesheet failed to compile");
        final XMLFilter filter = factory.newXMLFilter(templates);
        assertFalse(filterAndCapture(filter, "<root/>").contains(AttackTestSupport.LEAKED_MARKER), "document() through XMLFilter(Templates) leaked");
    }

    @Test
    void secureFilterRethrowsHandlerSAXException() throws Exception {
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        final SAXException handlerFailure = new SAXException("handler failure");
        filter.setContentHandler(new DefaultHandler() {
            @Override
            public void startDocument() throws SAXException {
                throw handlerFailure;
            }
        });
        filter.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        final SAXException e = assertThrows(SAXException.class, () -> filter.parse(new InputSource(new StringReader("<root/>"))));
        // Xalan wraps the handler's exception in its own SAXParseException, so assert on the chain: the original must be present and no TrAX wrapper above it.
        boolean found = false;
        for (Throwable cause = e; cause != null; cause = causeOf(cause)) {
            assertFalse(cause instanceof TransformerException, "handler failure should not come back wrapped in TrAX exceptions: " + e);
            found |= cause == handlerFailure;
        }
        assertTrue(found, "the handler's own SAXException should surface in the cause chain: " + e);
    }

    @Test
    void secureFilterRoutesEntityResolverToParent() throws Exception {
        // parse must wire the caller-set EntityResolver to the parent reader, chaining it onto the floor so a caller can opt a specific entity in.
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Android's Expat does not resolve the external general entity here");
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        filter.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("resolved-by-caller")));
        final String output = filterAndCapture(filter, entityPayload());
        assertTrue(output.contains("resolved-by-caller"), "caller-set EntityResolver should opt the external entity in through the parent");
        assertFalse(output.contains(AttackTestSupport.LEAKED_MARKER), "the real external resource must not be fetched");
    }

    @Test
    void secureFilterWiresCallbacksToParent() throws Exception {
        // parse must perform the XMLFilterImpl.setupParse wiring on the parent for the resolver, DTD and error callbacks (the ContentHandler is owned by the
        // transformer). The wiring calls are asserted directly on a recording parent: which of them the implementation later consults or overwrites varies.
        final XMLFilter filter = SaxSurfaceTestSupport.secureFactory().newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        final List<Object> wired = new ArrayList<>();
        final XMLFilterImpl parent = new XMLFilterImpl() {

            @Override
            public boolean getFeature(final String name) {
                // Accept the namespace probes implementations make on a SAXSource reader; there is no parent to delegate to.
                return "http://xml.org/sax/features/namespaces".equals(name);
            }

            @Override
            public Object getProperty(final String name) {
                return null;
            }

            @Override
            public void parse(final InputSource input) throws SAXException {
                // Minimal well-formed document for the transformation to consume; no real parser behind this parent.
                final ContentHandler handler = getContentHandler();
                handler.startDocument();
                handler.startElement("", "root", "root", new AttributesImpl());
                handler.endElement("", "root", "root");
                handler.endDocument();
            }

            @Override
            public void setDTDHandler(final DTDHandler handler) {
                wired.add(handler);
                super.setDTDHandler(handler);
            }

            @Override
            public void setEntityResolver(final EntityResolver resolver) {
                wired.add(resolver);
                super.setEntityResolver(resolver);
            }

            @Override
            public void setErrorHandler(final ErrorHandler handler) {
                wired.add(handler);
                super.setErrorHandler(handler);
            }

            @Override
            public void setFeature(final String name, final boolean value) {
            }

            @Override
            public void setProperty(final String name, final Object value) {
            }
        };
        filter.setParent(parent);
        assertEquals("", filterAndCapture(filter, "<ignored/>"));
        assertEquals(3, wired.stream().filter(callback -> callback == filter).count(),
                "parse should wire the filter as the parent's EntityResolver, DTDHandler and ErrorHandler: " + wired);
    }

    @Test
    void unconfiguredFilterLeaksDocument() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        final XMLFilter filter = factory.newXMLFilter(AttackTestSupport.resourceSource("with-document.xsl"));
        setPermissiveParentOnAndroid(filter);
        assertTrue(filterAndCapture(filter, "<root/>").contains(AttackTestSupport.LEAKED_MARKER),
                "unconfigured XMLFilter should resolve document()");
    }

    @Test
    void unconfiguredFilterLeaksExternalEntity() throws Exception {
        // Android's platform parser does not resolve the input's external general entity in this path, so the vector cannot be demonstrated there; the secure
        // counterpart still runs on Android and must not leak.
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Android's Expat does not resolve the external general entity here");
        final SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        final XMLFilter filter = factory.newXMLFilter(AttackTestSupport.streamSource(IDENTITY_XSLT));
        assertTrue(filterAndCapture(filter, entityPayload()).contains(AttackTestSupport.LEAKED_MARKER),
                "unconfigured XMLFilter should resolve the input's external entity");
    }
}
