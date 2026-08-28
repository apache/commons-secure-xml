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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

/**
 * {@link URIResolver} floor: consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 * <p>
 * The XSLT counterpart of {@link FallbackIgnoreEntityResolver2}, guarding {@code xsl:import}/{@code xsl:include} at compile time and {@code document()} at
 * transform time. The hardened {@link javax.xml.transform.TransformerFactory} and {@link javax.xml.transform.Transformer} wrappers install one of these and
 * route a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific URI in by returning a
 * non-{@code null} {@link Source}; anything left unresolved resolves to an empty {@link Source}, so the external resource is neither fetched nor leaked.
 * </p>
 * <p>
 * The shape of that empty {@link Source} is supplied by the caller: the default is a well-formed empty DOM document (which every stock TrAX consumer accepts),
 * while the Saxon path supplies {@code EmptySource.getInstance()} so its consumers get the "empty" shape they expect.
 * </p>
 * <p>
 * An opted-in {@link javax.xml.transform.stream.StreamSource} or reader-less {@link javax.xml.transform.sax.SAXSource} is rewritten to carry a hardened reader
 * before it is returned, so the implementation parses the opted-in content on the same floor instead of with an internal reader at its own defaults. A
 * {@link javax.xml.transform.dom.DOMSource} or a {@link javax.xml.transform.sax.SAXSource} carrying the caller's own reader is returned as-is.
 * </p>
 */
final class FallbackIgnoreURIResolver implements URIResolver {

    /**
     * Backing for the default ignore outcome. Consumers parse the resolved {@link Source}, and an empty character stream is not a well-formed XML document
     * (XSLTC rejects it for {@code document()} and for an ignored {@code xsl:include}/{@code xsl:import}), so the default supplier answers with a well-formed
     * empty document that evaluates to no content. It is never mutated, so one instance serves every resolution.
     *
     * @see #newEmptyDocument()
     */
    private static final Document EMPTY_DOCUMENT;

    static {
        EMPTY_DOCUMENT = newEmptyDocument();
    }

    /**
     * Creates a new empty document.
     *
     * @return a new empty document.
     * @throws SecureException        Thrown if a {@link DocumentBuilder} cannot be created which satisfies the configuration requested.
     * @throws ExceptionInInitializerError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    private static Document newEmptyDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private URIResolver delegate;

    /**
     * Produces the empty {@link Source} returned for an unresolved reference; a new value per call keeps callers from mutating a shared Source.
     */
    private final Supplier<Source> emptySource;

    /**
     * Whether the opted-in rewrite should use the pluggable parser lookup instead of the platform's built-in parser; read per resolution so the factory-level floor tracks a later
     * {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} toggle.
     */
    private final BooleanSupplier overrideDefaultParser;

    /**
     * Constructs a new resolver.
     *
     * @param delegate         the resolver to delegate resolution to; may be {@code null}.
     * @param emptySource      the empty-{@link Source} supplier for the ignore outcome, or {@code null} for the default empty DOM document.
     * @param overrideDefaultParser whether the opted-in rewrite should use the pluggable parser lookup instead of the platform's built-in parser, read at each resolution.
     */
    FallbackIgnoreURIResolver(final URIResolver delegate, final Supplier<Source> emptySource, final BooleanSupplier overrideDefaultParser) {
        this.delegate = delegate;
        this.emptySource = emptySource != null ? emptySource : () -> new DOMSource(EMPTY_DOCUMENT);
        this.overrideDefaultParser = overrideDefaultParser;
    }

    /**
     * Gets the delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     *
     * @return The delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     */
    URIResolver getDelegate() {
        return delegate;
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public Source resolve(final String href, final String base) throws TransformerException {
        final Source resolved = delegate != null ? delegate.resolve(href, base) : null;
        if (resolved != null) {
            // The implementation parses the opted-in handle with an internal reader at its own defaults; the rewrite hands it a hardened reader instead.
            return SecureSAXParserFactory.secure(resolved, overrideDefaultParser.getAsBoolean());
        }
        if (SecureException.throwOnUnresolved()) {
            throw new TransformerException(SecureException.forbidden("uri", null, null, href, base));
        }
        return emptySource.get();
    }

    /**
     * Sets the delegate to consult first, replacing any previous delegate. A {@code null} value removes the delegate and leaves a pure ignore-all floor.
     *
     * @param delegate The delegate to consult first, or {@code null} for a pure ignore-all floor.
     */
    void setDelegate(final URIResolver delegate) {
        this.delegate = delegate;
    }
}
