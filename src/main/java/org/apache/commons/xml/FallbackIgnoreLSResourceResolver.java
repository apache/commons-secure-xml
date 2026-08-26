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
 * {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific resource in by returning a non-{@code null} {@link LSInput};
 * anything left unresolved resolves to an empty {@link LSInput}, so the external resource is neither fetched nor leaked.</p>
 */
final class FallbackIgnoreLSResourceResolver implements LSResourceResolver {

    /** DOM Level 3 Load/Save implementation used to build the empty input for unresolved lookups. */
    private static final DOMImplementationLS DOM_LS = domImplementationLS();

    private static DOMImplementationLS domImplementationLS() {
        try {
            return (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
        } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new HardeningException("No DOM Level 3 Load/Save implementation available to build the empty schema input", e);
        }
    }

    private LSResourceResolver delegate;

    FallbackIgnoreLSResourceResolver(final LSResourceResolver delegate) {
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

    void setDelegate(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }
}
