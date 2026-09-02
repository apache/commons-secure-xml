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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

/**
 * {@link XMLResolver} floor: consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 * <p>
 * The StAX counterpart of {@link FallbackIgnoreEntityResolver2}, installed on each entity-resolution hook. The secure
 * {@link javax.xml.stream.XMLInputFactory} wrapper routes a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller
 * opts a specific entity in by returning a non-{@code null} result; anything left unresolved resolves to an empty input, so the external resource is neither
 * fetched nor leaked and the parse continues with no replacement content.
 * </p>
 */
final class FallbackIgnoreXMLResolver implements XMLResolver {

    /**
     * Empty {@link ByteArrayInputStream} shared across every call. {@code read()} on a zero-length array always returns {@code -1}, so reusing the instance is
     * safe even though the type is technically stateful.
     */
    private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);

    private XMLResolver delegate;

    /**
     * Constructs a new resolver that consults the given delegate and ignores whatever it does not resolve.
     *
     * @param delegate optional caller-supplied resolver to consult first; may be {@code null}.
     */
    FallbackIgnoreXMLResolver(final XMLResolver delegate) {
        this.delegate = delegate;
    }

    /**
     * Gets the delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     *
     * @return The delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     */
    XMLResolver getDelegate() {
        return delegate;
    }

    @Override
    public Object resolveEntity(final String publicID, final String systemID, final String baseURI, final String namespace) throws XMLStreamException {
        final Object resolved = delegate != null ? delegate.resolveEntity(publicID, systemID, baseURI, namespace) : null;
        if (resolved != null) {
            return resolved;
        }
        if (SecureException.throwOnUnresolved()) {
            throw new XMLStreamException(SecureException.forbidden(null, namespace, publicID, systemID, baseURI));
        }
        return EMPTY;
    }

    /**
     * Sets the delegate to consult first, replacing any previous delegate.
     * <p>
     * A null value removes the delegate and leaves a pure ignore-all floor.
     * </p>
     *
     * @param delegate The delegate to consult first, replacing any previous delegate.
     */
    void setDelegate(final XMLResolver delegate) {
        this.delegate = delegate;
    }
}
