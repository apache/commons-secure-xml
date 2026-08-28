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

import java.io.IOException;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * {@link XPath} wrapper that performs the document build behind every {@link InputSource}-taking {@code evaluate} call with a hardened, namespace-aware
 * {@link javax.xml.parsers.DocumentBuilder} and evaluates the delegate against the parsed {@link Document}, so the engine's own parser never runs.
 *
 * <p>The JAXP contract for {@link XPath#evaluate(String, InputSource, QName)} is "build a document from the source, then evaluate against it", and both the
 * stock JDK and Apache Xalan provision an internal parser for that build which {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} on the
 * {@link javax.xml.xpath.XPathFactory} does not reach. Parsing here puts the build on the library's resolver floor: an external reference inside the document
 * resolves to empty content, so it is neither fetched nor leaked, and the evaluation proceeds on whatever the parse produced. {@link #compile(String)} wraps
 * the compiled expression in a {@link HardeningXPathExpression} on the same terms.</p>
 *
 * <p>The {@code evaluateExpression} default methods added to the interface by Java 9 route through the {@code evaluate} overloads overridden here, so they
 * carry the same rewrite on newer runtimes even though this class targets Java 8.</p>
 */
final class HardeningXPath implements XPath {

    /**
     * Parses the source through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder}, mirroring the namespace awareness of the parser the
     * engine would have provisioned.
     *
     * @param source The document to evaluate against.
     * @return The parsed document.
     * @throws NullPointerException     if {@code source} is {@code null}, per the {@link XPath} contract.
     * @throws XPathExpressionException if the source cannot be parsed.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     * @throws HardeningException Thrown if a (non-Andoid) factory cannot support the secure processing feature {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     */
    static Document parse(final InputSource source) throws XPathExpressionException {
        Objects.requireNonNull(source, "source");
        try {
            final DocumentBuilderFactory factory = HardeningDocumentBuilderFactory.harden(DocumentBuilderFactory.newInstance());
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(source);
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new XPathExpressionException(e);
        }
    }

    private final XPath delegate;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningXPath(final XPath delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public XPathExpression compile(final String expression) throws XPathExpressionException {
        final XPathExpression compiled = delegate.compile(expression);
        return compiled == null ? null : new HardeningXPathExpression(compiled);
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public String evaluate(final String expression, final InputSource source) throws XPathExpressionException {
        return delegate.evaluate(expression, parse(source));
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public Object evaluate(final String expression, final InputSource source, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(expression, parse(source), returnType);
    }

    @Override
    public String evaluate(final String expression, final Object item) throws XPathExpressionException {
        return delegate.evaluate(expression, item);
    }

    @Override
    public Object evaluate(final String expression, final Object item, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(expression, item, returnType);
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return delegate.getNamespaceContext();
    }

    @Override
    public XPathFunctionResolver getXPathFunctionResolver() {
        return delegate.getXPathFunctionResolver();
    }

    @Override
    public XPathVariableResolver getXPathVariableResolver() {
        return delegate.getXPathVariableResolver();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public void setNamespaceContext(final NamespaceContext nsContext) {
        delegate.setNamespaceContext(nsContext);
    }

    @Override
    public void setXPathFunctionResolver(final XPathFunctionResolver resolver) {
        delegate.setXPathFunctionResolver(resolver);
    }

    @Override
    public void setXPathVariableResolver(final XPathVariableResolver resolver) {
        delegate.setXPathVariableResolver(resolver);
    }
}
