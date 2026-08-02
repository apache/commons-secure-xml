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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

/**
 * {@link URIResolver} floor: consults an optional caller-supplied resolver and ignores (resolves to empty) whatever the caller does not resolve.
 *
 * <p>The XSLT counterpart of {@link FallbackIgnoreEntityResolver2}, guarding {@code xsl:import}/{@code xsl:include} at compile time and {@code document()} at
 * transform time. The hardened {@link javax.xml.transform.TransformerFactory} and {@link javax.xml.transform.Transformer} wrappers install one of these and
 * route a caller-set resolver through {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific URI in by returning a
 * non-{@code null} {@link Source}; anything left unresolved resolves to an empty {@link Source}, so the external resource is neither fetched nor leaked.</p>
 */
final class FallbackIgnoreURIResolver implements URIResolver {

    /**
     * Shared backing for the ignore outcome. Consumers parse the resolved {@link Source}, and an empty character stream is not a well-formed XML document
     * (XSLTC rejects it for {@code document()} and for an ignored {@code xsl:include}/{@code xsl:import}), so the floor answers with a well-formed empty
     * document that evaluates to no content. It is never mutated, so one instance serves every resolution.
     */
    private static final Document EMPTY_DOCUMENT = newEmptyDocument();

    private static Document newEmptyDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (final ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private URIResolver delegate;

    FallbackIgnoreURIResolver(final URIResolver delegate) {
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
        // A fresh DOMSource per call keeps callers from mutating a shared Source.
        return resolved != null ? resolved : new DOMSource(EMPTY_DOCUMENT);
    }
}
