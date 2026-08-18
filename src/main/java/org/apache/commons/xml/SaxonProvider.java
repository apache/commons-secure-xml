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

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.CollectionFn;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.ChainedResourceResolver;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.EmptySource;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;

/**
 * Hardening recipes for Saxon-HE ({@code net.sf.saxon:Saxon-HE}).
 *
 * <p>Saxon supplies {@link TransformerFactory} and {@link XPathFactory} implementations; it does not ship a DOM, SAX, StAX or Schema factory of its own.</p>
 */
final class SaxonProvider {

    /**
     * A Saxon {@link Configuration} that locks down every channel through which Saxon would otherwise reach external resources.
     *
     * <p>Three layers of restriction are applied:</p>
     *
     * <ol>
     *   <li><b>SAX layer.</b> {@link #makeParser} hands every {@link XMLReader} Saxon would otherwise use through
     *   {@link XmlFactories#harden(XMLReader)}, which routes it to the matching bundled hardening recipe. External DTDs, entities and XInclude
     *   resolve to empty content at parse time.</li>
     *   <li><b>Resource-resolution layer.</b> A non-removable ignore-all {@link ResourceResolver} floor backs every resolution chain ({@code xsl:include},
     *   {@code xsl:import}, {@code xsl:source-document}, and the XPath/XSLT functions {@code fn:doc}, {@code fn:document}, {@code fn:unparsed-text},
     *   {@code fn:json-doc} and {@code fn:transform}) ahead of Saxon's direct-fetch fallback, resolving whatever a caller-set resolver leaves unresolved to
     *   empty content. {@code fn:collection} bypasses the resource resolver and fetches directly, so an empty {@link CollectionFinder} supplies its ignore
     *   outcome instead.</li>
     *   <li><b>Extension-function layer.</b> {@link Feature#ALLOW_EXTERNAL_FUNCTIONS} is disabled, so reflection-based extension calls cannot be used to
     *       sidestep the URI restrictions.</li>
     * </ol>
     */
    private static final class HardenedConfiguration extends Configuration {

        /**
         * Ignore-all resource-resolution floor: resolves whatever the resolvers ahead of it leave unresolved to empty content, so the external resource is
         * neither fetched nor leaked.
         *
         * <p>Nature-aware, because Saxon's consumers accept different shapes of "empty".</p>
         */
        private static final ResourceResolver IGNORE_ALL_FLOOR = request -> {
            if (ResourceRequest.EXTERNAL_ENTITY_NATURE.equals(request.nature) || ResourceRequest.DTD_NATURE.equals(request.nature)) {
                // Fall through to the parser's own EntityResolver, which Saxon chains behind this resolver:
                // on a hardened reader that is the FallbackIgnoreEntityResolver2 floor, which also implements the throw-on-unresolved toggle.
                return null;
            }
            if (HardeningException.throwOnUnresolved()) {
                throw new XPathException(HardeningException.forbidden(request.nature, null, request.publicId, request.uri, request.baseUri));
            }
            if (ResourceRequest.XML_NATURE.equals(request.nature) || ResourceRequest.XSLT_NATURE.equals(request.nature)
                    || ResourceRequest.XSD_NATURE.equals(request.nature)) {
                // EmptySource makes xsl:include/xsl:import substitute an empty stylesheet module and doc()/document() return the empty sequence.
                return EmptySource.getInstance();
            }
            // Text and binary consumers need actual empty content: unparsed-text() yields the empty string.
            return new StreamSource(new StringReader(""));
        };

        /** Collection-level ignore: {@code fn:collection()} and {@code fn:uri-collection()} resolve to an empty collection instead of fetching. */
        private static final CollectionFinder EMPTY_COLLECTION_FINDER = (context, collectionURI) -> {
            if (HardeningException.throwOnUnresolved()) {
                throw new XPathException(HardeningException.forbidden("collection", null, null, collectionURI, null));
            }
            return CollectionFn.EMPTY_COLLECTION;
        };

        private HardenedConfiguration() {
            // Extension-function layer: turn off Saxon's reflection-based extension calls. Without this an attacker could bypass URI restrictions through
            // user-supplied Java extensions.
            setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
            // Resource-resolution layer: the floor backs every resolution chain ahead of Saxon's direct-fetch fallback.
            // The setResourceResolver override below keeps it non-removable.
            setResourceResolver(null);
            //  fn:collection bypasses the resolver, closed by the empty collection finder.
            setCollectionFinder(EMPTY_COLLECTION_FINDER);
            // Use the parser below for both style and source:
            setStyleParserClass("#DEFAULT");
            setSourceParserClass("#DEFAULT");
        }

        /**
         * Keeps the floor underneath any resolver installed later.
         *
         * <p>The plain JAXP routes ({@code TransformerFactory.setURIResolver}, {@code setAttribute} with Saxon's resolver-valued keys) replace the
         * Configuration resolver wholesale rather than chaining to it, so the incoming resolver is re-wrapped with the floor as its fallback.</p>
         */
        @Override
        public void setResourceResolver(final ResourceResolver resolver) {
            super.setResourceResolver(resolver == null ? IGNORE_ALL_FLOOR : new ChainedResourceResolver(resolver, IGNORE_ALL_FLOOR));
        }

        /**
         * Saxon's hook for instantiating a new SAX parser.
         */
        @Override
        public XMLReader makeParser(final String className) throws TransformerFactoryConfigurationError {
            try {
                return SAXParserHardener.hardenReader(super.makeParser(className));
            } catch (final HardeningException e) {
                throw new TransformerFactoryConfigurationError(e);
            }
        }
    }

    private static final class SaxonProviderConfigurer {

        private static TransformerFactory configure(final TransformerFactory factory) {
            ((SaxonTransformerFactory) factory).setConfiguration(new HardenedConfiguration());
            return factory;
        }

        private static XPathFactory configure(final XPathFactory factory) {
            ((XPathFactoryImpl) factory).setConfiguration(new HardenedConfiguration());
            return factory;
        }
    }

    static TransformerFactory configure(final TransformerFactory factory) {
        try {
            return SaxonProviderConfigurer.configure(factory);
        } catch (final LinkageError e) {
            // Unlikely, but protects method execution from missing optional dependency
            throw new IllegalStateException(e);
        }
    }

    static XPathFactory configure(final XPathFactory factory) {
        try {
            return SaxonProviderConfigurer.configure(factory);
        } catch (final LinkageError e) {
            // Unlikely, but protects method execution from missing optional dependency
            throw new IllegalStateException(e);
        }
    }

    private SaxonProvider() {
    }
}
