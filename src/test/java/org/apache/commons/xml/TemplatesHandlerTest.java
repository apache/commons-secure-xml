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

import java.io.StringWriter;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link TemplatesHandler} products of the secure factory: {@code xsl:include} resolution during the SAX-driven compile sits on the factory's resolver floor,
 * and the {@link Templates} returned by {@link TemplatesHandler#getTemplates()} produce Transformers that carry the floor. The unconfigured control proves the
 * vector leaks without the hardening.
 */
@Tag("trax")
class TemplatesHandlerTest {

    private static String compileAndTransform(final TemplatesHandler handler, final String stylesheet) throws Exception {
        handler.setSystemId(AttackTestSupport.resourceUrl(stylesheet).toString());
        SaxSurfaceTestSupport.feed(handler, SaxSurfaceTestSupport.resourceInput(stylesheet));
        final Templates templates = handler.getTemplates();
        assertNotNull(templates, "stylesheet failed to compile");
        // Build the Transformer: a failed compile does not always throw, only building the transformer surfaces it.
        final Transformer transformer = templates.newTransformer();
        final StringWriter sink = new StringWriter();
        transformer.transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        return sink.toString();
    }

    @Test
    void secureTemplatesHandlerDoesNotLeakDocument() throws Exception {
        // The f004 product path: the Templates from getTemplates() must produce floored Transformers for runtime document().
        final TemplatesHandler handler = SaxSurfaceTestSupport.hardenedFactory().newTemplatesHandler();
        assertFalse(compileAndTransform(handler, "with-document.xsl").contains(AttackTestSupport.LEAKED_MARKER),
                "document() through TemplatesHandler.getTemplates() leaked");
    }

    @Test
    void secureTemplatesHandlerDoesNotLeakInclude() throws Exception {
        final TemplatesHandler handler = SaxSurfaceTestSupport.hardenedFactory().newTemplatesHandler();
        assertFalse(compileAndTransform(handler, "with-include.xsl").contains(AttackTestSupport.LEAKED_MARKER),
                "xsl:include through TemplatesHandler leaked");
    }

    @Test
    void unconfiguredTemplatesHandlerLeaksDocument() throws Exception {
        final TemplatesHandler handler = ((SAXTransformerFactory) TransformerFactory.newInstance()).newTemplatesHandler();
        assertTrue(compileAndTransform(handler, "with-document.xsl").contains(AttackTestSupport.LEAKED_MARKER),
                "unconfigured TemplatesHandler should resolve document()");
    }
}
