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

import java.util.function.Supplier;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.CollectionFn;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.EmptySource;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ResourceResolverWrappingURIResolver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;

/**
 * Hardening recipes for Saxon-HE ({@code net.sf.saxon:Saxon-HE}).
 *
 * <p>Saxon supplies {@link TransformerFactory} and {@link XPathFactory} implementations; it does not ship a DOM, SAX, StAX or Schema factory of its own.</p>
 */
final class SaxonProvider {

    /**
     * A Saxon {@link Configuration} carrying the vendor-specific restrictions that the standard JAXP knobs cannot express.
     *
     * <p>The ignore-all {@link javax.xml.transform.URIResolver} floor is not one of them: it is installed from outside by the shared
     * {@link HardeningTransformerFactory} wrapper (TrAX) or on the Configuration for the XPath path (see {@link SaxonProviderConfigurer#configure(XPathFactory)}),
     * so both cases reuse {@link FallbackIgnoreURIResolver}. What remains here is Saxon-only:</p>
     *
     * <ol>
     *   <li><b>SAX layer.</b> {@link #makeParser} hands every {@link XMLReader} Saxon would otherwise use through
     *   {@link SAXParserHardener#hardenReader(XMLReader)}, which routes it to the matching bundled hardening recipe. External DTDs, entities and XInclude
     *   resolve to empty content at parse time.</li>
     *   <li><b>Collection layer.</b> {@code fn:collection} bypasses the resource resolver and fetches directly, so an empty {@link CollectionFinder} supplies its
     *   ignore outcome instead.</li>
     *   <li><b>Extension-function layer.</b> {@link Feature#ALLOW_EXTERNAL_FUNCTIONS} is disabled, so reflection-based extension calls cannot be used to
     *       sidestep the URI restrictions.</li>
     * </ol>
     */
    private static final class HardenedConfiguration extends Configuration {

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
            //  fn:collection bypasses the resolver, closed by the empty collection finder.
            setCollectionFinder(EMPTY_COLLECTION_FINDER);
            // Use the parser below for both style and source:
            setStyleParserClass("#DEFAULT");
            setSourceParserClass("#DEFAULT");
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
            // The URIResolver floor is installed by the HardeningTransformerFactory wrapper that TransformerHardener puts around this factory.
            ((SaxonTransformerFactory) factory).setConfiguration(new HardenedConfiguration());
            return factory;
        }

        private static XPathFactory configure(final XPathFactory factory) {
            final HardenedConfiguration config = new HardenedConfiguration();
            // XPath has no factory wrapper, so the ignore-all floor lives on the Configuration; reuse FallbackIgnoreURIResolver, adapted to a ResourceResolver.
            config.setResourceResolver(new ResourceResolverWrappingURIResolver(new FallbackIgnoreURIResolver(null, emptySourceSupplier())));
            ((XPathFactoryImpl) factory).setConfiguration(config);
            return factory;
        }
    }

    static TransformerFactory configure(final TransformerFactory factory) {
        try {
            return SaxonProviderConfigurer.configure(factory);
        } catch (final ClassCastException e) {
            // A Saxon-package factory the configurer cannot lock down; refuse it rather than returning it unhardened.
            throw new HardeningException("Unsupported Saxon TransformerFactory " + factory.getClass().getName(), e);
        } catch (final LinkageError e) {
            // Unlikely, but protects method execution from missing optional dependency
            throw new IllegalStateException(e);
        }
    }

    static XPathFactory configure(final XPathFactory factory) {
        try {
            return SaxonProviderConfigurer.configure(factory);
        } catch (final ClassCastException e) {
            // A Saxon-package factory the configurer cannot lock down; refuse it rather than returning it unhardened.
            throw new HardeningException("Unsupported Saxon XPathFactory " + factory.getClass().getName(), e);
        } catch (final LinkageError e) {
            // Unlikely, but protects method execution from missing optional dependency
            throw new IllegalStateException(e);
        }
    }

    /**
     * The empty-{@link Source} shape Saxon's consumers expect, for the {@link FallbackIgnoreURIResolver} floor the TrAX wrapper installs.
     *
     * @return a supplier for Saxon's empty {@link Source}.
     */
    static Supplier<Source> emptySourceSupplier() {
        return EmptySource::getInstance;
    }

    /**
     * Tests whether the factory class is Saxon's, by package prefix, so public subclasses such as {@code net.sf.saxon.BasicTransformerFactory} route to the
     * same locked-down {@link Configuration} as the factory registered for JAXP lookup.
     *
     * @param factoryClass The factory implementation class.
     * @return Whether the class lives in Saxon's open-source or commercial packages.
     */
    static boolean isSaxon(final Class<?> factoryClass) {
        final String name = factoryClass.getName();
        return name.startsWith("net.sf.saxon.") || name.startsWith("com.saxonica.");
    }

    private SaxonProvider() {
    }
}
