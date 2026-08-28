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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Creates new, hardened {@link SAXParserFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml}, XInclude resolution is denied by default. When
 * {@link SAXParserFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on the returned factory, the parser will process {@code xi:include}
 * elements but every external resource lookup is rejected. To permit specific trusted resources, install an {@link org.xml.sax.EntityResolver
 * EntityResolver} on the {@link org.xml.sax.XMLReader} that allow-lists them; any href the resolver does not explicitly allow stays blocked.
 * </p>
 * <p>
 * Not a {@link SAXParserFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class SecureSAXParserFactory {

    /** Class name of Android's Expat-backed {@link XMLReader}. */
    private static final String ANDROID_EXPAT_READER = "org.apache.harmony.xml.ExpatReader";
    /** Class name of Android's Harmony-based {@link SAXParserFactory}, backed by the native Expat parser. */
    private static final String ANDROID_SAX_PARSER_FACTORY = "org.apache.harmony.xml.parsers.SAXParserFactoryImpl";
    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_SAX_PARSER_FACTORY = "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";

    /**
     * The JDK feature governing whether an implementation's internal parser lookup may resolve a third-party parser. The hardening wrappers parse every source
     * themselves, so instead of configuring the implementation the TrAX, XPath and schema wrappers read this feature and pick the rewrite parser accordingly.
     */
    static final String OVERRIDE_DEFAULT_PARSER = "jdk.xml.overrideDefaultParser";

    /** System property naming the {@link SAXParserFactory} implementation, the JDK's own mechanism for reconfiguring the default parser. */
    private static final String SAX_FACTORY_ID = "javax.xml.parsers.SAXParserFactory";

    private static final MethodHandle NEW_DEFAULT_INSTANCE = MethodHandleFactory.findStatic(SAXParserFactory.class, "newDefaultInstance",
            MethodType.methodType(SAXParserFactory.class));

    /**
     * Capability-driven hardening for any {@link SAXParserFactory} on the classpath.
     *
     * <p>Rather than branching on the implementation class, this method probes what the parser supports and adapts. Because
     * {@link SAXParserFactory} exposes only a feature API and no property API, the per-parse configuration runs on each {@link XMLReader} the factory produces,
     * funnelled through the nested wrapper into {@link #secure(XMLReader)}:</p>
     * <ul>
     *     <li><strong>Android</strong> (Harmony / Expat): {@link XMLConstants#FEATURE_SECURE_PROCESSING FSP} and the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties
     *         are not recognized, and libexpat enforces its own Billion Laughs check, so neither is applied. Two fixups are still needed: an ignore-all resolver
     *         (Expat ignores external fetches silently when no resolver is set; the floor keeps that behavior non-bypassable, resolving anything unresolved to
     *         empty), and a {@link HardeningExpatXMLReader} so the unsupported {@code namespace-prefixes} feature is rejected at
     *         configuration time rather than mid-parse.</li>
     *     <li><strong>FSP</strong>: required on every other reader. It switches on the implementation's built-in security manager, which is what carries the
     *         processing limits.</li>
     *     <li><strong>Ignore-all resolver floor</strong>: every reader is wrapped in a {@link SecureXMLReader} that keeps an ignore-all {@link EntityResolver} floor.
     *         That floor blocks external DTD, entity, schema and {@code xi:include} fetches in one place: the stock JDK's XInclude processor ignores
     *         {@code ACCESS_EXTERNAL_*} and consults the {@link EntityResolver} instead, so no {@code ACCESS_EXTERNAL_*} properties are needed here. A caller can
     *         chain its own resolver onto the floor to allow-list resources, but cannot remove it.</li>
     * </ul>
     *
     * @param factory the factory to harden; never {@code null}.
     * @return a hardened factory.
     */
    static SAXParserFactory secure(final SAXParserFactory factory) {
        // Required: enables the implementation's security manager, which carries the limits. Android's Expat rejects FSP, so it is skipped there.
        if (!ANDROID_SAX_PARSER_FACTORY.equals(factory.getClass().getName())) {
            setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }
        // The per-parse hardening (limits, entity blocking, Android fixups) lives in secure(XMLReader) because SAXParserFactory has no property API.
        return new Wrapper(factory);
    }

    /**
     * Rewrites a {@link Source} so that any SAX parsing it triggers runs through a hardened {@link XMLReader}.
     * <p>
     * Only a {@link StreamSource} or a {@link SAXSource} without a reader is enriched with a hardened, namespace-aware reader; other source kinds are returned
     * as-is. Used by the TrAX and schema wrappers to route every source they parse through the SAX hardening path.
     * </p>
     *
     * @param source           the source to harden; never {@code null}.
     * @param overrideDefaultParser whether {@value #OVERRIDE_DEFAULT_PARSER} on the originating factory asks to override the JDK's default parser.
     * @return a hardened source.
     * @throws TransformerConfigurationException if a hardened reader cannot be obtained.
     * @throws FactoryConfigurationError         Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                           configuration error} or if the implementation is not available or cannot be instantiated.
     */
    static Source secure(final Source source, final boolean overrideDefaultParser) throws TransformerConfigurationException {
        if (source instanceof StreamSource || source instanceof SAXSource && ((SAXSource) source).getXMLReader() == null) {
            final InputSource inputSource = SAXSource.sourceToInputSource(source);
            return inputSource == null ? source : new SAXSource(newHardenedReader(overrideDefaultParser), inputSource);
        }
        return source;
    }

    /**
     * Secures an existing {@link XMLReader}.
     *
     * @param reader The reader to harden; never {@code null}.
     * @return A hardened reader.
     * @throws IllegalStateException if a required hardening setting cannot be applied to the underlying implementation.
     */
    static XMLReader secure(final XMLReader reader) {
        if (reader instanceof SecureXMLReader) {
            // Already hardened (for example, a reader from a hardened factory passed back through secure(XMLReader)); the floor is already in place.
            return reader;
        }
        if (ANDROID_EXPAT_READER.equals(reader.getClass().getName())) {
            // Expat ignores external fetches when no resolver is set; the ignore-all floor keeps that behavior non-bypassable (routing a caller-set resolver,
            // including SAXParser.parse's handler, through it and resolving anything unresolved to empty) and, via HardeningExpatXMLReader, rejects the
            // unsupported namespace-prefixes feature eagerly rather than mid-parse.
            return new HardeningExpatXMLReader(reader);
        }
        // Required: enables the JDK XMLSecurityManager / Xerces SecurityManager limits.
        setFeature(reader, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Required: SecureXMLReader installs an ignore-all EntityResolver floor on the reader.
        // That floor blocks external DTD, entity, schema and xi:include fetches in one place: no ACCESS_EXTERNAL_* properties are needed here.
        // Callers can chain their resolvers, but not override the floor.
        return new SecureXMLReader(reader);
    }

    /**
     * Enables namespace awareness on the given factory; the {@code NSInstance} counterpart of each factory method routes its result through here.
     *
     * @param factory the factory to configure; never {@code null}.
     * @return The given factory, namespace-aware.
     */
    private static SAXParserFactory makeNSAware(final SAXParserFactory factory) {
        factory.setNamespaceAware(true);
        return factory;
    }

    /**
     * Returns a new, hardened {@link SAXParserFactory} of the system-default implementation.
     * <p>
     * Obtained as by {@code SAXParserFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and by
     * instantiating the JDK's built-in implementation directly on Java 8.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation
     *                                   (for example Android).
     */
    public static SAXParserFactory newDefaultInstance() {
        if (NEW_DEFAULT_INSTANCE != null) {
            final SAXParserFactory factory;
            try {
                factory = (SAXParserFactory) NEW_DEFAULT_INSTANCE.invokeExact();
            } catch (final FactoryConfigurationError e) {
                throw e;
            } catch (final Throwable e) {
                // Unreachable: the looked-up method declares no other exceptions.
                throw new IllegalStateException(e);
            }
            return secure(factory);
        }
        // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead. Where that class does not exist either (for
        // example Android), the lookup miss surfaces as the factory's own FactoryConfigurationError, like any newInstance miss.
        return newInstance(JDK_SAX_PARSER_FACTORY, null);
    }

    /**
     * Returns a new, hardened, namespace-aware {@link SAXParserFactory} of the system-default implementation, enabling namespace awareness on
     * {@link #newDefaultInstance()}, the behavior {@code SAXParserFactory.newDefaultNSInstance()} (Java 13 or later) is specified to have.
     *
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation
     *                                   (for example Android).
     */
    public static SAXParserFactory newDefaultNSInstance() {
        return makeNSAware(newDefaultInstance());
    }

    /**
     * Creates a new hardened, namespace-aware {@link XMLReader} for the TrAX, XPath and schema wrappers to parse sources with, from the factory
     * {@link #newNSInstance(boolean)} selects.
     *
     * @param overrideDefaultParser whether {@value #OVERRIDE_DEFAULT_PARSER} on the originating factory asks to override the JDK's default parser.
     * @return a hardened reader.
     * @throws TransformerConfigurationException if a hardened reader cannot be obtained.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    static XMLReader newHardenedReader(final boolean overrideDefaultParser) throws TransformerConfigurationException {
        try {
            return newNSInstance(overrideDefaultParser).newSAXParser().getXMLReader();
        } catch (final ParserConfigurationException | SAXException e) {
            throw new TransformerConfigurationException("Failed to obtain a hardened XMLReader for source parsing", e);
        }
    }

    /**
     * Returns a new, hardened {@link SAXParserFactory}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from {@link SAXParserFactory} in case of a {@link java.util.ServiceConfigurationError service configuration
     *                                   error} or if the implementation is not available or cannot be instantiated.
     */
    public static SAXParserFactory newInstance() {
        return secure(SAXParserFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link SAXParserFactory} of the given implementation class.
     *
     * @param factoryClassName The fully qualified class name of the {@link SAXParserFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static SAXParserFactory newInstance(final String factoryClassName, final ClassLoader classLoader) {
        return secure(SAXParserFactory.newInstance(factoryClassName, classLoader));
    }

    /**
     * Returns a new, hardened, namespace-aware {@link SAXParserFactory}, enabling namespace awareness on {@link #newInstance()}, the behavior
     * {@code SAXParserFactory.newNSInstance()} (Java 13 or later) is specified to have.
     *
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from {@link SAXParserFactory} in case of a {@link java.util.ServiceConfigurationError service configuration
     *                                   error} or if the implementation is not available or cannot be instantiated.
     */
    public static SAXParserFactory newNSInstance() {
        return makeNSAware(newInstance());
    }

    /**
     * Returns the hardened, namespace-aware factory the Source-rewriting wrappers parse with.
     * <p>
     * While {@code overrideDefaultParser} is {@code false} the factory is the JDK's "default parser" factory, determined the way the JDK itself determines it: the built-in parser,
     * unless the {@value #SAX_FACTORY_ID} system property is set — that property is the JDK's own mechanism for reconfiguring the default
     * parser, so it is honored through the standard lookup rather than bypassed.
     * </p>
     *
     * @param overrideDefaultParser whether {@value #OVERRIDE_DEFAULT_PARSER} on the originating factory asks to override the JDK's default parser.
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    static SAXParserFactory newNSInstance(final boolean overrideDefaultParser) {
        return overrideDefaultParser || System.getProperty(SAX_FACTORY_ID) != null ? newNSInstance() : newDefaultNSInstance();
    }

    /**
     * Returns a new, hardened, namespace-aware {@link SAXParserFactory} of the given implementation class, enabling namespace awareness on
     * {@link #newInstance(String, ClassLoader)}, the behavior {@code SAXParserFactory.newNSInstance(String, ClassLoader)} (Java 13 or later) is specified to have.
     *
     * @param factoryClassName The fully qualified class name of the {@link SAXParserFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened, namespace-aware factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static SAXParserFactory newNSInstance(final String factoryClassName, final ClassLoader classLoader) {
        return makeNSAware(newInstance(factoryClassName, classLoader));
    }

    private static void setFeature(final SAXParserFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw SecureException.settingFailed("feature", feature, factory, e);
        }
    }

    private static void setFeature(final XMLReader reader, final String feature, final boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (final Exception e) {
            throw SecureException.settingFailed("feature", feature, reader, e);
        }
    }

    private SecureSAXParserFactory() {
        // static only
    }

    /**
     * {@link SecureXMLReader} for Android's {@code org.apache.harmony.xml.ExpatReader} that additionally surfaces its {@code namespace-prefixes} limitation at
     * configuration time.
     *
     * <p>ExpatReader does not actually support the {@code namespace-prefixes} feature: enabling it is accepted by {@code setFeature} but fails later, during
     * {@code parse}, with a {@link SAXNotSupportedException}. Reporting the rejection eagerly from {@link #setFeature(String, boolean)} lets consumers that probe
     * the feature, such as Xalan's identity transformer, catch the exception and fall back instead of failing the whole parse.</p>
     */
    static final class HardeningExpatXMLReader extends SecureXMLReader {

        private static final String NAMESPACE_PREFIXES_FEATURE = "http://xml.org/sax/features/namespace-prefixes";

        HardeningExpatXMLReader(final XMLReader delegate) {
            super(delegate);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (value && NAMESPACE_PREFIXES_FEATURE.equals(name)) {
                throw new SAXNotSupportedException("ExpatReader does not support enabling the '" + NAMESPACE_PREFIXES_FEATURE + "' feature");
            }
            super.setFeature(name, value);
        }
    }

    /**
     * Universal SAX factory wrapper that funnels every produced parser through {@link SecureSAXParserFactory#secure(XMLReader)}.
     * <p>
     * {@link SAXParserFactory} exposes only a feature API and no property API, so the per-parse hardening (limits, entity blocking, implementation-specific fixups)
     * has to run on each {@link XMLReader} the factory produces. This wrapper returns a {@link SecureSAXParser}, which applies that hardening lazily to both the
     * SAX 2 {@link XMLReader} and the SAX 1 {@link org.xml.sax.Parser} it exposes.
     * </p>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends SAXParserFactory {

        private final SAXParserFactory delegate;

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final SAXParserFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean getFeature(final String name) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getFeature(name);
        }

        @Override
        public Schema getSchema() {
            return delegate.getSchema();
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
        public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
            return new SecureSAXParser(delegate.newSAXParser());
        }

        @Override
        public void setFeature(final String name, final boolean value) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setFeature(name, value);
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
}
