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

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.InputSource;

/**
 * {@link XPathExpression} wrapper that applies the same {@link InputSource} rewrite as {@link HardeningXPath} to the compiled evaluation entry points.
 *
 * <p>{@link HardeningXPath#compile(String)} returns one of these, so {@link #evaluate(InputSource)} and {@link #evaluate(InputSource, QName)} build the
 * document through a hardened, namespace-aware parser instead of the engine's own; the {@code evaluateExpression} default methods added by Java 9 route
 * through these overloads as well.</p>
 */
final class HardeningXPathExpression implements XPathExpression {

    private final XPathExpression delegate;

    HardeningXPathExpression(final XPathExpression delegate) {
        this.delegate = delegate;
    }

    @Override
    public String evaluate(final InputSource source) throws XPathExpressionException {
        return delegate.evaluate(HardeningXPath.parse(source));
    }

    @Override
    public Object evaluate(final InputSource source, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(HardeningXPath.parse(source), returnType);
    }

    // <editor-fold defaultstate="collapsed" desc="Trivial delegation">
    @Override
    public String evaluate(final Object item) throws XPathExpressionException {
        return delegate.evaluate(item);
    }

    @Override
    public Object evaluate(final Object item, final QName returnType) throws XPathExpressionException {
        return delegate.evaluate(item, returnType);
    }
    // </editor-fold>
}
