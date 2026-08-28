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
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
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
 * Creates new, hardened {@link SchemaFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml}:
 * </p>
 * <ul>
 * <li>{@code xs:import}, {@code xs:include} and {@code xs:redefine} schemaLocation URIs are not resolved during schema compilation, and</li>
 * <li>{@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hints in instance documents are not resolved during validation.</li>
 * </ul>
 * <p>
 * The same guarantees apply to {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} instances produced from the
 * resulting {@link javax.xml.validation.Schema}.
 * </p>
 * <p>
 * Not a {@link SchemaFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningSchemaFactory {

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_SCHEMA_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory";

    private static final MethodHandle NEW_DEFAULT_INSTANCE = MethodHandleFactory.findStatic(SchemaFactory.class, "newDefaultInstance",
            MethodType.methodType(SchemaFactory.class));

    /**
     * Hardening for any {@link SchemaFactory} on the classpath.
     *
     * <p>Unlike the other factory types there is no per-implementation branching and no feature or limit configuration on the factory itself: schema compilation
     * and validation reach external resources only through the resolver hook, so wrapping the factory with a non-removable ignore-all resolver floor is enough on
     * every implementation. The reader used to parse schema and instance documents is hardened separately, through
     * {@link HardeningSAXParserFactory#harden(javax.xml.transform.Source, boolean)}.</p>
     *
     * @param factory the factory to harden; never {@code null}.
     * @return a hardened factory.
     */
    static SchemaFactory harden(final SchemaFactory factory) {
        return new Wrapper(factory);
    }

    /**
     * Returns a new, hardened {@link SchemaFactory} of the system-default implementation, supporting W3C XML Schema 1.0.
     * <p>
     * Obtained as by {@code SchemaFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and by instantiating the JDK's built-in
     * implementation directly on Java 8.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException    Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalArgumentException Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation
     *                                 (for example Android).
     */
    public static SchemaFactory newDefaultInstance() {
        if (NEW_DEFAULT_INSTANCE != null) {
            final SchemaFactory factory;
            try {
                factory = (SchemaFactory) NEW_DEFAULT_INSTANCE.invokeExact();
            } catch (final SchemaFactoryConfigurationError e) {
                throw e;
            } catch (final Throwable e) {
                // Unreachable: the looked-up method declares no other exceptions.
                throw new IllegalStateException(e);
            }
            return harden(factory);
        }
        // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead. Where that class does not exist either (for
        // example Android), the lookup miss surfaces as IllegalArgumentException, the error SchemaFactory.newInstance(String, String, ClassLoader) defines.
        return newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI, JDK_SCHEMA_FACTORY, null);
    }

    /**
     * Returns a new, hardened {@link SchemaFactory} for the given schema language.
     *
     * @param schemaLanguage The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @return A hardened factory.
     * @throws IllegalArgumentException        Thrown if no implementation of the schema language is available.
     * @throws NullPointerException            Thrown if {@code schemaLanguage} is {@code null}.
     * @throws SchemaFactoryConfigurationError Thrown if a configuration error is encountered.
     */
    public static SchemaFactory newInstance(final String schemaLanguage) {
        return harden(SchemaFactory.newInstance(schemaLanguage));
    }

    /**
     * Returns a new, hardened {@link SchemaFactory} of the given implementation class.
     *
     * @param schemaLanguage   The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link SchemaFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalArgumentException Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or instantiated, or does
     *                                  not support {@code schemaLanguage}.
     * @throws NullPointerException     Thrown if {@code schemaLanguage} is {@code null}.
     */
    public static SchemaFactory newInstance(final String schemaLanguage, final String factoryClassName, final ClassLoader classLoader) {
        return harden(SchemaFactory.newInstance(schemaLanguage, factoryClassName, classLoader));
    }

    private HardeningSchemaFactory() {
        // static only
    }

    /**
     * Capability-driven hardening wrapper for any {@link SchemaFactory} on the classpath, the same recipe for every implementation. It is the entry point reached
     * by {@link HardeningSchemaFactory#newInstance(String)}; there is no per-implementation branching, no {@code FEATURE_SECURE_PROCESSING} and no limit configuration on the
     * factory itself.
     *
     * <p>Three layers cooperate:</p>
     * <ol>
     *   <li>{@link HardeningSchemaFactory} installs an ignore-all {@link FallbackIgnoreLSResourceResolver} floor on the factory (blocking
     *       {@code xs:import}/{@code xs:include}/{@code xs:redefine} at compile time) and rewrites the Source on every {@code newSchema(Source[])} entry point
     *       through {@link HardeningSAXParserFactory#harden(Source, boolean)}.</li>
     *   <li>{@link HardeningSchema} wraps every Validator/ValidatorHandler the inner Schema produces and re-installs the floor on each (blocking
     *       {@code xsi:schemaLocation} at validation time), since neither the JDK nor Xerces reliably propagates it through {@code Schema}.</li>
     *   <li>{@link HardeningValidator} rewrites the Source on every {@link Validator#validate(Source)} call.</li>
     * </ol>
     *
     * <p>
     * The hardened reader supplied by {@link HardeningSAXParserFactory#harden(Source, boolean)} already carries {@code FEATURE_SECURE_PROCESSING} and the processing limits, so a
     * DOCTYPE, external entity or Billion Laughs payload in the schema or instance document is bounded there rather than on this factory. The JAXP 1.5
     * {@code ACCESS_EXTERNAL_*} properties are deliberately not set: the resolver floor already blocks the same fetches on every implementation, and the JDK 8
     * {@code SchemaFactory} has a bug whereby those properties keep blocking even when a caller's own resolver would grant the access. The floor is a non-removable
     * lower bound: a caller-set {@link LSResourceResolver} is routed through it (opting a specific lookup in by returning a non-{@code null} result) rather than
     * replacing it, so hardening cannot be dropped by swapping the resolver.
     * </p>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends SchemaFactory {

        /**
         * Hardens every schema source through {@link HardeningSAXParserFactory#harden(Source, boolean)}.
         *
         * @param schemas the schema sources to harden; must not be {@code null}.
         * @return a new array of hardened sources.
         * @throws SAXException if any source cannot be hardened.
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        private Source[] harden(final Source[] schemas) throws SAXException {
            final Source[] hardened = new Source[schemas.length];
            final boolean useDefaultParser = useDefaultParser();
            try {
                for (int i = 0; i < schemas.length; i++) {
                    hardened[i] = HardeningSAXParserFactory.harden(schemas[i], useDefaultParser);
                }
            } catch (final TransformerConfigurationException e) {
                throw new SAXException("Failed to harden schema source", e);
            }
            return hardened;
        }


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
            return new HardeningSchema(delegate.newSchema(), useDefaultParser());
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Schema newSchema(final Source[] schemas) throws SAXException {
            return new HardeningSchema(delegate.newSchema(harden(schemas)), useDefaultParser());
        }

        /**
         * Checks whether parsers should be instantiated via {@code newDefaultInstance()} instead of {@code newInstance()}.
         *
         * <p>The JDK implementation of {@link SchemaFactory} uses the JDK parsers while {@value HardeningSAXParserFactory#OVERRIDE_DEFAULT_PARSER} is unset or
         * {@code false}.</p>
         *
         * @return {@code true} if parsers should be created via {@code newDefaultInstance()}.
         */
        private boolean useDefaultParser() {
            try {
                return !delegate.getFeature(HardeningSAXParserFactory.OVERRIDE_DEFAULT_PARSER);
            } catch (final SAXNotRecognizedException | SAXNotSupportedException e) {
                return false;
            }
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
}
