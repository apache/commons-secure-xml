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

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;

/**
 * {@link XMLResolver} floor: consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
 *
 * <p>The StAX counterpart of {@link FallbackDenyEntityResolver2}, installed on each entity-resolution hook. The hardened {@link javax.xml.stream.XMLInputFactory}
 * wrapper routes a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific entity in by returning
 * a non-{@code null} result; anything left unresolved goes to {@link #onUnresolved}, which denies by default. Subclasses override {@code onUnresolved} to give
 * a hook a different unresolved policy (e.g. return an empty input for the external DTD subset, or for undeclared entities) while keeping the caller-delegate
 * behavior.</p>
 */
class FallbackDenyXMLResolver implements XMLResolver {

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
        return new XMLStreamException(HardeningException.forbidden(null, namespace, publicID, systemID, baseURI));
    }
}
