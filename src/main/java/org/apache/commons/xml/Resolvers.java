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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.EntityResolver2;

/**
 * Policy resolvers that fix the outcome of every external lookup. Each member is a floor with two defining properties:
 *
 * <ol>
 * <li><strong>Non-removable, and it wraps the resolver the caller sets.</strong> The hardened wrappers install one and route a caller-set resolver through
 * {@code setDelegate} rather than letting it replace the floor, so the caller's resolver is consulted first but cannot remove the floor underneath it.</li>
 * <li><strong>It supplies the default action for a lookup the caller's resolver does not resolve</strong> (a {@code null} return, or no caller resolver at
 * all). This is where a floor departs from stock JAXP: normally an unresolved lookup falls back to the processor's built-in resolution and the resource is
 * <em>fetched</em>; a floor instead <em>denies</em> it (throws).</li>
 * </ol>
 *
 * <p>The deny members are {@link FallbackDenyResolver} (for {@code EntityResolver}), {@link FallbackDenyLSResourceResolver} (for {@link LSResourceResolver}),
 * {@link FallbackDenyURIResolver} (for {@link URIResolver}) and {@link FallbackDenyXMLResolver} (for {@link XMLResolver}). {@link FallbackIgnoreXMLResolver} is a
 * variant whose default action returns an empty input instead of throwing, for the Woodstox DTD-subset and undeclared-entity hooks where a missing resource must
 * be skipped rather than denied.</p>
 */
final class Resolvers {

    /**
     * Entity resolver that consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
     *
     * <p>This is the entity-resolution counterpart of the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties: a non-overridable floor. The hardened DOM and SAX
     * wrappers install one of these and, when the caller sets their own {@link EntityResolver}, route it through {@link #setDelegate} rather than letting it
     * replace the floor. A caller therefore opts a specific resource in by returning a non-{@code null} {@link InputSource} from their resolver; anything they
     * leave unresolved (a {@code null} return, or no caller resolver at all) goes to {@link #onUnresolved}, which denies by default.</p>
     *
     * <p>It extends {@link DefaultHandler2} so it is also usable as a {@link org.xml.sax.ext.LexicalHandler} (see {@code SAXParserHardener}'s Android subclass,
     * which needs {@code startDTD}/{@code endDTD}); {@link #getExternalSubset} therefore inherits the {@code DefaultHandler2} "no synthetic subset" default. Only
     * {@link #resolveEntity(String, String, String, String) resolveEntity} (the actual external fetch) reaches the deny fallback.</p>
     */
    static class FallbackDenyResolver extends DefaultHandler2 {

        /**
         * Caller-supplied resolver consulted first, or {@code null} for a pure deny-all floor.
         */
        private EntityResolver delegate;

        FallbackDenyResolver(final EntityResolver delegate) {
            this.delegate = delegate;
        }

        /**
         * Replaces the caller resolver consulted ahead of the floor; lets a single floor instance back successive {@code setEntityResolver} calls.
         *
         * @param delegate The caller-supplied resolver, or {@code null} for a pure deny-all floor.
         */
        final void setDelegate(final EntityResolver delegate) {
            this.delegate = delegate;
        }

        final EntityResolver getDelegate() {
            return delegate;
        }

        @Override
        public final InputSource resolveEntity(final String publicId, final String systemId) throws SAXException, IOException {
            return resolveEntity(null, publicId, null, systemId);
        }

        @Override
        public final InputSource resolveEntity(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            final InputSource resolved = resolveWithDelegate(name, publicId, baseURI, systemId);
            return resolved != null ? resolved : onUnresolved(name, publicId, baseURI, systemId);
        }

        /**
         * Outcome when neither the caller delegate nor this resolver provides the entity. Denies by default; a subclass may permit specific lookups (e.g. the
         * external DTD subset) by returning {@code null} or an {@link InputSource} instead of calling {@code super}.
         *
         * @param name     The entity name, or {@code null} on the 2-arg resolution path.
         * @param publicId The public identifier, or {@code null} if none.
         * @param baseURI  The base URI for relative resolution, or {@code null}.
         * @param systemId The system identifier of the unresolved entity.
         * @return An {@link InputSource} to permit the lookup, or {@code null} to skip it silently; the default implementation never returns normally.
         * @throws SAXException to deny the lookup (the default behavior).
         * @throws IOException  if a subclass opens a stream that fails.
         */
        protected InputSource onUnresolved(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            throw new SAXException(forbiddenMessage(name, null, publicId, systemId, baseURI));
        }

        private InputSource resolveWithDelegate(final String name, final String publicId, final String baseURI,
                final String systemId) throws SAXException, IOException {
            if (delegate != null) {
                return delegate instanceof EntityResolver2 ? ((EntityResolver2) delegate).resolveEntity(name, publicId, baseURI, systemId) :
                        // We need to resolve the systemId against baseURI, because a plain EntityResolver expects an absolute URI.
                        delegate.resolveEntity(publicId, absolutize(baseURI, systemId));
            }
            return null;
        }
    }

    /**
     * {@link LSResourceResolver} floor: consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
     *
     * <p>The schema-compile counterpart of {@link FallbackDenyResolver}. The hardened {@link javax.xml.validation.SchemaFactory}, {@link
     * javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} wrappers install one of these and route a caller-set resolver through
     * {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific resource in by returning a non-{@code null} {@link LSInput};
     * anything left unresolved is denied.</p>
     */
    static final class FallbackDenyLSResourceResolver implements LSResourceResolver {

        private LSResourceResolver delegate;

        FallbackDenyLSResourceResolver(final LSResourceResolver delegate) {
            this.delegate = delegate;
        }

        void setDelegate(final LSResourceResolver delegate) {
            this.delegate = delegate;
        }

        LSResourceResolver getDelegate() {
            return delegate;
        }

        @Override
        public LSInput resolveResource(final String type, final String namespaceURI, final String publicId, final String systemId, final String baseURI) {
            final LSInput resolved = delegate != null ? delegate.resolveResource(type, namespaceURI, publicId, systemId, baseURI) : null;
            if (resolved != null) {
                return resolved;
            }
            throw new SecurityException(forbiddenMessage(type, namespaceURI, publicId, systemId, baseURI));
        }
    }

    /**
     * {@link URIResolver} floor: consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
     *
     * <p>The XSLT counterpart of {@link FallbackDenyResolver}, guarding {@code xsl:import}/{@code xsl:include} at compile time and {@code document()} at
     * transform time. The hardened {@link javax.xml.transform.TransformerFactory} and {@link javax.xml.transform.Transformer} wrappers install one of these and
     * route a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific URI in by returning a
     * non-{@code null} {@link Source}; anything left unresolved is denied.</p>
     */
    static final class FallbackDenyURIResolver implements URIResolver {

        private URIResolver delegate;

        FallbackDenyURIResolver(final URIResolver delegate) {
            this.delegate = delegate;
        }

        void setDelegate(final URIResolver delegate) {
            this.delegate = delegate;
        }

        URIResolver getDelegate() {
            return delegate;
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {
            final Source resolved = delegate != null ? delegate.resolve(href, base) : null;
            if (resolved != null) {
                return resolved;
            }
            throw new TransformerException(forbiddenMessage("uri", null, null, href, base));
        }
    }

    /**
     * {@link XMLResolver} floor: consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
     *
     * <p>The StAX counterpart of {@link FallbackDenyResolver}, installed on each entity-resolution hook. The hardened {@link javax.xml.stream.XMLInputFactory}
     * wrapper routes a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific entity in by returning
     * a non-{@code null} result; anything left unresolved goes to {@link #onUnresolved}, which denies by default. Subclasses override {@code onUnresolved} to give
     * a hook a different unresolved policy (e.g. return an empty input for the external DTD subset, or for undeclared entities) while keeping the caller-delegate
     * behavior.</p>
     */
    static class FallbackDenyXMLResolver implements XMLResolver {

        private XMLResolver delegate;

        FallbackDenyXMLResolver(final XMLResolver delegate) {
            this.delegate = delegate;
        }

        final void setDelegate(final XMLResolver delegate) {
            this.delegate = delegate;
        }

        final XMLResolver getDelegate() {
            return delegate;
        }

        @Override
        public final Object resolveEntity(final String publicID, final String systemID, final String baseURI, final String namespace) throws XMLStreamException {
            final Object resolved = delegate != null ? delegate.resolveEntity(publicID, systemID, baseURI, namespace) : null;
            return resolved != null ? resolved : onUnresolved(publicID, systemID, baseURI, namespace);
        }

        /**
         * Outcome when the caller delegate does not resolve the entity. Denies by default; a subclass may return an {@link java.io.InputStream}, {@link Source} or
         * other {@link XMLResolver}-supported value (for example an empty input) instead of calling {@code super}, or {@code throw}
         * {@link #denied(String, String, String, String)} to deny only some lookups.
         *
         * @param publicID The public identifier, or {@code null} if none.
         * @param systemID The system identifier of the unresolved entity.
         * @param baseURI  The base URI for relative resolution, or {@code null}.
         * @param namespace The namespace (or, for Woodstox, the entity name), or {@code null}.
         * @return The replacement input, or a value the caller's parser accepts; the default implementation never returns normally.
         * @throws XMLStreamException to deny the lookup (the default behavior).
         */
        protected Object onUnresolved(final String publicID, final String systemID, final String baseURI, final String namespace) throws XMLStreamException {
            throw denied(publicID, systemID, baseURI, namespace);
        }

        /**
         * Builds the standard "forbidden by hardening" exception for a denied lookup, so a subclass with a mixed policy can reuse the deny outcome for the
         * lookups it refuses.
         *
         * @param publicID The public identifier, or {@code null} if none.
         * @param systemID The system identifier of the unresolved entity.
         * @param baseURI  The base URI for relative resolution, or {@code null}.
         * @param namespace The namespace (or, for Woodstox, the entity name), or {@code null}.
         * @return The exception to throw.
         */
        protected final XMLStreamException denied(final String publicID, final String systemID, final String baseURI, final String namespace) {
            return new XMLStreamException(forbiddenMessage(null, namespace, publicID, systemID, baseURI));
        }
    }

    /**
     * {@link FallbackDenyXMLResolver} variant whose unresolved policy returns an empty input instead of throwing, so the parse continues with no replacement
     * content. Used on Woodstox's DTD-subset and undeclared-entity hooks (where a missing resource must be skipped, not denied), while still consulting an
     * optional caller-supplied resolver first.
     */
    static class FallbackIgnoreXMLResolver extends FallbackDenyXMLResolver {

        /**
         * Empty {@link ByteArrayInputStream} shared across every call. {@code read()} on a zero-length array always returns {@code -1}, so reusing the instance
         * is safe even though the type is technically stateful.
         */
        private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);

        FallbackIgnoreXMLResolver(final XMLResolver delegate) {
            super(delegate);
        }

        @Override
        protected Object onUnresolved(final String publicID, final String systemID, final String baseURI, final String namespace) throws XMLStreamException {
            return EMPTY;
        }
    }

    /**
     * Resolves {@code systemId} against {@code baseURI}.
     *
     * @param baseURI  The absolute base URI to resolve against, or {@code null} if none is available.
     * @param systemId The system identifier, possibly relative to {@code baseURI}.
     * @return The absolutized system identifier, or {@code systemId} unchanged when it cannot or need not be resolved.
     */
    private static String absolutize(final String baseURI, final String systemId) {
        if (systemId == null || baseURI == null) {
            return systemId;
        }
        try {
            final URI system = new URI(systemId);
            return system.isAbsolute() ? systemId : new URI(baseURI).resolve(system).toString();
        } catch (final URISyntaxException e) {
            return systemId;
        }
    }

    private static String forbiddenMessage(final String type, final String namespace, final String publicId, final String systemId, final String baseURI) {
        return String.format("External resource fetch forbidden by hardening: type=%s, namespace=%s, publicId=%s, systemId=%s, baseURI=%s", type, namespace,
                publicId, systemId, baseURI);
    }

    private Resolvers() {
    }
}
