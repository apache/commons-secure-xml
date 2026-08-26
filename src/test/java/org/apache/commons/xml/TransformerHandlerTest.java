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
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link TransformerHandler} products of the hardened factory sit on the resolver floor: a stylesheet's runtime {@code document()} resolves to empty content
 * whether the transform runs through the handler's SAX events or through the {@link TransformerHandler#getTransformer()} bypass. The unconfigured control
 * proves the vector leaks without the hardening. The {@code Templates} overload doubles as a regression test for handing the factory a wrapped
 * {@code Templates} (implementations cast its {@code newTransformer()} to their own type).
 */
@Tag("trax")
class TransformerHandlerTest {

    private static String transformViaHandler(final TransformerHandler handler) throws Exception {
        final StringWriter sink = new StringWriter();
        handler.setResult(new StreamResult(sink));
        SaxSurfaceTestSupport.feed(handler, SaxSurfaceTestSupport.rootInput());
        return sink.toString();
    }

    @Test
    void hardenedGetTransformerDoesNotLeakDocument() throws Exception {
        // The f004 bypass: pull the inner Transformer out of the handler and transform directly; the floor must ride along.
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.hardenedFactory();
        final TransformerHandler handler = factory.newTransformerHandler(AttackTestSupport.resourceSource("with-document.xsl"));
        final StringWriter sink = new StringWriter();
        handler.getTransformer().transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "document() through getTransformer() leaked");
    }

    @Test
    void hardenedTransformerHandlerDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.hardenedFactory();
        final TransformerHandler handler = factory.newTransformerHandler(AttackTestSupport.resourceSource("with-document.xsl"));
        assertFalse(transformViaHandler(handler).contains(AttackTestSupport.LEAKED_MARKER), "document() through TransformerHandler leaked");
    }

    @Test
    void hardenedTransformerHandlerFromTemplatesDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.hardenedFactory();
        final Templates templates = factory.newTemplates(AttackTestSupport.resourceSource("with-document.xsl"));
        assertNotNull(templates, "stylesheet failed to compile");
        final TransformerHandler handler = factory.newTransformerHandler(templates);
        assertFalse(transformViaHandler(handler).contains(AttackTestSupport.LEAKED_MARKER),
                "document() through TransformerHandler(Templates) leaked");
    }

    @Test
    void unconfiguredTransformerHandlerLeaksDocument() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        final TransformerHandler handler = factory.newTransformerHandler(AttackTestSupport.resourceSource("with-document.xsl"));
        assertTrue(transformViaHandler(handler).contains(AttackTestSupport.LEAKED_MARKER),
                "unconfigured TransformerHandler should resolve document()");
    }
}
