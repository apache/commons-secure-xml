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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("trax")
class SecureTransformerTest {

    /** A transformer a caller configured before this library saw it, the shape a caller's own Templates hands out. */
    private static SecureTransformer wrap(final URIResolver carried) throws Exception {
        final Transformer delegate = TransformerFactory.newInstance().newTransformer(AttackTestSupport.resourceSource("with-document.xsl"));
        delegate.setURIResolver(carried);
        return new SecureTransformer(delegate, null, null, false);
    }

    @Test
    void adoptsAResolverTheDelegateAlreadyCarries() throws Exception {
        final URIResolver carried = (href, base) -> new StreamSource(new StringReader("<opted-in/>"));
        final SecureTransformer transformer = wrap(carried);
        assertSame(carried, transformer.getURIResolver(), "the resolver the delegate carried must survive the wrapping");
        final StringWriter output = new StringWriter();
        transformer.transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(output));
        assertTrue(output.toString().contains("opted-in"), "the carried resolver must answer document()");
        assertFalse(output.toString().contains(AttackTestSupport.LEAKED_MARKER), "the real resource must not be fetched");
    }

    @Test
    void keepsTheFloorUnderACarriedResolverThatDeclines() throws Exception {
        // Adopting the caller's resolver must not make the floor reachable around: what the resolver declines stays unfetched.
        final SecureTransformer transformer = wrap((href, base) -> null);
        final StringWriter output = new StringWriter();
        transformer.transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(output));
        assertFalse(output.toString().contains(AttackTestSupport.LEAKED_MARKER), "document() the resolver declined must not be fetched");
    }

    @Test
    void carriesTheAdoptedResolverThroughReset() throws Exception {
        final URIResolver carried = (href, base) -> new StreamSource(new StringReader("<opted-in/>"));
        final SecureTransformer transformer = wrap(carried);
        // reset() re-seeds the floor, and the seed is the resolver the delegate carried, not the factory's.
        transformer.reset();
        assertSame(carried, transformer.getURIResolver(), "reset must restore the resolver the delegate carried");
        final StringWriter output = new StringWriter();
        transformer.transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(output));
        assertTrue(output.toString().contains("opted-in"), "the carried resolver must still answer document() after a reset");
        assertFalse(output.toString().contains(AttackTestSupport.LEAKED_MARKER), "the real resource must not be fetched");
    }

    @Test
    void forwardsEveryTransformerMethod() throws Exception {
        final TransformerFactory factory = TransformerFactory.newInstance();
        final SecureTransformer transformer = new SecureTransformer(factory
                .newTemplates(new StreamSource(new StringReader(
                        "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template match='/'/></xsl:stylesheet>")))
                .newTransformer(), null, null, false);
        transformer.clearParameters();
        transformer.setParameter("p", "v");
        assertNotNull(transformer.getParameter("p"));
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        assertNotNull(transformer.getOutputProperty(OutputKeys.METHOD));
        transformer.setOutputProperties(new Properties());
        assertNotNull(transformer.getOutputProperties());
        transformer.setErrorListener(new ErrorListener() {

            @Override
            public void error(final TransformerException e) {
            }

            @Override
            public void fatalError(final TransformerException e) {
            }

            @Override
            public void warning(final TransformerException e) {
            }
        });
        assertNotNull(transformer.getErrorListener());
        transformer.setURIResolver((href, base) -> null);
        assertNotNull(transformer.getURIResolver());
        transformer.transform(new DOMSource(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()), new StreamResult(new StringWriter()));
        transformer.reset();
    }
}
