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
import java.net.URI;
import java.net.URISyntaxException;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.EntityResolver2;

/**
 * Entity resolver that consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 * <p>
 * The canonical hardening floor, and the entity-resolution counterpart of the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties. Every floor
 * ({@link FallbackIgnoreLSResourceResolver}, {@link FallbackIgnoreURIResolver} and {@link FallbackIgnoreXMLResolver}) shares two defining properties:
 * </p>
 * <ol>
 * <li><strong>Non-removable, and it wraps the resolver the caller sets.</strong> The hardened wrappers install one and route a caller-set resolver through
 * {@code setDelegate} rather than letting it replace the floor, so the caller's resolver is consulted first but cannot remove the floor underneath it.</li>
 * <li><strong>It supplies the default action for a lookup the caller's resolver does not resolve</strong> (a {@code null} return, or no caller resolver at
 * all). This is where a floor departs from stock JAXP: normally an unresolved lookup falls back to the processor's built-in resolution and the resource is
 * <em>fetched</em>; a floor instead resolves it to <em>empty</em> content, so the parse continues without the external fetch and without a leak.</li>
 * </ol>
 * <p>
 * The hardened DOM and SAX wrappers install one of these and, when the caller sets their own {@link EntityResolver}, route it through {@link #setDelegate}
 * rather than letting it replace the floor. A caller therefore opts a specific resource in by returning a non-{@code null} {@link InputSource} from their
 * resolver; anything they leave unresolved (a {@code null} return, or no caller resolver at all) goes to {@link #onUnresolved}, which returns empty content by
 * default.
 * </p>
 * <p>
 * It extends {@link DefaultHandler2} so it is also usable as a {@link org.xml.sax.ext.LexicalHandler}; {@link #getExternalSubset} therefore inherits the
 * {@code DefaultHandler2} "no synthetic subset" default. Only {@link #resolveEntity(String, String, String, String) resolveEntity} (the actual external fetch)
 * reaches the ignore fallback.
 * </p>
 */
class FallbackIgnoreEntityResolver2 extends DefaultHandler2 {

    private static final byte[] EMPTY = new byte[0];

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

    /**
     * Caller-supplied resolver consulted first, or {@code null} for a pure ignore-all floor.
     */
    private EntityResolver delegate;

    /**
     * Constructs a new ignore-all floor with an optional caller-supplied resolver.
     *
     * @param delegate The caller-supplied resolver, or {@code null} for a pure ignore-all floor.
     */
    FallbackIgnoreEntityResolver2(final EntityResolver delegate) {
        this.delegate = delegate;
    }

    /**
     * Gets the delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     *
     * @return The delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     */
    final EntityResolver getDelegate() {
        return delegate;
    }

    /**
     * Outcome when neither the caller delegate nor this resolver provides the entity. Resolves to empty content by default, so the external resource is neither
     * fetched nor leaked and the parse continues with no replacement text. The returned source echoes the requested identifiers (with {@code systemId}
     * absolutized): the parser reads the empty byte stream, but Xerces still derives the entity's base URI from the system id and fails on a {@code null} one.
     *
     * @param name     The entity name, or {@code null} on the 2-arg resolution path.
     * @param publicId The public identifier, or {@code null} if none.
     * @param baseURI  The base URI for relative resolution, or {@code null}.
     * @param systemId The system identifier of the unresolved entity.
     * @return An empty {@link InputSource} carrying the requested identifiers.
     * @throws SAXException when {@value SecureException#THROW_ON_UNRESOLVED} is set: unresolved references are rejected instead of resolved to empty.
     * @throws IOException  never by the default implementation.
     */
    protected InputSource onUnresolved(final String name, final String publicId, final String baseURI, final String systemId) throws SAXException, IOException {
        if (SecureException.throwOnUnresolved()) {
            throw new SAXException(SecureException.forbidden(name, null, publicId, systemId, baseURI));
        }
        final InputSource empty = new InputSource(new ByteArrayInputStream(EMPTY));
        empty.setPublicId(publicId);
        empty.setSystemId(absolutize(baseURI, systemId));
        return empty;
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

    private InputSource resolveWithDelegate(final String name, final String publicId, final String baseURI, final String systemId)
            throws SAXException, IOException {
        if (delegate != null) {
            return delegate instanceof EntityResolver2 ? ((EntityResolver2) delegate).resolveEntity(name, publicId, baseURI, systemId) :
            // We need to resolve the systemId against baseURI, because a plain EntityResolver expects an absolute URI.
                    delegate.resolveEntity(publicId, absolutize(baseURI, systemId));
        }
        return null;
    }

    /**
     * Replaces the caller resolver consulted ahead of the floor; lets a single floor instance back successive {@code setEntityResolver} calls.
     *
     * @param delegate The caller-supplied resolver, or {@code null} for a pure ignore-all floor.
     */
    final void setDelegate(final EntityResolver delegate) {
        this.delegate = delegate;
    }
}
