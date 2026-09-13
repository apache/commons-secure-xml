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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link TransformerHandler} products of the secure factory sit on the resolver floor: a stylesheet's runtime {@code document()} resolves to empty content
 * whether the transform runs through the handler's SAX events or through the {@link TransformerHandler#getTransformer()} bypass. The unconfigured control
 * proves the vector leaks without the securing. The {@code Templates} overload doubles as a regression test for handing the factory a wrapped
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
    void secureGetTransformerDoesNotLeakDocument() throws Exception {
        // The f004 bypass: pull the inner Transformer out of the handler and transform directly; the floor must ride along.
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.secureFactory();
        final TransformerHandler handler = factory.newTransformerHandler(AttackTestSupport.resourceSource("with-document.xsl"));
        final StringWriter sink = new StringWriter();
        handler.getTransformer().transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "document() through getTransformer() leaked");
    }

    @Test
    void secureTransformerHandlerDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.secureFactory();
        final TransformerHandler handler = factory.newTransformerHandler(AttackTestSupport.resourceSource("with-document.xsl"));
        assertFalse(transformViaHandler(handler).contains(AttackTestSupport.LEAKED_MARKER), "document() through TransformerHandler leaked");
    }

    /** A caller's own Templates that only configures the Transformer it hands out, the shape Apache CXF's XSLTJaxbProvider builds. */
    private static Templates callersTemplates(final Templates compiled, final URIResolver carried) {
        return new Templates() {

            @Override
            public Properties getOutputProperties() {
                return compiled.getOutputProperties();
            }

            @Override
            public Transformer newTransformer() throws TransformerConfigurationException {
                final Transformer transformer = compiled.newTransformer();
                transformer.setURIResolver(carried);
                return transformer;
            }
        };
    }

    /** Skips the test where the implementation refuses a Templates it did not compile itself, as Saxon does. */
    private static TransformerHandler assumeAcceptsForeignImplementation(final Templates callers) {
        try {
            return ((SAXTransformerFactory) TransformerFactory.newInstance()).newTransformerHandler(callers);
        } catch (final TransformerConfigurationException e) {
            Assumptions.abort("the implementation does not accept a foreign Templates: " + e.getMessage());
            return null;
        }
    }

    @Test
    void secureHandlerKeepsAResolverTheCallersTemplatesSet() throws Exception {
        final Templates compiled = TransformerFactory.newInstance().newTemplates(AttackTestSupport.resourceSource("with-document.xsl"));
        final URIResolver carried = (href, base) -> null;
        final Templates callers = callersTemplates(compiled, carried);
        // What the implementation itself ends up with: XSLTC leaves the caller's resolver in place, Apache Xalan overwrites it with its factory's own.
        final TransformerHandler nativeTransformerHandler = assumeAcceptsForeignImplementation(callers);
        Assumptions.assumeTrue(nativeTransformerHandler.getTransformer().getURIResolver() == carried,
                "the implementation does not keep a resolver set by the caller's Templates");

        final TransformerHandler handler = SaxSurfaceTestSupport.secureFactory().newTransformerHandler(callers);
        assertSame(carried, handler.getTransformer().getURIResolver(), "the securing must not lose a resolver the implementation kept");
        assertFalse(transformViaHandler(handler).contains(AttackTestSupport.LEAKED_MARKER), "what that resolver declined must not be fetched");
    }

    @Test
    void secureTransformerHandlerFromTemplatesDoesNotLeakDocument() throws Exception {
        final SAXTransformerFactory factory = SaxSurfaceTestSupport.secureFactory();
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
