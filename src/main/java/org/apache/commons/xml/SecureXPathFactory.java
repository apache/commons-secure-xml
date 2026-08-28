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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/**
 * Creates new, hardened {@link XPathFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml}, URI-fetching XPath 3.1+ functions ({@code doc()}, {@code collection()},
 * {@code unparsed-text()}) are not resolved.
 * </p>
 * <p>
 * The guarantees also cover the document parse behind {@code XPath.evaluate(String, InputSource)} and {@code XPathExpression.evaluate(InputSource)}: the
 * input document is built through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} instead of the engine's internal parser.
 * </p>
 * <p>
 * Not a {@link XPathFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class SecureXPathFactory {

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_XPATH_FACTORY = "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl";

    private static final MethodHandle NEW_DEFAULT_INSTANCE = MethodHandleFactory.findStatic(XPathFactory.class, "newDefaultInstance",
            MethodType.methodType(XPathFactory.class));

    /**
     * Capability-driven hardening for any {@link XPathFactory} on the classpath.
     *
     * <p>The XPath object model mirrors TrAX: the stock JDK and Apache Xalan ship an XPath 1.0 engine with no URI-fetching functions, while Saxon adds the XPath 3.1
     * {@code fn:doc}, {@code fn:collection} and {@code fn:unparsed-text} functions that can reach external resources. Rather than branching on the implementation
     * class, this method probes what the factory supports and adapts:</p>
     * <ul>
     *     <li><strong>Saxon</strong> ({@code net.sf.saxon}): recognized by package prefix and handed to {@link SaxonProvider#configure(XPathFactory)}, so any public
     *         subclass routes to the same recipe as the registered factory. Its URI-fetching
     *         functions and reflection-based extension calls are reachable only through a locked-down Saxon {@code Configuration}, not the standard JAXP knobs; this
     *         is the XPath counterpart of the Saxon exception in {@link SecureTransformerFactory#harden(javax.xml.transform.TransformerFactory)}, kept as a
     *         documented package-prefix exception because the required hardening surface is reachable only through a vendor API.</li>
     *     <li><strong>FSP</strong> ({@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING}): required. It is the only knob both the stock JDK and Xalan XPath
     *         engines expose, and switches on their secure-processing limits. {@link XPathFactory} has no attribute API for finer control.</li>
     *     <li><strong>The nested wrapper</strong>: required. FSP governs only the engine, not the parser it provisions internally for the
     *         {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points; the wrapper performs that document build with a hardened parser instead, so
     *         the engine never parses.</li>
     * </ul>
     *
     * @param factory The factory to harden.
     * @return A new hardened factory or the original factory, hardened, if it is a known Saxon factory.
     * @throws SecureException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature.
     */
    static XPathFactory harden(final XPathFactory factory) {
        if (SaxonProvider.isSaxon(factory.getClass())) {
            // Saxon: only a locked-down Configuration can close its URI-fetching functions and extension-function surface.
            return SaxonProvider.configure(factory);
        }
        // Required: enables the engine's secure-processing limits; XPathFactory has no attribute API for finer control.
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Required: FSP does not reach the parser the engine provisions for InputSource-taking evaluate calls; the wrapper parses those itself.
        return new Wrapper(factory);
    }

    /**
     * Returns a new, hardened {@link XPathFactory} of the system-default implementation, supporting the default XPath object model.
     * <p>
     * Obtained as by {@code XPathFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and by instantiating the JDK's built-in
     * implementation directly on Java 8.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation (for
     *                               example Android).
     */
    public static XPathFactory newDefaultInstance() {
        if (NEW_DEFAULT_INSTANCE != null) {
            final XPathFactory factory;
            try {
                factory = (XPathFactory) NEW_DEFAULT_INSTANCE.invokeExact();
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                // Unreachable: the looked-up method declares no other exceptions.
                throw new IllegalStateException(e);
            }
            return harden(factory);
        }
        try {
            // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead.
            return newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, JDK_XPATH_FACTORY, null);
        } catch (final XPathFactoryConfigurationException e) {
            // newDefaultInstance declares no checked exception; mirror XPathFactory.newInstance(), which reports a default-model miss as a RuntimeException.
            throw new RuntimeException(
                    "Neither XPathFactory.newDefaultInstance() nor " + JDK_XPATH_FACTORY + " is available", e);
        }
    }

    /**
     * Returns a new, hardened {@link XPathFactory} for the default XPath object model.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if there is a failure in creating an {@link XPathFactory} for the default object model.
     */
    public static XPathFactory newInstance() {
        return harden(XPathFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XPathFactory} for the given object model.
     *
     * @param uri The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @return A hardened factory.
     * @throws IllegalStateException              Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException Thrown if no implementation of the object model is available.
     * @throws NullPointerException               Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException           Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri) throws XPathFactoryConfigurationException {
        return harden(XPathFactory.newInstance(uri));
    }

    /**
     * Returns a new, hardened {@link XPathFactory} of the given implementation class.
     *
     * @param uri              The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link XPathFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException              Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or
     *                                            instantiated, or does not support {@code uri}.
     * @throws NullPointerException               Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException           Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri, final String factoryClassName, final ClassLoader classLoader)
            throws XPathFactoryConfigurationException {
        return harden(XPathFactory.newInstance(uri, factoryClassName, classLoader));
    }

    /**
     * Sets a feature on the given factory, throwing a {@link SecureException} if the implementation does not recognize it.
     *
     * @param factory The factory to harden.
     * @param feature The feature to set.
     * @param value   The value to set.
     * @throws SecureException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature or if {@code feature} is
     *                            {@code null}.
     */
    private static void setFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final XPathFactoryConfigurationException e) {
            throw SecureException.settingFailed("feature", feature, factory, e);
        }
    }

    private SecureXPathFactory() {
        // static only
    }

    /**
     * {@link XPathFactory} wrapper that returns a {@link SecureXPath} from {@link #newXPath()}.
     *
     * <p>Required because {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} on the factory governs only the XPath engine: the stock JDK and Apache Xalan
     * implement the {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points by provisioning an internal document parser the feature does not reach.
     * The wrapper performs that document build itself through a hardened parser instead; see {@link SecureXPath}.</p>
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
        private Wrapper(final XPathFactory delegate) {
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
            return xpath == null ? null : new SecureXPath(xpath, overrideDefaultParser());
        }

        @Override
        public void setFeature(final String name, final boolean value) throws XPathFactoryConfigurationException {
            delegate.setFeature(name, value);
        }

        /**
         * Checks whether parsers should be instantiated via {@code newInstance()} instead of {@code newDefaultInstance()}.
         *
         * <p>The JDK implementation of {@link XPathFactory} uses the JDK parsers while {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} is unset or
         * {@code false}.</p>
         *
         * @return {@code true} if parsers should be created via {@code newInstance()}.
         */
        private boolean overrideDefaultParser() {
            try {
                return delegate.getFeature(SecureSAXParserFactory.OVERRIDE_DEFAULT_PARSER);
            } catch (final XPathFactoryConfigurationException e) {
                return true;
            }
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
