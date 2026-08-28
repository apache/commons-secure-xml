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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

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
    void secureFilterFromTemplatesDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.secureFactory();
        final Templates templates = factory.newTemplates(AttackTestSupport.resourceSource("with-document.xsl"));
        assertNotNull(templates, "stylesheet failed to compile");
        final XMLFilter filter = factory.newXMLFilter(templates);
        assertFalse(filterAndCapture(filter, "<root/>").contains(AttackTestSupport.LEAKED_MARKER), "document() through XMLFilter(Templates) leaked");
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
