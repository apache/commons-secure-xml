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

import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

/**
 * Creates new, hardened {@link XPathFactory} instances.
 *
 * <p>Each factory method mirrors the {@link XPathFactory} static factory method of the same name and signature, and every returned factory carries the
 * hardening guarantees documented for the {@link org.apache.commons.xml package}.</p>
 *
 * <p>Beyond the three universal guarantees, URI-fetching XPath 3.1+ functions ({@code doc()}, {@code collection()}, {@code unparsed-text()}) are not
 * resolved.</p>
 * <p>
 * The guarantees also cover the document parse behind {@code XPath.evaluate(String, InputSource)} and {@code XPathExpression.evaluate(InputSource)}: the
 * input document is built through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} instead of the engine's internal parser.
 * </p>
 *
 * <p>On Java 9 or later the Multi-Release jar adds {@code newDefaultInstance()}, mirroring the {@link XPathFactory} method of the same name and returning a
 * hardened factory.</p>
 */
public final class SafeXPathFactory {

    /**
     * Returns a new, hardened {@link XPathFactory} for the default XPath object model, obtained as by {@link XPathFactory#newInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if there is a failure in creating an {@link XPathFactory} for the default object model.
     */
    public static XPathFactory newInstance() {
        return XPathHardener.harden(XPathFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XPathFactory} for the given object model, obtained as by {@link XPathFactory#newInstance(String)}.
     *
     * @param uri The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @return A hardened factory.
     * @throws IllegalStateException               Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException  Thrown if no implementation of the object model is available.
     * @throws NullPointerException                Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException            Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri) throws XPathFactoryConfigurationException {
        return XPathHardener.harden(XPathFactory.newInstance(uri));
    }

    /**
     * Returns a new, hardened {@link XPathFactory} of the given implementation class, obtained as by
     * {@link XPathFactory#newInstance(String, String, ClassLoader)}.
     *
     * @param uri              The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link XPathFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException               Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException  Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or
     *                                             instantiated, or does not support {@code uri}.
     * @throws NullPointerException                Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException            Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri, final String factoryClassName, final ClassLoader classLoader)
            throws XPathFactoryConfigurationException {
        return XPathHardener.harden(XPathFactory.newInstance(uri, factoryClassName, classLoader));
    }

    /**
     * Returns a new, hardened {@link XPathFactory} of the system-default implementation, supporting the default XPath object model, obtained as by
     * {@link XPathFactory#newDefaultInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static XPathFactory newDefaultInstance() {
        return XPathHardener.harden(XPathFactory.newDefaultInstance());
    }

    private SafeXPathFactory() {
        // static only
    }
}
