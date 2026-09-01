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

import java.lang.invoke.MethodHandle;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.SchemaFactoryConfigurationError;
import javax.xml.validation.Validator;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Creates new, secure {@link SchemaFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml.secure}:
 * </p>
 * <ul>
 * <li>{@code xs:import}, {@code xs:include} and {@code xs:redefine} schemaLocation URIs are not resolved during schema compilation,</li>
 * <li>{@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hints in instance documents are not resolved during validation, and</li>
 * <li>the content model a schema expands into is bounded, on every implementation offering a limit for it. A loader expands a repeated particle while building
 * the DFA, so a compact schema carrying a large {@code maxOccurs} would otherwise exhaust memory or CPU (see Xerces'
 * <a href="https://xerces.apache.org/xerces2-j/properties.html#security-manager">security manager</a>, which caps that expansion at 3,000 nodes).</li>
 * </ul>
 * <p>
 * The same guarantees apply to {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} instances produced from the resulting
 * {@link javax.xml.validation.Schema}.
 * </p>
 * <p>
 * This class is not itself a {@link SchemaFactory}, so it inherits none of the static JAXP factory methods. A caller therefore cannot obtain an unsecured
 * factory through this class by calling a method such as {@code newDefaultInstance()}. The secure factories are instances of a nested, non-public wrapper
 * class.
 * </p>
 *
 * @see org.apache.commons.xml.secure
 */
public final class SecureSchemaFactory {

    /**
     * Capability-driven secure wrapper for any {@link SchemaFactory} on the classpath, the same recipe for every implementation. It is the entry point reached
     * by {@link SecureSchemaFactory#newInstance(String)}; there is no per-implementation branching and no limit configuration on the factory itself beyond
     * {@code FEATURE_SECURE_PROCESSING}.
     *
     * <p>Three layers cooperate:</p>
     * <ol>
     *   <li>{@link SecureSchemaFactory} installs an ignore-all {@link FallbackIgnoreLSResourceResolver} floor on the factory (blocking
     *       {@code xs:import}/{@code xs:include}/{@code xs:redefine} at compile time) and rewrites the Source on every {@code newSchema(Source[])} entry point
     *       through {@link SecureSAXParserFactory#secure(Source, boolean)}.</li>
     *   <li>{@link SecureSchema} wraps every Validator/ValidatorHandler the inner Schema produces and re-installs the floor on each (blocking
     *       {@code xsi:schemaLocation} at validation time), since neither the JDK nor Xerces reliably propagates it through {@code Schema}.</li>
     *   <li>{@link SecureValidator} rewrites the Source on every {@link Validator#validate(Source)} call.</li>
     * </ol>
     *
     * <p>
     * The secure reader supplied by {@link SecureSAXParserFactory#secure(Source, boolean)} already carries {@code FEATURE_SECURE_PROCESSING} and the processing limits, so a
     * DOCTYPE, external entity or Billion Laughs payload in the schema or instance document is bounded there rather than on this factory. One limit it cannot
     * supply is content-model expansion: a large {@code maxOccurs} is expanded by the schema loader when it builds the DFA, after parsing and without the
     * reader, so {@code FEATURE_SECURE_PROCESSING} is set on the factory as well, which is what installs that bound on external Xerces (the stock JDK applies
     * it unconditionally). The JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties are still not set explicitly: the resolver floor already blocks the same fetches on
     * every implementation, and the JDK 8 {@code SchemaFactory} has a bug whereby those properties keep blocking even when a caller's own resolver would grant
     * the access. The floor is a non-removable
     * lower bound: a caller-set {@link LSResourceResolver} is routed through it (opting a specific lookup in by returning a non-{@code null} result) rather than
     * replacing it, so secure cannot be dropped by swapping the resolver.
     * </p>
     *
     * @see org.apache.commons.xml.secure
     */
    private static final class Wrapper extends SchemaFactory {

        private final SchemaFactory delegate;


        private final FallbackIgnoreLSResourceResolver floor = new FallbackIgnoreLSResourceResolver(null);

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final SchemaFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            // Content-model expansion happens in the schema loader, after parsing, so the injected reader's limits cannot reach it.
            SecureSchemaFactory.setFeature(delegate, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Compile-time block for xs:import/include/redefine; the wrappers carry the rest (per-product resolver, source rewriting, limits via the reader).
            delegate.setResourceResolver(floor);
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return delegate.getErrorHandler();
        }

        @Override
        public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getFeature(name);
        }

        @Override
        public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getProperty(name);
        }

        @Override
        public LSResourceResolver getResourceResolver() {
            return floor.getDelegate();
        }

        @Override
        public boolean isSchemaLanguageSupported(final String schemaLanguage) {
            return delegate.isSchemaLanguageSupported(schemaLanguage);
        }

        @Override
        public Schema newSchema() throws SAXException {
            return new SecureSchema(delegate.newSchema(), overrideDefaultParser());
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Schema newSchema(final Source[] schemas) throws SAXException {
            return new SecureSchema(delegate.newSchema(secure(schemas)), overrideDefaultParser());
        }

        /**
         * Tests whether parsers should be instantiated via {@code newInstance()} instead of {@code newDefaultInstance()}.
         *
         * <p>The JDK implementation of {@link SchemaFactory} uses the JDK parsers while {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} is unset or
         * {@code false}.</p>
         *
         * @return {@code true} if parsers should be created via {@code newInstance()}.
         */
        private boolean overrideDefaultParser() {
            try {
                return delegate.getFeature(SecureSAXParserFactory.OVERRIDE_DEFAULT_PARSER);
            } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
                return true;
            }
        }

        /**
         * Secures every schema source through {@link SecureSAXParserFactory#secure(Source, boolean)}.
         *
         * @param schemas the schema sources to secure; must not be {@code null}.
         * @return a new array of secure sources.
         * @throws IllegalStateException     Thrown if the underlying implementation cannot provide a secure reader.
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        private Source[] secure(final Source[] schemas) {
            final Source[] secure = new Source[schemas.length];
            final boolean overrideDefaultParser = overrideDefaultParser();
            for (int i = 0; i < schemas.length; i++) {
                secure[i] = SecureSAXParserFactory.secure(schemas[i], overrideDefaultParser);
            }
            return secure;
        }

        @Override
        public void setErrorHandler(final ErrorHandler errorHandler) {
            delegate.setErrorHandler(errorHandler);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setFeature(name, value);
        }


        @Override
        public void setProperty(final String name, final Object object) throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setProperty(name, object);
        }

        @Override
        public void setResourceResolver(final LSResourceResolver resourceResolver) {
            // Route a caller resolver through the floor instead of replacing it, so the ignore-all lower bound cannot be removed.
            floor.setDelegate(resourceResolver);
        }
    }

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_SCHEMA_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory";

    private static final MethodHandle MH_newDefaultInstance = MethodHandleFactory.findStatic(SchemaFactory.class, "newDefaultInstance");

    /**
     * Returns a new, secure {@link SchemaFactory} of the system-default implementation, supporting W3C XML Schema 1.0.
     * <p>
     * Obtained from {@code SchemaFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), by instantiating the JDK's built-in
     * implementation directly on Java 8, and by the standard {@link #newInstance(String)} lookup where the platform provides neither (for example Android,
     * whose lookup falls back to exactly the Xerces implementation this library recognizes).
     * </p>
     *
     * @return A secure factory.
     * @throws IllegalStateException    Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws IllegalArgumentException Thrown from the {@link #newInstance(String)} lookup this method falls back to on a platform that provides neither
     *                                 {@code newDefaultInstance()} nor the JDK's built-in implementation (for example Android).
     */
    public static SchemaFactory newDefaultInstance() {
        if (MH_newDefaultInstance != null) {
            return secure(MethodHandleFactory.invokeExact(() -> (SchemaFactory) MH_newDefaultInstance.invokeExact(), SchemaFactoryConfigurationError.class));
        }
        try {
            // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead.
            return newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI, JDK_SCHEMA_FACTORY, null);
        } catch (final IllegalArgumentException e) {
            // Neither exists (for example Android): degrade to the regular lookup, whose Android fallback is exactly the Xerces implementation.
            return newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        }
    }

    /**
     * Returns a new, secure {@link SchemaFactory} for the given schema language.
     *
     * @param schemaLanguage The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @return A secure factory.
     * @throws IllegalArgumentException        Thrown if no implementation of the schema language is available.
     * @throws NullPointerException            Thrown if {@code schemaLanguage} is {@code null}.
     * @throws SchemaFactoryConfigurationError Thrown if a configuration error is encountered.
     */
    public static SchemaFactory newInstance(final String schemaLanguage) {
        return secure(SchemaFactory.newInstance(schemaLanguage));
    }

    /**
     * Returns a new, secure {@link SchemaFactory} of the given implementation class.
     *
     * @param schemaLanguage   The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link SchemaFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A secure factory.
     * @throws IllegalArgumentException Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or instantiated, or does
     *                                  not support {@code schemaLanguage}.
     * @throws NullPointerException     Thrown if {@code schemaLanguage} is {@code null}.
     */
    public static SchemaFactory newInstance(final String schemaLanguage, final String factoryClassName, final ClassLoader classLoader) {
        return secure(SchemaFactory.newInstance(schemaLanguage, factoryClassName, classLoader));
    }

    /**
     * Secures a {@link SchemaFactory}.
     *
     * <p>Unlike the other factory types there is no per-implementation branching: schema compilation and validation reach external resources only through the
     * resolver hook, so wrapping the factory with a non-removable ignore-all resolver floor is enough on every implementation. The reader used to parse schema
     * and instance documents is secure separately, through {@link SecureSAXParserFactory#secure(javax.xml.transform.Source, boolean)}; the factory carries
     * {@code FEATURE_SECURE_PROCESSING} for the one limit that reader cannot supply, the loader's content-model expansion.</p>
     *
     * @param factory the factory to secure; never {@code null}.
     * @return a secure factory.
     */
    static SchemaFactory secure(final SchemaFactory factory) {
        return new Wrapper(factory);
    }

    /**
     * Sets a feature on the delegate, failing closed: an implementation that cannot accept it yields no factory rather than an unsecured one.
     *
     * @param factory the factory to configure; never {@code null}.
     * @param feature the feature name.
     * @param value   the value to set.
     * @throws SecureException if the implementation rejects the feature.
     */
    private static void setFeature(final SchemaFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw SecureException.featureFailed(feature, factory, e);
        }
    }

    private SecureSchemaFactory() {
        // static only
    }
}
