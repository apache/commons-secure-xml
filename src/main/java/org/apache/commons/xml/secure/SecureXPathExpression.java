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

import java.util.Objects;

import javax.xml.namespace.QName;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.InputSource;

/**
 * {@link XPathExpression} wrapper that applies the same {@link InputSource} rewrite as {@link SecureXPath} to the compiled evaluation entry points.
 * <p>
 * {@link SecureXPath#compile(String)} returns one of these, so {@link #evaluate(InputSource)} and {@link #evaluate(InputSource, QName)} build the document
 * through a secure, namespace-aware parser instead of the engine's own; the {@code evaluateExpression} default methods added by Java 9 route through these
 * overloads as well.
 * </p>
 */
final class SecureXPathExpression implements XPathExpression {

    private final XPathExpression delegate;

    /**
     * Snapshot of the factory's {@code jdk.xml.overrideDefaultParser} outcome, inherited from the {@link SecureXPath} that compiled this expression.
     */
    private final boolean overrideDefaultParser;

    /**
     * Constructs a new instance.
     *
     * @param delegate         the delegate to wrap; must not be {@code null}
     * @param overrideDefaultParser whether the {@link InputSource} document builds should use the pluggable parser lookup instead of the platform's built-in parser
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    SecureXPathExpression(final XPathExpression delegate, final boolean overrideDefaultParser) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.overrideDefaultParser = overrideDefaultParser;
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated
     */
    @Override
    public String evaluate(final InputSource source) throws XPathExpressionException {
        return delegate.evaluate(SecureXPath.parse(source, overrideDefaultParser));
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated
     */
    @Override
    public Object evaluate(final InputSource source, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(SecureXPath.parse(source, overrideDefaultParser), returnType);
    }

    @Override
    public String evaluate(final Object item) throws XPathExpressionException {
        return delegate.evaluate(item);
    }

    @Override
    public Object evaluate(final Object item, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(item, returnType);
    }
}
