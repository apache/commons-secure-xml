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

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Capability-driven hardening for any {@link SAXParserFactory} on the classpath.
 *
 * <p>Rather than branching on the implementation class, {@link #harden(SAXParserFactory)} probes what the parser supports and adapts. Because
 * {@link SAXParserFactory} exposes only a feature API and no property API, the per-parse configuration runs on each {@link XMLReader} the factory produces,
 * funnelled through {@link HardeningSAXParserFactory} into {@link #hardenReader(XMLReader)}:</p>
 * <ul>
 *     <li><strong>Android</strong> (Harmony / Expat): {@link XMLConstants#FEATURE_SECURE_PROCESSING FSP} and the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties
 *         are not recognized, and libexpat enforces its own Billion Laughs check, so neither is applied. Two fixups are still needed: an ignore-all resolver
 *         (Expat ignores external fetches silently when no resolver is set; the floor keeps that behavior non-bypassable, resolving anything unresolved to
 *         empty), and a {@link HardeningExpatXMLReader} so the unsupported {@code namespace-prefixes} feature is rejected at
 *         configuration time rather than mid-parse.</li>
 *     <li><strong>FSP</strong>: required on every other reader. It switches on the implementation's built-in security manager, which is what carries the
 *         processing limits.</li>
 *     <li><strong>Ignore-all resolver floor</strong>: every reader is wrapped in a {@link HardeningXMLReader} that keeps an ignore-all {@link EntityResolver} floor.
 *         That floor blocks external DTD, entity, schema and {@code xi:include} fetches in one place: the stock JDK's XInclude processor ignores
 *         {@code ACCESS_EXTERNAL_*} and consults the {@link EntityResolver} instead, so no {@code ACCESS_EXTERNAL_*} properties are needed here. A caller can
 *         chain its own resolver onto the floor to allow-list resources, but cannot remove it.</li>
 * </ul>
 */
final class SAXParserHardener {

    /**
     * {@link HardeningXMLReader} for Android's {@code org.apache.harmony.xml.ExpatReader} that additionally surfaces its {@code namespace-prefixes} limitation at
     * configuration time.
     *
     * <p>ExpatReader does not actually support the {@code namespace-prefixes} feature: enabling it is accepted by {@code setFeature} but fails later, during
     * {@code parse}, with a {@link SAXNotSupportedException}. Reporting the rejection eagerly from {@link #setFeature(String, boolean)} lets consumers that probe
     * the feature, such as Xalan's identity transformer, catch the exception and fall back instead of failing the whole parse.</p>
     */
    static final class HardeningExpatXMLReader extends HardeningXMLReader {

        private static final String NAMESPACE_PREFIXES_FEATURE = "http://xml.org/sax/features/namespace-prefixes";

        HardeningExpatXMLReader(final XMLReader delegate) {
            super(delegate);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (value && NAMESPACE_PREFIXES_FEATURE.equals(name)) {
                throw new SAXNotSupportedException("ExpatReader does not support enabling the '" + NAMESPACE_PREFIXES_FEATURE + "' feature");
            }
            super.setFeature(name, value);
        }
    }

    /** Class name of Android's Harmony-based {@link SAXParserFactory}, backed by the native Expat parser. */
    private static final String ANDROID_SAX_PARSER_FACTORY = "org.apache.harmony.xml.parsers.SAXParserFactoryImpl";

    /** Class name of Android's Expat-backed {@link XMLReader}. */
    private static final String ANDROID_EXPAT_READER = "org.apache.harmony.xml.ExpatReader";

    static SAXParserFactory harden(final SAXParserFactory factory) {
        // Required: enables the implementation's security manager, which carries the limits. Android's Expat rejects FSP, so it is skipped there.
        if (!ANDROID_SAX_PARSER_FACTORY.equals(factory.getClass().getName())) {
            setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }
        // The per-parse hardening (limits, entity blocking, Android fixups) lives in hardenReader() because SAXParserFactory has no property API.
        return new HardeningSAXParserFactory(factory);
    }

    /**
     * Hardens an existing {@link XMLReader}.
     *
     * @param reader The reader to harden; never {@code null}.
     * @return A hardened reader.
     * @throws IllegalStateException if a required hardening setting cannot be applied to the underlying implementation.
     */
    static XMLReader hardenReader(final XMLReader reader) {
        if (reader instanceof HardeningXMLReader) {
            // Already hardened (for example, a reader from a hardened factory passed back through hardenReader); the floor is already in place.
            return reader;
        }
        if (ANDROID_EXPAT_READER.equals(reader.getClass().getName())) {
            // Expat ignores external fetches when no resolver is set; the ignore-all floor keeps that behavior non-bypassable (routing a caller-set resolver,
            // including SAXParser.parse's handler, through it and resolving anything unresolved to empty) and, via HardeningExpatXMLReader, rejects the
            // unsupported namespace-prefixes feature eagerly rather than mid-parse.
            return new HardeningExpatXMLReader(reader);
        }
        // Required: enables the JDK XMLSecurityManager / Xerces SecurityManager limits.
        setFeature(reader, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Required: HardeningXMLReader installs an ignore-all EntityResolver floor on the reader.
        // That floor blocks external DTD, entity, schema and xi:include fetches in one place: no ACCESS_EXTERNAL_* properties are needed here.
        // Callers can chain their resolvers, but not override the floor.
        return new HardeningXMLReader(reader);
    }

    /**
     * Rewrites a {@link Source} so that any SAX parsing it triggers runs through a hardened {@link XMLReader}.
     *
     * <p>Only a {@link StreamSource} or a {@link SAXSource} without a reader is enriched with a hardened, namespace-aware reader; other source kinds are returned
     * as-is. Used by the TrAX and schema wrappers to route every source they parse through the SAX hardening path.</p>
     *
     * @param source the source to harden; never {@code null}.
     * @return a hardened source.
     * @throws TransformerConfigurationException if a hardened reader cannot be obtained.
     */
    static Source hardenSource(final Source source) throws TransformerConfigurationException {
        if (source instanceof StreamSource || source instanceof SAXSource && ((SAXSource) source).getXMLReader() == null) {
            try {
                final SAXParserFactory factory = harden(SAXParserFactory.newInstance());
                factory.setNamespaceAware(true);
                final XMLReader reader = factory.newSAXParser().getXMLReader();
                final InputSource inputSource = SAXSource.sourceToInputSource(source);
                return inputSource == null ? source : new SAXSource(reader, inputSource);
            } catch (final ParserConfigurationException | SAXException e) {
                throw new TransformerConfigurationException("Failed to obtain a hardened XMLReader for source parsing", e);
            }
        }
        return source;
    }

    private static void setFeature(final SAXParserFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw HardeningException.settingFailed("feature", feature, factory, e);
        }
    }

    private static void setFeature(final XMLReader reader, final String feature, final boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (final Exception e) {
            throw HardeningException.settingFailed("feature", feature, reader, e);
        }
    }

    private SAXParserHardener() {
    }
}
