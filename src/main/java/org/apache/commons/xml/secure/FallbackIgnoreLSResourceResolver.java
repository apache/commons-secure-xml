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

import java.io.StringReader;

import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * {@link LSResourceResolver} floor: consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 * <p>
 * The schema-compile counterpart of {@link FallbackIgnoreEntityResolver2}. The secure {@link javax.xml.validation.SchemaFactory},
 * {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} wrappers install one of these and route a caller-set resolver
 * through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific resource in by returning a non-{@code null} {@link LSInput};
 * anything left unresolved resolves to an empty {@link LSInput}, so the external resource is neither fetched nor leaked.
 * </p>
 */
final class FallbackIgnoreLSResourceResolver implements LSResourceResolver {

    /** DOM Level 3 Load/Save implementation used to build the empty input for unresolved lookups. */
    private static final DOMImplementationLS DOM_LS;

    static {
        try {
            DOM_LS = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private LSResourceResolver delegate;

    /**
     * Constructs a new resolver that consults the given delegate and ignores whatever it does not resolve.
     *
     * @param delegate optional caller-supplied resolver to consult first; may be {@code null}.
     */
    FallbackIgnoreLSResourceResolver(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }

    /**
     * Gets the delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     *
     * @return The delegate provided by the constructor or set by {@link #setDelegate}, may be {@code null}.
     */
    LSResourceResolver getDelegate() {
        return delegate;
    }

    @Override
    public LSInput resolveResource(final String type, final String namespaceURI, final String publicId, final String systemId, final String baseURI) {
        final LSInput resolved = delegate != null ? delegate.resolveResource(type, namespaceURI, publicId, systemId, baseURI) : null;
        if (resolved != null) {
            return resolved;
        }
        if (SecureException.throwOnUnresolved()) {
            // The interface declares no checked exception; LSException is the DOM Load/Save runtime failure type.
            throw new LSException(LSException.PARSE_ERR, SecureException.forbidden(type, namespaceURI, publicId, systemId, baseURI));
        }
        // A character stream, not setStringData(""): the JDK's DOMEntityResolverWrapper discards empty string data, leaving a source with no content and a
        // null system id that Xerces then fails to absolutize. The echoed identifiers give Xerces a valid base URI; the content still comes from this
        // empty stream, so nothing is fetched.
        final LSInput empty = DOM_LS.createLSInput();
        empty.setCharacterStream(new StringReader(""));
        empty.setPublicId(publicId);
        empty.setSystemId(systemId);
        empty.setBaseURI(baseURI);
        return empty;
    }

    /**
     * Sets the delegate to consult first, replacing any previous delegate. A {@code null} value removes the delegate and leaves a pure ignore-all floor.
     *
     * @param delegate The delegate to consult first, or {@code null} for a pure ignore-all floor.
     */
    void setDelegate(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }
}
