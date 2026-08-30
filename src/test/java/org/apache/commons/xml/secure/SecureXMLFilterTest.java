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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLFilterImpl;

@Tag("trax")
class SecureXMLFilterTest {

    private static SecureXMLFilter filter() throws Exception {
        final Templates templates = TransformerFactory.newInstance().newTemplates(new StreamSource(new StringReader(
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template match='@*|node()'><xsl:copy><xsl:apply-templates select='@*|node()'/></xsl:copy></xsl:template></xsl:stylesheet>")));
        return new SecureXMLFilter(new SecureTemplates(templates, null, null, false));
    }

    @Test
    void propagatesSaxFailuresFromTheTransformationHandler() throws Exception {
        final SecureXMLFilter filter = filter();
        filter.setContentHandler(new DefaultHandler() {

            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
                    throws SAXException {
                throw new SAXException("handler");
            }
        });
        final SAXException exception = assertThrows(SAXException.class, () -> filter.parse(new InputSource(new StringReader("<root/>"))));
        assertEquals("handler", exception.getMessage());
    }

    @Test
    void reportsWarningErrorAndFatalErrorUsingSaxShape() throws Exception {
        final SecureXMLFilter filter = filter();
        final AtomicInteger reports = new AtomicInteger();
        filter.setErrorHandler(new DefaultHandler() {

            @Override
            public void error(final SAXParseException e) {
                reports.incrementAndGet();
            }

            @Override
            public void fatalError(final SAXParseException e) {
                reports.incrementAndGet();
            }

            @Override
            public void warning(final SAXParseException e) {
                reports.incrementAndGet();
            }
        });
        filter.warning(new TransformerException("warning"));
        filter.error(new TransformerException("error", new SAXParseException("cause", null)));
        final TransformerException fatal = new TransformerException("fatal");
        assertSame(fatal, assertThrows(TransformerException.class, () -> filter.fatalError(fatal)));
        assertEquals(3, reports.get());
    }

    @Test
    void requiresContentHandlerAndTransformsWhenOneIsSet() throws Exception {
        final SecureXMLFilter filter = filter();
        assertThrows(SAXException.class, () -> filter.parse(new InputSource(new StringReader("<root/>"))));
        filter.setContentHandler(new DefaultHandler());
        filter.parse(new InputSource(new StringReader("<root/>")));
    }

    @Test
    void rethrowsAnIoExceptionFromTheTransformer() throws Exception {
        final Transformer delegate = TransformerFactory.newInstance().newTransformer();
        final Templates templates = new Templates() {

            @Override
            public Properties getOutputProperties() {
                return delegate.getOutputProperties();
            }

            @Override
            public Transformer newTransformer() {
                return new Transformer() {

                    @Override
                    public void clearParameters() {
                        delegate.clearParameters();
                    }

                    @Override
                    public ErrorListener getErrorListener() {
                        return delegate.getErrorListener();
                    }

                    @Override
                    public Properties getOutputProperties() {
                        return delegate.getOutputProperties();
                    }

                    @Override
                    public String getOutputProperty(final String name) {
                        return delegate.getOutputProperty(name);
                    }

                    @Override
                    public Object getParameter(final String name) {
                        return delegate.getParameter(name);
                    }

                    @Override
                    public URIResolver getURIResolver() {
                        return delegate.getURIResolver();
                    }

                    @Override
                    public void reset() {
                        delegate.reset();
                    }

                    @Override
                    public void setErrorListener(final ErrorListener listener) {
                        delegate.setErrorListener(listener);
                    }

                    @Override
                    public void setOutputProperties(final Properties properties) {
                        delegate.setOutputProperties(properties);
                    }

                    @Override
                    public void setOutputProperty(final String name, final String value) {
                        delegate.setOutputProperty(name, value);
                    }

                    @Override
                    public void setParameter(final String name, final Object value) {
                        delegate.setParameter(name, value);
                    }

                    @Override
                    public void setURIResolver(final URIResolver resolver) {
                        delegate.setURIResolver(resolver);
                    }

                    @Override
                    public void transform(final Source source, final Result result) throws TransformerException {
                        throw new TransformerException(new IOException("transform"));
                    }
                };
            }
        };
        final SecureXMLFilter filter = new SecureXMLFilter(new SecureTemplates(templates, null, null, false));
        filter.setContentHandler(new DefaultHandler());
        final IOException exception = assertThrows(IOException.class, () -> filter.parse(new InputSource(new StringReader("<root/>"))));
        assertEquals("transform", exception.getMessage());
    }

    @Test
    void sendsLexicalEventsToALexicalContentHandler() throws Exception {
        final SecureXMLFilter filter = filter();
        filter.setContentHandler(new DefaultHandler2());
        filter.parse(new InputSource(new StringReader("<root><!--comment--><![CDATA[text]]></root>")));
    }

    @Test
    void wrapsErrorHandlerFailuresAndUsesTheConfiguredParent() throws Exception {
        final SecureXMLFilter filter = filter();
        final SAXException handlerFailure = new SAXException("error handler");
        filter.setErrorHandler(new DefaultHandler() {

            @Override
            public void warning(final SAXParseException e) throws SAXException {
                throw handlerFailure;
            }
        });
        final TransformerException warning = assertThrows(TransformerException.class, () -> filter.warning(new TransformerException("warning")));
        assertSame(handlerFailure, warning.getCause());
        filter.setContentHandler(new DefaultHandler());
        filter.setParent(SecureSAXParserFactory.newXMLReader(false));
        filter.parse(new InputSource(new StringReader("<root/>")));
    }

    @Test
    void surfacesIoFailuresFromTheParentReader() throws Exception {
        final SecureXMLFilter filter = filter();
        filter.setContentHandler(new DefaultHandler());
        filter.setParent(new XMLFilterImpl() {

            @Override
            public void parse(final InputSource input) throws IOException {
                throw new IOException("parent");
            }
        });
        // XSLTC wraps the failure in a SAXException, Xalan hands the filter a cause it rethrows as the original IOException, and Saxon reports a SAXException
        // with no linked cause: the portable contract is that the parse fails with a declared exception instead of returning a truncated result.
        final Exception exception = assertThrows(Exception.class, () -> filter.parse(new InputSource(new StringReader("<root/>"))));
        assertTrue(exception instanceof SAXException || exception instanceof IOException,
                "parse must fail with a declared exception type: " + exception);
    }
}
