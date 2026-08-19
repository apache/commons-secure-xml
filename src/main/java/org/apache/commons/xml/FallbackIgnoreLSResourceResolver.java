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

import java.io.StringReader;

import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * {@link LSResourceResolver} floor: consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 *
 * <p>The schema-compile counterpart of {@link FallbackIgnoreEntityResolver2}. The hardened {@link javax.xml.validation.SchemaFactory}, {@link
 * javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} wrappers install one of these and route a caller-set resolver through
 * {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific resource in by returning a non-{@code null} {@link LSInput} that
 * carries content (a character stream, byte stream, or string data); anything left unresolved resolves to an empty {@link LSInput}, so the external resource
 * is neither fetched nor leaked.</p>
 *
 * <p>An {@link LSInput} naming only identifiers is treated as unresolved, not as an opt-in: handing it to the implementation would trigger default
 * resolution, where the implementation fetches the system id itself and parses it with an internal parser at its own defaults. A caller who wants the
 * resource available must supply its content on the {@link LSInput}.</p>
 */
final class FallbackIgnoreLSResourceResolver implements LSResourceResolver {

    /** DOM Level 3 Load/Save implementation used to build the empty input for unresolved lookups. */
    private static final DOMImplementationLS DOM_LS = domImplementationLS();

    private LSResourceResolver delegate;

    FallbackIgnoreLSResourceResolver(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }

    private static DOMImplementationLS domImplementationLS() {
        try {
            return (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new HardeningException("No DOM Level 3 Load/Save implementation available to build the empty schema input", e);
        }
    }

    void setDelegate(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }

    LSResourceResolver getDelegate() {
        return delegate;
    }

    /**
     * Tells whether the input carries content, so the consumer never falls back to resolving its identifiers itself.
     *
     * @param input The input the caller's resolver returned.
     * @return Whether a character stream, byte stream, or non-empty string data is present.
     */
    private static boolean hasContent(final LSInput input) {
        // Empty string data counts as no content: the JDK's DOMEntityResolverWrapper discards it (see the unresolved branch below).
        return input.getCharacterStream() != null || input.getByteStream() != null || input.getStringData() != null && !input.getStringData().isEmpty();
    }

    @Override
    public LSInput resolveResource(final String type, final String namespaceURI, final String publicId, final String systemId, final String baseURI) {
        final LSInput resolved = delegate != null ? delegate.resolveResource(type, namespaceURI, publicId, systemId, baseURI) : null;
        if (resolved != null && hasContent(resolved)) {
            return resolved;
        }
        if (HardeningException.throwOnUnresolved()) {
            // The interface declares no checked exception; LSException is the DOM Load/Save runtime failure type.
            throw new LSException(LSException.PARSE_ERR, HardeningException.forbidden(type, namespaceURI, publicId, systemId, baseURI));
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
}
