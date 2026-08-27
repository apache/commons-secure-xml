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

import java.util.Objects;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/**
 * Creates new, hardened {@link XPathFactory} instances.
 * <p>
 * Not a {@link XPathFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningXPathFactory {

    /**
     * Returns a new, hardened {@link XPathFactory} for the default XPath object model.
     * <p>
     * Beyond the three universal guarantees on {@link org.apache.commons.xml}, URI-fetching XPath 3.1+ functions ({@code doc()}, {@code collection()},
     * {@code unparsed-text()}) are not resolved.
     * </p>
     * <p>
     * The guarantees also cover the document parse behind {@code XPath.evaluate(String, InputSource)} and {@code XPathExpression.evaluate(InputSource)}: the
     * input document is built through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} instead of the engine's internal parser.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if there is a failure in creating an {@link XPathFactory} for the default object model.
     */
    public static XPathFactory newInstance() {
        return XPathHardener.harden(XPathFactory.newInstance());
    }

    /**
     * Wraps a prepared delegate in the hardening wrapper; called by the hardener once the required settings are applied.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @return The hardened factory.
     */
    static XPathFactory wrap(final XPathFactory delegate) {
        return new Wrapper(delegate);
    }

    private HardeningXPathFactory() {
        // static only
    }

    /**
     * {@link XPathFactory} wrapper that returns a {@link HardeningXPath} from {@link #newXPath()}.
     *
     * <p>Required because {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} on the factory governs only the XPath engine: the stock JDK and Apache Xalan
     * implement the {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points by provisioning an internal document parser the feature does not reach.
     * The wrapper performs that document build itself through a hardened parser instead; see {@link HardeningXPath}.</p>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends XPathFactory {

        private final XPathFactory delegate;

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        Wrapper(final XPathFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean getFeature(final String name) throws XPathFactoryConfigurationException {
            return delegate.getFeature(name);
        }

        @Override
        public boolean isObjectModelSupported(final String objectModel) {
            return delegate.isObjectModelSupported(objectModel);
        }

        @Override
        public XPath newXPath() {
            final XPath xpath = delegate.newXPath();
            return xpath == null ? null : new HardeningXPath(xpath);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws XPathFactoryConfigurationException {
            delegate.setFeature(name, value);
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
}
