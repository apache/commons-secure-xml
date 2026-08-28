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

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

/**
 * Creates new, hardened {@link TransformerFactory} instances.
 * <p>
 * Beyond the three universal guarantees on {@link org.apache.commons.xml}: {@code xsl:import}, {@code xsl:include} and {@code document()} URIs are not resolved.
 * </p>
 * <p>
 * The guarantees govern what the transform reads, not what it writes: an output instruction like {@code xsl:result-document} still writes wherever the
 * stylesheet directs, so an untrusted stylesheet's output destinations must be restricted outside the library.
 * </p>
 * <p>
 * The guarantees apply to every parser the factory creates internally for the standard {@link TransformerFactory} entry points: stylesheet compilation
 * ({@link TransformerFactory#newTemplates(javax.xml.transform.Source) newTemplates(Source)},
 * {@link TransformerFactory#newTransformer(javax.xml.transform.Source) newTransformer(Source)}) and source-document reading at
 * {@code Transformer.transform(Source, Result)} time.
 * </p>
 * <p>
 * The {@link javax.xml.transform.sax.SAXTransformerFactory} extension methods ({@code newTransformerHandler(..)}, {@code newTemplatesHandler()},
 * {@code newXMLFilter(..)}), if reachable by casting the returned factory, produce objects carrying the same guarantees.
 * </p>
 * <p>
 * Not a {@link TransformerFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningTransformerFactory {

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultInstance()}. */
    private static final String JDK_TRANSFORMER_FACTORY = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    private static final MethodHandle NEW_DEFAULT_INSTANCE = MethodHandleFactory.findStatic(TransformerFactory.class, "newDefaultInstance",
            MethodType.methodType(TransformerFactory.class));

    /**
     * Capability-driven hardening for any {@link TransformerFactory} on the classpath.
     *
     * <p>Rather than branching on the implementation class, this method probes what the factory supports and adapts:</p>
     * <ul>
     *     <li><strong>Saxon</strong> ({@code net.sf.saxon}): recognized by package prefix and handed to {@link SaxonProvider#configure(TransformerFactory)} for the
     *         channels the standard JAXP knobs cannot close (reflection-based extension functions, the collection finder, the internal SAX parser). It is then
     *         wrapped like every other implementation to install the {@link FallbackIgnoreURIResolver} floor; the only
     *         difference is the empty-{@link Source} shape the floor returns, {@code EmptySource} for Saxon rather than the default empty DOM document.</li>
     *     <li><strong>FSP</strong> ({@link XMLConstants#FEATURE_SECURE_PROCESSING}): required. On XSLTC it enables the runtime evaluator limits; on Xalan it disables
     *         reflection-based extension functions.</li>
     *     <li><strong>{@link FallbackIgnoreURIResolver} floor</strong>: required. An ignore-all {@link URIResolver} floor, installed by
     *         the nested wrapper and carried onto every produced {@link Transformer}, resolves {@code xsl:import}/{@code xsl:include} at compile
     *         time and {@code document()} at runtime to an empty document, the one channel both XSLTC and Xalan route through. A caller-set {@link URIResolver} is
     *         routed through the floor rather than replacing it, so a caller can opt a specific URI in but cannot reopen the fetch.</li>
     *     <li><strong>The nested wrapper</strong>: required. Both implementations fall back to {@code SAXParserFactory.newInstance()} to parse a
     *         stylesheet or source document that does not carry its own reader, and only set FSP on it; wrapping the factory rewrites every {@link Source} through an
     *         {@link org.apache.commons.xml}-hardened reader instead.</li>
     * </ul>
     *
     * @param factory the factory to harden; never {@code null}.
     * @return a hardened factory.
     */
    static TransformerFactory harden(final TransformerFactory factory) {
        // Required: enables secure processing (XSLTC runtime limits; Xalan's extension-function block).
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        if (SaxonProvider.isSaxon(factory.getClass())) {
            // Saxon keeps its vendor Configuration for the channels JAXP cannot close,
            // then goes through the same wrapper as every other implementation for the URIResolver floor;
            // EmptySource is the empty-source shape Saxon's consumers expect.
            return new Wrapper((SAXTransformerFactory) SaxonProvider.configure(factory), SaxonProvider.emptySourceSupplier());
        }
        // Required: source/stylesheet parsing provisions its own SAX reader otherwise; the wrapper routes every Source through a hardened one and installs the
        // ignore-all URIResolver floor (blocking xsl:import/include at compile time and document() at runtime) that a caller-set resolver cannot remove.
        return new Wrapper((SAXTransformerFactory) factory);
    }

    /**
     * Returns a new, hardened {@link TransformerFactory} of the system-default implementation.
     * <p>
     * Obtained as by {@code TransformerFactory.newDefaultInstance()} where the platform provides it (Java 9 or later), and by instantiating the JDK's built-in
     * implementation directly on Java 8.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException                Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws TransformerFactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultInstance()} nor the JDK's built-in
     *                                                implementation (for example Android).
     */
    public static TransformerFactory newDefaultInstance() {
        if (NEW_DEFAULT_INSTANCE != null) {
            final TransformerFactory factory;
            try {
                factory = (TransformerFactory) NEW_DEFAULT_INSTANCE.invokeExact();
            } catch (final TransformerFactoryConfigurationError e) {
                throw e;
            } catch (final Throwable e) {
                // Unreachable: the looked-up method declares no other exceptions.
                throw new IllegalStateException(e);
            }
            return harden(factory);
        }
        // Java 8: the method does not exist; instantiate the JDK's built-in default by its class name instead. Where that class does not exist either (for
        // example Android), the lookup miss surfaces as TransformerFactoryConfigurationError, like any newInstance miss.
        return newInstance(JDK_TRANSFORMER_FACTORY, null);
    }

    /**
     * Returns a new, hardened {@link TransformerFactory}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static TransformerFactory newInstance() {
        return harden(TransformerFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link TransformerFactory} of the given implementation class.
     *
     * @param factoryClassName The fully qualified class name of the {@link TransformerFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException                Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws TransformerFactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static TransformerFactory newInstance(final String factoryClassName, final ClassLoader classLoader) {
        return harden(TransformerFactory.newInstance(factoryClassName, classLoader));
    }

    private static void setFeature(final TransformerFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw HardeningException.settingFailed("feature", feature, factory, e);
        }
    }

    private HardeningTransformerFactory() {
        // static only
    }

    /**
     * {@link TransformerFactory} wrapper that rewrites every Source-taking entry point through {@link HardeningSAXParserFactory#harden(Source, boolean)} before
     * delegating.
     *
     * <p>Used by providers whose underlying TrAX implementation pulls a new {@code SAXParserFactory.newInstance()} for any Source that is not already a
     * {@link SAXSource} carrying its own {@link XMLReader}, and only sets {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} on the resulting reader.
     * Wrapping the factory and rewriting the Source upstream guarantees the parse runs through an {@link org.apache.commons.xml}-hardened reader instead.</p>
     *
     * <p>Three layers cooperate:</p>
     * <ol>
     *   <li>{@link HardeningTransformerFactory} rewrites the Source on every entry point that compiles a stylesheet or transforms a one-shot input.</li>
     *   <li>{@link HardeningTemplates} returns a {@link HardeningTransformer} from {@link Templates#newTransformer()} so runtime source parsing is also covered, and
     *       restores the factory's URIResolver onto the produced Transformer (which the underlying implementation typically does not propagate through
     *       {@code Templates}).</li>
     *   <li>{@link HardeningTransformer} rewrites the Source on every {@link Transformer#transform(Source, javax.xml.transform.Result)} call.</li>
     * </ol>
     *
     * <p>The {@link SAXTransformerFactory} extension products ride the same wrappers: {@code newTransformerHandler}/{@code newTemplatesHandler} products are
     * wrapped ({@link HardeningTransformerHandler}, {@link HardeningTemplatesHandler}) so the {@link Transformer}/{@link Templates} they expose carry the resolver
     * floor, and {@code newXMLFilter} returns a {@link HardeningXMLFilter} composed from these wrappers instead of the implementation's filter, which would
     * self-provision an unhardened input reader.</p>
     *
     * <h2>Caveats</h2>
     * <ul>
     *   <li>A {@link SAXSource} that carries its own {@link XMLReader} is trusted as-is: the caller is expected to supply a hardened reader (via
     *       {@link HardeningSAXParserFactory#newInstance()}) in that case. The same applies to the SAX events a caller feeds into a handler, and to a parent reader a
     *       caller sets on a returned {@link XMLFilter}.</li>
     * </ul>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends SAXTransformerFactory {

        /**
         * Parses a reader-less source into a DOM through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} and returns a {@link DOMSource}
         * carrying its system id, so the consumer walks the tree instead of provisioning its own reader. Any other source is left to
         * {@link HardeningSAXParserFactory#harden(Source, boolean)}.
         *
         * @param source The source to scan for an associated stylesheet.
         * @return A {@link DOMSource} for a reader-less source, otherwise the result of {@link HardeningSAXParserFactory#harden(Source, boolean)}.
         * @throws TransformerConfigurationException if the source cannot be parsed.
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         * @throws HardeningException Thrown if a (non-Andoid) factory cannot support the secure processing feature {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
         */
        private Source hardenSourceToDom(final Source source) throws TransformerConfigurationException {
            if (source instanceof StreamSource || source instanceof SAXSource && ((SAXSource) source).getXMLReader() == null) {
                final InputSource inputSource = SAXSource.sourceToInputSource(source);
                if (inputSource != null) {
                    try {
                        final DocumentBuilderFactory factory = HardeningDocumentBuilderFactory.newNSInstance(overrideDefaultParser());
                        final Document document = factory.newDocumentBuilder().parse(inputSource);
                        return new DOMSource(document, inputSource.getSystemId());
                    } catch (final ParserConfigurationException | SAXException | IOException e) {
                        throw new TransformerConfigurationException("Failed to parse the source for associated-stylesheet lookup", e);
                    }
                }
            }
            return HardeningSAXParserFactory.harden(source, overrideDefaultParser());
        }

        /**
         * Whether the delegate is Apache Xalan (either its interpretive or its XSLTC factory), whose {@code getAssociatedStylesheet} ignores a SAXSource reader.
         *
         * @param factory The delegate factory.
         * @return Whether the delegate is an {@code org.apache.xalan.} implementation.
         */
        private static boolean isXalan(final SAXTransformerFactory factory) {
            return factory.getClass().getName().startsWith("org.apache.xalan.");
        }

        /**
         * Whether the delegate recognizes {@value HardeningSAXParserFactory#OVERRIDE_DEFAULT_PARSER}, probed with a same-value {@code setFeature}:
         * {@code TransformerFactory.getFeature} cannot signal an unrecognized name (it returns {@code false}), while every implementation rejects a
         * {@code setFeature} for a name it does not support (Xalan with {@link TransformerConfigurationException}, Saxon with its own unchecked exception).
         *
         * @param factory The delegate factory.
         * @return Whether the delegate recognizes the feature.
         */
        private static boolean probeOverrideDefaultParser(final SAXTransformerFactory factory) {
            try {
                factory.setFeature(HardeningSAXParserFactory.OVERRIDE_DEFAULT_PARSER,
                        factory.getFeature(HardeningSAXParserFactory.OVERRIDE_DEFAULT_PARSER));
                return true;
            } catch (final Exception e) {
                return false;
            }
        }

        private static Templates unwrap(final Templates templates) {
            return templates instanceof HardeningTemplates ? ((HardeningTemplates) templates).getDelegate() : templates;
        }

        private final SAXTransformerFactory delegate;

        /**
         * Empty-{@link Source} supplier for the resolver floor, threaded onto every produced Templates/Transformer; {@code null} means the default empty DOM.
         */
        private final Supplier<Source> emptySource;

        private final FallbackIgnoreURIResolver floor;

        /** Whether the delegate recognizes {@value HardeningSAXParserFactory#OVERRIDE_DEFAULT_PARSER}; its value is read per created product, like the JDK. */
        private final boolean supportsOverrideDefaultParser;

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final SAXTransformerFactory delegate) {
            this(delegate, null);
        }

        /**
         * Constructs a new instance.
         *
         * @param delegate    the delegate to wrap; must not be {@code null}.
         * @param emptySource the empty-{@link Source} supplier for the resolver floor, threaded onto every produced Templates/Transformer; {@code null} means the
         *                    default empty DOM.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final SAXTransformerFactory delegate, final Supplier<Source> emptySource) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.emptySource = emptySource;
            this.supportsOverrideDefaultParser = probeOverrideDefaultParser(delegate);
            this.floor = new FallbackIgnoreURIResolver(null, emptySource, this::overrideDefaultParser);
            // Compile-time block for xsl:import/xsl:include and document(); a caller-set resolver is routed through the floor rather than replacing it.
            delegate.setURIResolver(floor);
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Source getAssociatedStylesheet(final Source source, final String media, final String title, final String charset)
                throws TransformerConfigurationException {
            // Xalan's getAssociatedStylesheet drops a SAXSource's reader and self-provisions its own to scan for xml-stylesheet PIs (XALANJ-2849).
            final Source hardened = isXalan(delegate) ? hardenSourceToDom(source) : HardeningSAXParserFactory.harden(source, overrideDefaultParser());
            return delegate.getAssociatedStylesheet(hardened, media, title, charset);
        }

        @Override
        public Object getAttribute(final String name) {
            return delegate.getAttribute(name);
        }

        @Override
        public ErrorListener getErrorListener() {
            return delegate.getErrorListener();
        }

        @Override
        public boolean getFeature(final String name) {
            return delegate.getFeature(name);
        }

        @Override
        public URIResolver getURIResolver() {
            return floor.getDelegate();
        }

        private TransformerHandler hardenHandler(final TransformerHandler handler) {
            return handler == null ? null : new HardeningTransformerHandler(handler, getURIResolver(), emptySource, overrideDefaultParser());
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Templates newTemplates(final Source source) throws TransformerConfigurationException {
            final Templates templates = delegate.newTemplates(HardeningSAXParserFactory.harden(source, overrideDefaultParser()));
            return templates == null ? null : new HardeningTemplates(templates, getURIResolver(), emptySource, overrideDefaultParser());
        }

        @Override
        public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
            final TemplatesHandler handler = delegate.newTemplatesHandler();
            return handler == null ? null : new HardeningTemplatesHandler(handler, getURIResolver(), emptySource, overrideDefaultParser());
        }

        @Override
        public Transformer newTransformer() throws TransformerConfigurationException {
            // Identity transformer: still parses runtime sources, so wrap it to harden Transformer.transform(Source, Result).
            final Transformer transformer = delegate.newTransformer();
            return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver(), emptySource, overrideDefaultParser());
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Transformer newTransformer(final Source source) throws TransformerConfigurationException {
            final Transformer transformer = delegate.newTransformer(HardeningSAXParserFactory.harden(source, overrideDefaultParser()));
            return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver(), emptySource, overrideDefaultParser());
        }

        @Override
        public TransformerHandler newTransformerHandler() throws TransformerConfigurationException {
            return hardenHandler(delegate.newTransformerHandler());
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public TransformerHandler newTransformerHandler(final Source source) throws TransformerConfigurationException {
            return hardenHandler(delegate.newTransformerHandler(HardeningSAXParserFactory.harden(source, overrideDefaultParser())));
        }

        @Override
        public TransformerHandler newTransformerHandler(final Templates templates) throws TransformerConfigurationException {
            // Implementations cast templates.newTransformer() to their own Transformer type, so hand them the wrapped implementation Templates, not the wrapper.
            return hardenHandler(delegate.newTransformerHandler(unwrap(templates)));
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public XMLFilter newXMLFilter(final Source source) throws TransformerConfigurationException {
            final Templates templates = newTemplates(source);
            return templates == null ? null : new HardeningXMLFilter((HardeningTemplates) templates);
        }

        @Override
        public XMLFilter newXMLFilter(final Templates templates) throws TransformerConfigurationException {
            return new HardeningXMLFilter(templates instanceof HardeningTemplates ? (HardeningTemplates) templates
                    : new HardeningTemplates(templates, getURIResolver(), emptySource, overrideDefaultParser()));
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            delegate.setAttribute(name, value);
        }

        @Override
        public void setErrorListener(final ErrorListener listener) {
            delegate.setErrorListener(listener);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws TransformerConfigurationException {
            delegate.setFeature(name, value);
        }


        @Override
        public void setURIResolver(final URIResolver resolver) {
            floor.setDelegate(resolver);
        }

        /**
         * Checks whether parsers should be instantiated via {@code newInstance()} instead of {@code newDefaultInstance()}.
         *
         * <p>The JDK implementation of {@link TransformerFactory} uses the JDK parsers while {@value HardeningSAXParserFactory#OVERRIDE_DEFAULT_PARSER} is unset
         * or {@code false}.</p>
         *
         * @return {@code true} if parsers should be created via {@code newInstance()}.
         */
        private boolean overrideDefaultParser() {
            return !supportsOverrideDefaultParser || delegate.getFeature(HardeningSAXParserFactory.OVERRIDE_DEFAULT_PARSER);
        }
    }
}
