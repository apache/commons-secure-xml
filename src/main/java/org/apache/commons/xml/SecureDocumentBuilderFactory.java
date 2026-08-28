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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;

import org.xml.sax.EntityResolver;

/**
 * Creates new, secure {@link DocumentBuilderFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml}, XInclude resolution is denied by default. When
 * {@link DocumentBuilderFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on the returned factory, the parser will process
 * {@code xi:include} elements but every external resource lookup is rejected. To permit specific trusted resources, install an
 * {@link org.xml.sax.EntityResolver EntityResolver} on the {@link DocumentBuilder} that allow-lists them; any href the resolver does not explicitly allow
 * stays blocked.
 * </p>
 * <p>
 * Not a {@link DocumentBuilderFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The secure factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class SecureDocumentBuilderFactory {

    /**
     * {@link DocumentBuilderFactory} wrapper that keeps an ignore-all {@link EntityResolver} floor on every {@link DocumentBuilder} produced.
     * <p>
     * Wraps each produced builder in a {@link SecureDocumentBuilder}; required when the underlying factory carries no resolver of its own and does not honor
     * JAXP 1.5 {@code ACCESS_EXTERNAL_*} (e.g. the external Xerces distribution). A caller-set resolver is routed through the floor rather than replacing it. Kept
     * as a standalone wrapper so any hardener can reuse the floor.
     * </p>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends DocumentBuilderFactory {

        private final DocumentBuilderFactory delegate;

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final DocumentBuilderFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Object getAttribute(final String name) {
            return delegate.getAttribute(name);
        }

        @Override
        public boolean getFeature(final String name) throws ParserConfigurationException {
            return delegate.getFeature(name);
        }

        @Override
        public Schema getSchema() {
            return delegate.getSchema();
        }

        @Override
        public boolean isCoalescing() {
            return delegate.isCoalescing();
        }

        @Override
        public boolean isExpandEntityReferences() {
            return delegate.isExpandEntityReferences();
        }

        @Override
        public boolean isIgnoringComments() {
            return delegate.isIgnoringComments();
        }

        @Override
        public boolean isIgnoringElementContentWhitespace() {
            return delegate.isIgnoringElementContentWhitespace();
        }

        @Override
        public boolean isNamespaceAware() {
            return delegate.isNamespaceAware();
        }

        @Override
        public boolean isValidating() {
            return delegate.isValidating();
        }

        @Override
        public boolean isXIncludeAware() {
            return delegate.isXIncludeAware();
        }

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            return new SecureDocumentBuilder(delegate.newDocumentBuilder());
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            delegate.setAttribute(name, value);
        }

        @Override
        public void setCoalescing(final boolean coalescing) {
            delegate.setCoalescing(coalescing);
        }

        @Override
        public void setExpandEntityReferences(final boolean expandEntityRef) {
            delegate.setExpandEntityReferences(expandEntityRef);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws ParserConfigurationException {
            delegate.setFeature(name, value);
        }

        @Override
        public void setIgnoringComments(final boolean ignoreComments) {
            delegate.setIgnoringComments(ignoreComments);
        }

        @Override
        public void setIgnoringElementContentWhitespace(final boolean whitespace) {
            delegate.setIgnoringElementContentWhitespace(whitespace);
        }

        @Override
        public void setNamespaceAware(final boolean awareness) {
            delegate.setNamespaceAware(awareness);
        }

        @Override
        public void setSchema(final Schema schema) {
            delegate.setSchema(schema);
        }

        @Override
        public void setValidating(final boolean validating) {
            delegate.setValidating(validating);
        }

        @Override
        public void setXIncludeAware(final boolean state) {
            delegate.setXIncludeAware(state);
        }
    }
    /** Class name of Android's Harmony-based {@link DocumentBuilderFactory}, which exposes no secure surface. */
    private static final String ANDROID_DOCUMENT_BUILDER_FACTORY = "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl";
    /** System property naming the {@link DocumentBuilderFactory} implementation, the JDK's own mechanism for reconfiguring the default parser. */
    private static final String DOM_FACTORY_ID = "javax.xml.parsers.DocumentBuilderFactory";

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_DOCUMENT_BUILDER_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";

    private static final MethodHandle MH_newDefaultInstance = MethodHandleFactory.findStatic(DocumentBuilderFactory.class, "newDefaultInstance",
            MethodType.methodType(DocumentBuilderFactory.class));

    /**
     * Enables namespace awareness on the given factory; the {@code NSInstance} counterpart of each factory method routes its result through here.
     *
     * @param factory the factory to configure; never {@code null}.
     * @return The given factory, namespace-aware.
     */
    private static DocumentBuilderFactory makeNSAware(final DocumentBuilderFactory factory) {
        factory.setNamespaceAware(true);
        return factory;
    }

    /**
     * Returns a new, secure {@link DocumentBuilderFactory} of the system-default implementation.
     * <p>
     * Obtained as by {@code DocumentBuilderFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and
     * by instantiating the JDK's built-in implementation directly on Java 8.
     * </p>
     *
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation
     *                                   (for example Android).
     */
    public static DocumentBuilderFactory newDefaultInstance() {
        if (MH_newDefaultInstance != null) {
            return secure(MethodHandleFactory.invokeExact(() -> (DocumentBuilderFactory) MH_newDefaultInstance.invokeExact(), FactoryConfigurationError.class));
        }
        // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead. Where that class does not exist either (for
        // example Android), the lookup miss surfaces as the factory's own FactoryConfigurationError, like any newInstance miss.
        return newInstance(JDK_DOCUMENT_BUILDER_FACTORY, null);
    }

    /**
     * Returns a new, secure, namespace-aware {@link DocumentBuilderFactory} of the system-default implementation, enabling namespace awareness on
     * {@link #newDefaultInstance()}, the behavior {@code DocumentBuilderFactory.newDefaultNSInstance()} (Java 13 or later) is specified to have.
     *
     * @return A secure, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation
     *                                   (for example Android).
     */
    public static DocumentBuilderFactory newDefaultNSInstance() {
        return makeNSAware(newDefaultInstance());
    }

    /**
     * Returns a new, secure {@link DocumentBuilderFactory}.
     *
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    public static DocumentBuilderFactory newInstance() {
        return secure(DocumentBuilderFactory.newInstance());
    }

    /**
     * Returns a new, secure {@link DocumentBuilderFactory} of the given implementation class.
     *
     * @param factoryClassName The fully qualified class name of the {@link DocumentBuilderFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Android) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static DocumentBuilderFactory newInstance(final String factoryClassName, final ClassLoader classLoader) {
        return secure(DocumentBuilderFactory.newInstance(factoryClassName, classLoader));
    }

    /**
     * Returns a new, secure, namespace-aware {@link DocumentBuilderFactory}, enabling namespace awareness on {@link #newInstance()}, the behavior
     * {@code DocumentBuilderFactory.newNSInstance()} (Java 13 or later) is specified to have.
     *
     * @return A secure, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    public static DocumentBuilderFactory newNSInstance() {
        return makeNSAware(newInstance());
    }

    /**
     * Returns the secure, namespace-aware factory the Source-rewriting wrappers parse with.
     * <p>
     * While {@code overrideDefaultParser} is {@code false} the factory is the JDK's "default parser" factory, determined the way the JDK itself determines it: the built-in
     * implementation, unless the {@value #DOM_FACTORY_ID} system property is set — that property is the JDK's own mechanism for
     * reconfiguring the default parser, so it is honored through the standard lookup rather than bypassed.
     * </p>
     *
     * @param overrideDefaultParser whether {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} on the originating factory asks to override the JDK's default parser.
     * @return A secure, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    static DocumentBuilderFactory newNSInstance(final boolean overrideDefaultParser) {
        return overrideDefaultParser || System.getProperty(DOM_FACTORY_ID) != null ? newNSInstance() : newDefaultNSInstance();
    }

    /**
     * Returns a new, secure, namespace-aware {@link DocumentBuilderFactory} of the given implementation class, enabling namespace awareness on
     * {@link #newInstance(String, ClassLoader)}, the behavior {@code DocumentBuilderFactory.newNSInstance(String, ClassLoader)} (Java 13 or later) is specified
     * to have.
     *
     * @param factoryClassName The fully qualified class name of the {@link DocumentBuilderFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A secure, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static DocumentBuilderFactory newNSInstance(final String factoryClassName, final ClassLoader classLoader) {
        return makeNSAware(newInstance(factoryClassName, classLoader));
    }

    /**
     * Capability-driven secure for any {@link DocumentBuilderFactory} on the classpath.
     *
     * <p>Rather than branching on the implementation class, this method probes what the factory supports and adapts:</p>
     * <ul>
     *     <li><strong>Android</strong> (Harmony / KXmlParser): recognized by class name and left untouched. It exposes no {@link XMLConstants#FEATURE_SECURE_PROCESSING
     *         FSP}, no JAXP 1.5 {@code ACCESS_EXTERNAL_*} and no attribute API at all, while KXmlParser silently drops user-defined entities, so there is nothing to
     *         apply.</li>
     *     <li><strong>FSP</strong>: required. It switches on the implementation's built-in security manager, which is what carries the processing limits.</li>
     *     <li><strong>Ignore-all resolver floor</strong>: every produced {@link DocumentBuilder} is wrapped by the nested wrapper, which keeps an
     *         ignore-all {@link EntityResolver} floor. That floor blocks external DTD, entity, schema and {@code xi:include} fetches in one place: the stock JDK's
     *         XInclude processor ignores {@code ACCESS_EXTERNAL_*} and consults the {@link EntityResolver} instead, so no {@code ACCESS_EXTERNAL_*} attributes are
     *         needed here. A caller can chain its own resolver onto the floor to allow-list resources, but cannot remove it.</li>
     * </ul>
     *
     * @param factory The factory to harden.
     * @return A new secure factory or the original factory, as-is, if it is a known Android factory.
     * @throws SecureException Thrown if a (non-Android) factory cannot support the secure processing feature {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     */
    static DocumentBuilderFactory secure(final DocumentBuilderFactory factory) {
        // Android exposes no FSP, ACCESS_EXTERNAL_* or attribute API, and KXmlParser drops user-defined entities; nothing to apply.
        if (ANDROID_DOCUMENT_BUILDER_FACTORY.equals(factory.getClass().getName())) {
            return factory;
        }
        // Required: enables the implementation's security manager, which carries the limits.
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Required: the wrapper installs an ignore-all EntityResolver floor on every DocumentBuilder.
        // That floor blocks external DTD, entity, schema and xi:include fetches in one place: no ACCESS_EXTERNAL_* attributes are needed here.
        // Callers can chain their resolvers, but not override the floor.
        return new Wrapper(factory);
    }

    /**
     * Sets a feature on the given factory, throwing a {@link SecureException} if the implementation does not recognize it.
     *
     * @param factory The factory to harden.
     * @param feature The feature to set.
     * @param value   The value to set.
     * @throws SecureException   Thrown if this factory or the {@code XPath}s it creates cannot support this feature.
     * @throws NullPointerException Thrown if the {@code feature} parameter is null.
     */
    private static void setFeature(final DocumentBuilderFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final ParserConfigurationException e) {
            throw SecureException.featureFailed(feature, factory, e);
        }
    }

    private SecureDocumentBuilderFactory() {
        // static only
    }
}
