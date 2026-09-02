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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/**
 * Creates new, secure {@link XPathFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml.secure}, URI-fetching XPath 3.1+ functions ({@code doc()}, {@code collection()},
 * {@code unparsed-text()}) are not resolved.
 * </p>
 * <p>
 * The guarantees also cover the document parse behind {@code XPath.evaluate(String, InputSource)} and {@code XPathExpression.evaluate(InputSource)}: the input
 * document is built through a secure, namespace-aware {@link javax.xml.parsers.DocumentBuilder} instead of the engine's internal parser.
 * </p>
 * <p>
 * This class is not itself a {@link XPathFactory}, so it inherits none of the static JAXP factory methods. A caller therefore cannot obtain an unsecured
 * factory through this class by calling a method such as {@code newDefaultInstance()}. The secure factories are instances of a nested, non-public wrapper
 * class.
 * </p>
 *
 * @see org.apache.commons.xml.secure
 */
public final class SecureXPathFactory {

    /**
     * {@link XPathFactory} wrapper that returns a {@link SecureXPath} from {@link #newXPath()}.
     * <p>
     * Required because {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} on the factory governs only the XPath engine: the stock JDK and Apache Xalan
     * implement the {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points by provisioning an internal document parser the feature does not
     * reach. The wrapper performs that document build itself through a secure parser instead; see {@link SecureXPath}.
     * </p>
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

        /**
         * Reports a property of the delegate, the Java 18 {@code XPathFactory.getProperty(String)}.
         * <p>
         * Not marked {@code @Override}: this library compiles against the Java 8 API, where {@link XPathFactory} declares no such method, so the annotation
         * would not compile. At run time on Java 18 or later it overrides the inherited method, which would otherwise answer for the wrapper and hide the
         * delegate's own limits ({@code jdk.xml.xpath*}) behind an {@code UnsupportedOperationException}.
         * </p>
         *
         * @param name the property name.
         * @return the delegate's value for the property.
         */
        public String getProperty(final String name) {
            if (MH_getProperty == null) {
                throw new UnsupportedOperationException("XPathFactory.getProperty(String) requires Java 18 or later");
            }
            return MethodHandleFactory.invokeExact(() -> (String) MH_getProperty.invokeExact(delegate, name), RuntimeException.class);
        }

        @Override
        public boolean isObjectModelSupported(final String objectModel) {
            return delegate.isObjectModelSupported(objectModel);
        }

        @Override
        public XPath newXPath() {
            // newXPath() should never return null for a specification-compliant factory.
            final XPath xpath = delegate.newXPath();
            return xpath == null ? null : new SecureXPath(xpath, overrideDefaultParser());
        }

        /**
         * Tests whether parsers should be instantiated via {@code newInstance()} instead of {@code newDefaultInstance()}.
         * <p>
         * The JDK implementation of {@link XPathFactory} uses the JDK parsers while {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} is unset or
         * {@code false}.
         * </p>
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
        public void setFeature(final String name, final boolean value) throws XPathFactoryConfigurationException {
            delegate.setFeature(name, value);
        }

        /**
         * Sets a property on the delegate, the Java 18 {@code XPathFactory.setProperty(String, String)}; see {@link #getProperty(String)} for why it carries no
         * {@code @Override}. The {@code jdk.xml.xpath*} limits reached this way are processing limits like any other: an operator may tighten them, and
         * loosening one is reconfiguration.
         *
         * @param name  the property name.
         * @param value the value to set.
         */
        public void setProperty(final String name, final String value) {
            if (MH_setProperty == null) {
                throw new UnsupportedOperationException("XPathFactory.setProperty(String, String) requires Java 18 or later");
            }
            MethodHandleFactory.invokeExact(() -> {
                MH_setProperty.invokeExact(delegate, name, value);
                return null;
            }, RuntimeException.class);
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

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_XPATH_FACTORY = "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl";

    private static final MethodHandle MH_newDefaultInstance = MethodHandleFactory.findStatic(XPathFactory.class, "newDefaultInstance");

    /** {@code XPathFactory.getProperty(String)}, added in Java 18; {@code null} on earlier releases, where the method does not exist. */
    private static final MethodHandle MH_getProperty = MethodHandleFactory.findVirtual(XPathFactory.class, "getProperty", String.class, String.class);

    /** {@code XPathFactory.setProperty(String, String)}, added in Java 18; {@code null} on earlier releases, where the method does not exist. */
    private static final MethodHandle MH_setProperty =
            MethodHandleFactory.findVirtual(XPathFactory.class, "setProperty", void.class, String.class, String.class);

    /**
     * Returns a new, secure {@link XPathFactory} of the system-default implementation, supporting the default XPath object model.
     * <p>
     * Obtained from {@code XPathFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and by instantiating the JDK's built-in
     * implementation directly on Java 8.
     * </p>
     *
     * @return A secure factory.
     * @throws IllegalStateException Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in implementation (for
     *                               example Android).
     */
    public static XPathFactory newDefaultInstance() {
        if (MH_newDefaultInstance != null) {
            return secure(MethodHandleFactory.invokeExact(() -> (XPathFactory) MH_newDefaultInstance.invokeExact(), RuntimeException.class));
        }
        try {
            // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead.
            return newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, JDK_XPATH_FACTORY, null);
        } catch (final XPathFactoryConfigurationException e) {
            // newDefaultInstance declares no checked exception; mirror XPathFactory.newInstance(), which reports a default-model miss as a RuntimeException.
            throw new RuntimeException("Neither XPathFactory.newDefaultInstance() nor " + JDK_XPATH_FACTORY + " is available", e);
        }
    }

    /**
     * Returns a new, secure {@link XPathFactory} for the default XPath object model.
     *
     * @return A secure factory.
     * @throws IllegalStateException Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if there is a failure in creating an {@link XPathFactory} for the default object model.
     */
    public static XPathFactory newInstance() {
        return secure(XPathFactory.newInstance());
    }

    /**
     * Returns a new, secure {@link XPathFactory} for the given object model.
     *
     * @param uri The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @return A secure factory.
     * @throws IllegalStateException              Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException Thrown if no implementation of the object model is available.
     * @throws NullPointerException               Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException           Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri) throws XPathFactoryConfigurationException {
        return secure(XPathFactory.newInstance(uri));
    }

    /**
     * Returns a new, secure {@link XPathFactory} of the given implementation class.
     *
     * @param uri              The underlying object model identifier, as accepted by {@link XPathFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link XPathFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A secure factory.
     * @throws IllegalStateException              Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws XPathFactoryConfigurationException Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or
     *                                            instantiated, or does not support {@code uri}.
     * @throws NullPointerException               Thrown if {@code uri} is {@code null}.
     * @throws IllegalArgumentException           Thrown if {@code uri} is empty.
     */
    public static XPathFactory newInstance(final String uri, final String factoryClassName, final ClassLoader classLoader)
            throws XPathFactoryConfigurationException {
        return secure(XPathFactory.newInstance(uri, factoryClassName, classLoader));
    }

    /**
     * Capability-driven securing for any {@link XPathFactory} on the classpath.
     *
     * <p>The XPath object model mirrors TrAX: the stock JDK and Apache Xalan ship an XPath 1.0 engine with no URI-fetching functions, while Saxon adds the XPath 3.1
     * {@code fn:doc}, {@code fn:collection} and {@code fn:unparsed-text} functions that can reach external resources. Rather than branching on the implementation
     * class, this method probes what the factory supports and adapts:</p>
     * <ul>
     *     <li><strong>Saxon</strong> ({@code net.sf.saxon}): recognized by package prefix and handed to {@link SaxonProvider#configure(XPathFactory)}, so any public
     *         subclass routes to the same recipe as the registered factory. Its URI-fetching
     *         functions and reflection-based extension calls are reachable only through a locked-down Saxon {@code Configuration}, not the standard JAXP knobs; this
     *         is the XPath counterpart of the Saxon exception in {@link SecureTransformerFactory#secure(javax.xml.transform.TransformerFactory)}, kept as a
     *         documented package-prefix exception because the required securing surface is reachable only through a vendor API.</li>
     *     <li><strong>FSP</strong> ({@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING}): required. It is the only knob both the stock JDK and Xalan XPath
     *         engines expose, and switches on their secure-processing limits. {@link XPathFactory} has no attribute API for finer control.</li>
     *     <li><strong>The nested wrapper</strong>: required. FSP governs only the engine, not the parser it provisions internally for the
     *         {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points; the wrapper performs that document build with a secure parser instead, so
     *         the engine never parses.</li>
     * </ul>
     *
     * @param factory The factory to secure.
     * @return A new secure factory or the original factory, as-is, if it is a known Saxon factory.
     * @throws SecureException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature.
     */
    static XPathFactory secure(final XPathFactory factory) {
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
     * Sets a feature on the given factory, throwing a {@link SecureException} if the implementation does not recognize it.
     *
     * @param factory The factory to secure.
     * @param feature The feature to set.
     * @param value   The value to set.
     * @throws SecureException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature or if {@code feature} is
     *                            {@code null}.
     */
    private static void setFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final XPathFactoryConfigurationException e) {
            throw SecureException.featureFailed(feature, factory, e);
        }
    }

    private SecureXPathFactory() {
        // static only
    }
}
