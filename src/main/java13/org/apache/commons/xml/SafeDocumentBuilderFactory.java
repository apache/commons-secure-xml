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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;

/**
 * Creates new, hardened {@link DocumentBuilderFactory} instances.
 *
 * <p>Each factory method mirrors the {@link DocumentBuilderFactory} static factory method of the same name and signature, and every returned factory carries
 * the hardening guarantees documented for the {@link org.apache.commons.xml package}.</p>
 *
 * <p>Beyond the three universal guarantees, XInclude resolution is denied by default. When
 * {@link DocumentBuilderFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on a returned factory, the parser will process
 * {@code xi:include} elements but every external resource lookup is rejected. To permit specific trusted resources, install an
 * {@link org.xml.sax.EntityResolver EntityResolver} on the {@link DocumentBuilder} that allow-lists them; any href the resolver does not explicitly allow
 * stays blocked.</p>
 *
 * <p>On Java 9 or later the Multi-Release jar adds {@code newDefaultInstance()}, and on Java 13 or later {@code newNSInstance()},
 * {@code newNSInstance(String, ClassLoader)} and {@code newDefaultNSInstance()}; each mirrors the {@link DocumentBuilderFactory} method of the same name and
 * returns a hardened factory.</p>
 */
public final class SafeDocumentBuilderFactory {

    /**
     * Returns a new, hardened {@link DocumentBuilderFactory}, obtained as by {@link DocumentBuilderFactory#newInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    public static DocumentBuilderFactory newInstance() {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link DocumentBuilderFactory} of the given implementation class, obtained as by
     * {@link DocumentBuilderFactory#newInstance(String, ClassLoader)}.
     *
     * @param factoryClassName The fully qualified class name of the {@link DocumentBuilderFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static DocumentBuilderFactory newInstance(final String factoryClassName, final ClassLoader classLoader) {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newInstance(factoryClassName, classLoader));
    }

    /**
     * Returns a new, hardened {@link DocumentBuilderFactory} of the system-default implementation, obtained as by
     * {@link DocumentBuilderFactory#newDefaultInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException Thrown if a (non-Android) factory cannot support the secure processing feature
     *                               {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     */
    public static DocumentBuilderFactory newDefaultInstance() {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newDefaultInstance());
    }

    /**
     * Returns a new, hardened, namespace-aware {@link DocumentBuilderFactory} of the system-default implementation, obtained as by
     * {@link DocumentBuilderFactory#newDefaultNSInstance()}.
     *
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException Thrown if a (non-Android) factory cannot support the secure processing feature
     *                               {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     */
    public static DocumentBuilderFactory newDefaultNSInstance() {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newDefaultNSInstance());
    }

    /**
     * Returns a new, hardened, namespace-aware {@link DocumentBuilderFactory}, obtained as by {@link DocumentBuilderFactory#newNSInstance()}.
     *
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    public static DocumentBuilderFactory newNSInstance() {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newNSInstance());
    }

    /**
     * Returns a new, hardened, namespace-aware {@link DocumentBuilderFactory} of the given implementation class, obtained as by
     * {@link DocumentBuilderFactory#newNSInstance(String, ClassLoader)}.
     *
     * @param factoryClassName The fully qualified class name of the {@link DocumentBuilderFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static DocumentBuilderFactory newNSInstance(final String factoryClassName, final ClassLoader classLoader) {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newNSInstance(factoryClassName, classLoader));
    }

    private SafeDocumentBuilderFactory() {
        // static only
    }
}
