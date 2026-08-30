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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLFilter;

@Tag("trax")
class SecureTransformerFactoryTest {

    private static class NullProductsFactory extends SAXTransformerFactory {

        private final SAXTransformerFactory delegate = (SAXTransformerFactory) TransformerFactory.newInstance();

        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Source getAssociatedStylesheet(final Source source, final String media, final String title, final String charset)
                throws TransformerConfigurationException {
            return delegate.getAssociatedStylesheet(source, media, title, charset);
        }

        @Override
        public Object getAttribute(final String name) {
            return attributes.get(name);
        }

        @Override
        public ErrorListener getErrorListener() {
            return delegate.getErrorListener();
        }

        @Override
        public boolean getFeature(final String name) {
            return delegate.getFeature(name);
        }

        @Override
        public URIResolver getURIResolver() {
            return delegate.getURIResolver();
        }

        @Override
        public Templates newTemplates(final Source source) {
            return null;
        }

        @Override
        public TemplatesHandler newTemplatesHandler() {
            return null;
        }

        @Override
        public Transformer newTransformer() {
            return null;
        }

        @Override
        public Transformer newTransformer(final Source source) {
            return null;
        }

        @Override
        public TransformerHandler newTransformerHandler() {
            return null;
        }

        @Override
        public TransformerHandler newTransformerHandler(final Source source) {
            return null;
        }

        @Override
        public TransformerHandler newTransformerHandler(final Templates templates) {
            return null;
        }

        @Override
        public XMLFilter newXMLFilter(final Source source) {
            return null;
        }

        @Override
        public XMLFilter newXMLFilter(final Templates templates) {
            return null;
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            attributes.put(name, value);
        }

        @Override
        public void setErrorListener(final ErrorListener listener) {
            delegate.setErrorListener(listener);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws TransformerConfigurationException {
            delegate.setFeature(name, value);
        }

        @Override
        public void setURIResolver(final URIResolver resolver) {
            delegate.setURIResolver(resolver);
        }
    }

    private static final class RejectingFeatureFactory extends NullProductsFactory {

        @Override
        public void setFeature(final String name, final boolean value) throws TransformerConfigurationException {
            throw new TransformerConfigurationException(name);
        }
    }

    private static void associatedStylesheet(final SAXTransformerFactory factory, final Source source) throws Exception {
        try {
            factory.getAssociatedStylesheet(source, null, null, null);
        } catch (final TransformerConfigurationException | NullPointerException expected) {
            // Saxon signals no matching PI with TransformerConfigurationException; Xalan rejects a source without an InputSource.
        }
    }

    private static StreamSource stylesheet() {
        return new StreamSource(new StringReader(
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" + "<xsl:template match='/'/></xsl:stylesheet>"));
    }

    @Test
    void preservesNullResultsFromEveryWrappableProduct() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) SecureTransformerFactory.secure(new NullProductsFactory());
        final Templates templates = TransformerFactory.newInstance().newTemplates(stylesheet());
        assertNull(factory.newTemplates(stylesheet()));
        assertNull(factory.newTemplatesHandler());
        assertNull(factory.newTransformer());
        assertNull(factory.newTransformer(stylesheet()));
        assertNull(factory.newTransformerHandler());
        assertNull(factory.newTransformerHandler(stylesheet()));
        assertNull(factory.newTransformerHandler(templates));
        assertNull(factory.newXMLFilter(stylesheet()));
        factory.setAttribute("test", "value");
        assertEquals("value", factory.getAttribute("test"));
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }

    @Test
    void rejectsDelegatesThatCannotEnableSecureProcessing() {
        assertThrows(SecureException.class, () -> SecureTransformerFactory.secure(new RejectingFeatureFactory()));
    }

    @Test
    void securesAssociatedStylesheetSourcesOfEverySupportedShape() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) SecureTransformerFactory.newInstance();
        associatedStylesheet(factory, new StreamSource(new StringReader("<root/>")));
        associatedStylesheet(factory, new StreamSource(new StringReader("<root>")));
        associatedStylesheet(factory, new SAXSource(new InputSource(new StringReader("<root/>"))));
        associatedStylesheet(factory, new SAXSource());
        associatedStylesheet(factory,
                new SAXSource(SecureSAXParserFactory.newXMLReader(false), new InputSource(new StringReader("<root/>"))));
        associatedStylesheet(factory,
                new DOMSource(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()));
    }

    @Test
    void wrapsEveryStandardAndSaxFactoryProduct() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) SecureTransformerFactory.newInstance();
        final URIResolver resolver = (href, base) -> null;
        factory.setURIResolver(resolver);
        assertSame(resolver, factory.getURIResolver());
        factory.setErrorListener(factory.getErrorListener());
        try {
            factory.setAttribute("indent-number", 2);
            // XSLTC quirk: the attribute is settable but not readable.
            assertThrows(IllegalArgumentException.class, () -> factory.getAttribute("indent-number"));
        } catch (final IllegalArgumentException e) {
            // Saxon and Xalan reject the XSLTC-only attribute at set time; the delegate's rejection is itself the forwarding proof.
        }
        final Templates templates = factory.newTemplates(stylesheet());
        assertInstanceOf(SecureTemplates.class, templates);
        assertInstanceOf(SecureTransformer.class, factory.newTransformer());
        assertInstanceOf(SecureTransformer.class, factory.newTransformer(stylesheet()));
        assertInstanceOf(SecureTemplatesHandler.class, factory.newTemplatesHandler());
        assertInstanceOf(SecureTransformerHandler.class, factory.newTransformerHandler());
        assertInstanceOf(SecureTransformerHandler.class, factory.newTransformerHandler(stylesheet()));
        assertInstanceOf(SecureTransformerHandler.class, factory.newTransformerHandler(templates));
        assertInstanceOf(SecureXMLFilter.class, factory.newXMLFilter(stylesheet()));
        assertInstanceOf(SecureXMLFilter.class, factory.newXMLFilter(templates));
        final Templates rawTemplates = TransformerFactory.newInstance().newTemplates(stylesheet());
        assertInstanceOf(SecureXMLFilter.class, factory.newXMLFilter(rawTemplates));
        associatedStylesheet(factory, new StreamSource(new StringReader("<root/>")));
        assertInstanceOf(SecureTransformer.class, SecureTransformerFactory.newInstance().newTransformer());
    }
}
