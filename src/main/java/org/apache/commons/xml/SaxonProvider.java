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

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.Feature;
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
     *   {@link XmlFactories#harden(XMLReader)}, which routes it to the matching bundled hardening recipe. DOCTYPE, external entities and XInclude
     *   are refused at parse time.</li>
     *   <li><b>URI-resolution layer.</b> {@link Feature#ALLOWED_PROTOCOLS} is set to the empty string. This blocks XSLT inclusions {@code xsl:include},
     *   {@code xsl:import}, {@code xsl:source-document}, and the XPath/XSLT functions {@code fn:doc}, {@code fn:document}, {@code fn:unparsed-text},
     *   {@code fn:collection}, {@code fn:json-doc} and {@code fn:transform}.</li>
     *   <li><b>Extension-function layer.</b> {@link Feature#ALLOW_EXTERNAL_FUNCTIONS} is disabled, so reflection-based extension calls cannot be used to
     *       sidestep the URI restrictions.</li>
     * </ol>
     */
    private static final class HardenedConfiguration extends Configuration {

        private HardenedConfiguration() {
            // Extension-function layer: turn off Saxon's reflection-based extension calls. Without this an attacker could bypass URI restrictions through
            // user-supplied Java extensions.
            setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
            // URI-resolution layer: empty string disallows every URI scheme. Saxon front-ends the existing ResourceResolver with a ProtocolRestrictor; a
            // later setResourceResolver call would cancel this filter, so this stays last in the constructor.
            setConfigurationProperty(Feature.ALLOWED_PROTOCOLS, "");
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
