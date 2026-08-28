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
 * Not a {@link TransformerFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultInstance()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningTransformerFactory {

    /**
     * Returns a new, hardened {@link TransformerFactory}.
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
     *
     * @return A hardened factory.
     * @throws IllegalStateException if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static TransformerFactory newInstance() {
        return TransformerHardener.harden(TransformerFactory.newInstance());
    }

    /**
     * Wraps a prepared delegate in the hardening wrapper; called by the hardener once the required settings are applied.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @return The hardened factory.
     */
    static TransformerFactory wrap(final SAXTransformerFactory delegate) {
        return new Wrapper(delegate);
    }

    /**
     * Wraps a prepared delegate in the hardening wrapper; called by the hardener once the required settings are applied.
     *
     * @param delegate    the delegate to wrap; must not be {@code null}.
     * @param emptySource supplies the empty document a denied fetch resolves to.
     * @return The hardened factory.
     */
    static TransformerFactory wrap(final SAXTransformerFactory delegate, final Supplier<Source> emptySource) {
        return new Wrapper(delegate, emptySource);
    }

    private HardeningTransformerFactory() {
        // static only
    }

    /**
     * {@link javax.xml.transform.TransformerFactory} wrapper that rewrites every Source-taking entry point through {@link SAXParserHardener#hardenSource(Source)} before
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
         * {@link SAXParserHardener#hardenSource(Source)}.
         *
         * @param source The source to scan for an associated stylesheet.
         * @return A {@link DOMSource} for a reader-less source, otherwise the result of {@link SAXParserHardener#hardenSource(Source)}.
         * @throws TransformerConfigurationException if the source cannot be parsed.
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         * @throws HardeningException Thrown if a (non-Andoid) factory cannot support the secure processing feature {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
         */
        private static Source hardenSourceToDom(final Source source) throws TransformerConfigurationException {
            if (source instanceof StreamSource || source instanceof SAXSource && ((SAXSource) source).getXMLReader() == null) {
                final InputSource inputSource = SAXSource.sourceToInputSource(source);
                if (inputSource != null) {
                    try {
                        final DocumentBuilderFactory factory = DocumentBuilderHardener.harden(DocumentBuilderFactory.newInstance());
                        factory.setNamespaceAware(true);
                        final Document document = factory.newDocumentBuilder().parse(inputSource);
                        return new DOMSource(document, inputSource.getSystemId());
                    } catch (final ParserConfigurationException | SAXException | IOException e) {
                        throw new TransformerConfigurationException("Failed to parse the source for associated-stylesheet lookup", e);
                    }
                }
            }
            return SAXParserHardener.hardenSource(source);
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

        private static Templates unwrap(final Templates templates) {
            return templates instanceof HardeningTemplates ? ((HardeningTemplates) templates).getDelegate() : templates;
        }

        private final SAXTransformerFactory delegate;

        /**
         * Empty-{@link Source} supplier for the resolver floor, threaded onto every produced Templates/Transformer; {@code null} means the default empty DOM.
         */
        private final Supplier<Source> emptySource;

        private final FallbackIgnoreURIResolver floor;

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
            this.floor = new FallbackIgnoreURIResolver(null, emptySource);
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
            final Source hardened = isXalan(delegate) ? hardenSourceToDom(source) : SAXParserHardener.hardenSource(source);
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
            return handler == null ? null : new HardeningTransformerHandler(handler, getURIResolver(), emptySource);
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Templates newTemplates(final Source source) throws TransformerConfigurationException {
            final Templates templates = delegate.newTemplates(SAXParserHardener.hardenSource(source));
            return templates == null ? null : new HardeningTemplates(templates, getURIResolver(), emptySource);
        }

        @Override
        public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
            final TemplatesHandler handler = delegate.newTemplatesHandler();
            return handler == null ? null : new HardeningTemplatesHandler(handler, getURIResolver(), emptySource);
        }

        @Override
        public Transformer newTransformer() throws TransformerConfigurationException {
            // Identity transformer: still parses runtime sources, so wrap it to harden Transformer.transform(Source, Result).
            final Transformer transformer = delegate.newTransformer();
            return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver(), emptySource);
        }

        /**
         * {@inheritDoc}
         *
         * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
         *                                   configuration error} or if the implementation is not available or cannot be instantiated.
         */
        @Override
        public Transformer newTransformer(final Source source) throws TransformerConfigurationException {
            final Transformer transformer = delegate.newTransformer(SAXParserHardener.hardenSource(source));
            return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver(), emptySource);
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
            return hardenHandler(delegate.newTransformerHandler(SAXParserHardener.hardenSource(source)));
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
                    : new HardeningTemplates(templates, getURIResolver(), emptySource));
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
    }
}
