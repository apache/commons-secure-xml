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

import static org.apache.commons.xml.JaxpSetters.setFeature;
import static org.apache.commons.xml.JaxpSetters.setOptionalFeature;
import static org.apache.commons.xml.JaxpSetters.trySetProperty;

import java.io.IOException;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Capability-driven hardening for any {@link SAXParserFactory} on the classpath.
 *
 * <p>Rather than branching on the implementation class, {@link #harden(SAXParserFactory)} probes what the parser supports and adapts. Because
 * {@link SAXParserFactory} exposes only a feature API and no property API, the per-parse configuration runs on each {@link XMLReader} the factory produces,
 * funnelled through {@link HardeningSAXParserFactory} into {@link #hardenReader(XMLReader)}:</p>
 * <ul>
 *     <li><strong>Android</strong> (Harmony / Expat): {@link XMLConstants#FEATURE_SECURE_PROCESSING FSP} and the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties
 *         are not recognized, and libexpat enforces its own Billion Laughs check, so neither is applied. Two fixups are still needed: a subset-aware deny-all
 *         resolver (Expat ignores external fetches silently when no resolver is set, so an explicit one is required to <em>fail</em> on external entities while
 *         still letting an unused external subset load), and a {@link HardeningExpatXMLReader} so the unsupported {@code namespace-prefixes} feature is rejected at
 *         configuration time rather than mid-parse.</li>
 *     <li><strong>FSP</strong>: required on every other reader. It switches on the implementation's built-in security manager, which is what carries the
 *         processing limits.</li>
 *     <li><strong>{@code XERCES_LOAD_EXTERNAL_DTD}</strong>: optional. Where supported, it skips the external DTD subset on non-validating parsers so a
 *         DOCTYPE-only document parses without a fetch attempt. If not supported, the fetch will throw instead, due to the following settings.</li>
 *     <li><strong>Limits</strong>: applied best-effort by {@link Limits#tryApply(XMLReader)}, which adapts to the JDK limit properties or Xerces'
 *         {@code SecurityManager} as appropriate.</li>
 *     <li><strong>Deny-all resolver floor</strong>: every reader is wrapped in a {@link HardeningXMLReader} that keeps a deny-all {@link EntityResolver} floor.
 *         That floor blocks external DTD, entity, schema and {@code xi:include} fetches in one place: the stock JDK's XInclude processor ignores
 *         {@code ACCESS_EXTERNAL_*} and consults the {@link EntityResolver} instead, so no {@code ACCESS_EXTERNAL_*} properties are needed here. A caller can
 *         chain its own resolver onto the floor to allow-list resources, but cannot remove it.</li>
 * </ul>
 */
final class SAXParserHardener {

    /**
     * Deny floor that additionally lets the external DTD subset declared by the DOCTYPE be skipped silently; merely <em>declaring</em> an external subset does
     * not throw.
     *
     * <p>Android's Expat routes every external fetch (subset, DOCTYPE {@code SYSTEM}, general/parameter entity) through the 2-arg
     * {@link EntityResolver#resolveEntity(String, String)}; a deny-all resolver there would also reject a DOCTYPE that merely <em>names</em> an unused external
     * subset. As a {@link Resolvers.FallbackDenyResolver} it consults the caller's resolver first; as a {@link LexicalHandler} (via {@code DefaultHandler2}) it
     * tracks the declared subset's identifiers so {@link #onUnresolved} can tell the subset apart from a forbidden external general or parameter entity. It is
     * stateful, so a fresh instance is installed per reader.</p>
     */
    private static final class DtdAwareDenyResolver extends Resolvers.FallbackDenyResolver {

        private String dtdPublicId;
        private String dtdSystemId;
        private boolean inDtd;

        DtdAwareDenyResolver() {
            super(null);
        }

        @Override
        public void startDTD(final String name, final String publicId, final String systemId) {
            inDtd = true;
            dtdPublicId = publicId;
            dtdSystemId = systemId;
        }

        @Override
        public void endDTD() {
            inDtd = false;
        }

        @Override
        protected InputSource onUnresolved(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            // Declaring (but not using) an external subset must not throw: let the parser skip it silently. Everything else is denied by the floor.
            if (inDtd && Objects.equals(publicId, dtdPublicId) && Objects.equals(systemId, dtdSystemId)) {
                return null;
            }
            return super.onUnresolved(name, publicId, baseURI, systemId);
        }
    }

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

        HardeningExpatXMLReader(final XMLReader delegate, final Resolvers.FallbackDenyResolver floor) {
            super(delegate, floor);
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

    /** SAX property carrying the {@link LexicalHandler}; used to observe the DTD boundary on Android's Expat. */
    private static final String LEXICAL_HANDLER_PROPERTY = "http://xml.org/sax/properties/lexical-handler";

    /** Xerces feature: load the external DTD subset for non-validating parsers. */
    private static final String XERCES_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

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
            // Already hardened (for example, handed back through XmlFactories.harden(XMLReader)); the floor is already in place.
            return reader;
        }
        if (ANDROID_EXPAT_READER.equals(reader.getClass().getName())) {
            // Expat ignores external fetches when no resolver is set; a subset-aware deny floor fails on external entities but lets an unused subset load.
            // HardeningExpatXMLReader keeps that floor non-bypassable (routing a caller-set resolver, including SAXParser.parse's handler, through it) and rejects
            // the unsupported namespace-prefixes feature eagerly rather than mid-parse.
            final DtdAwareDenyResolver floor = new DtdAwareDenyResolver();
            final HardeningExpatXMLReader hardened = new HardeningExpatXMLReader(reader, floor);
            // The floor needs the DTD-boundary events to tell the subset apart from entities; Expat recognizes the lexical-handler property.
            trySetProperty(hardened, LEXICAL_HANDLER_PROPERTY, floor);
            return hardened;
        }
        // Required: enables the JDK XMLSecurityManager / Xerces SecurityManager limits.
        setFeature(reader, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Optional: skip the external DTD subset on non-validating parsers so DOCTYPE-only documents parse without a blocked fetch attempt.
        setOptionalFeature(reader, XERCES_LOAD_EXTERNAL_DTD, false);
        // Optional, implementation-based: JDK limit properties or Xerces' SecurityManager.
        Limits.tryApply(reader);
        // Required: HardeningXMLReader installs a deny-all EntityResolver floor on the reader.
        // That floor blocks external DTD, entity, schema and xi:include fetches in one place: no ACCESS_EXTERNAL_* properties are needed here.
        // Callers can chain their resolvers, but not override the floor.
        return new HardeningXMLReader(reader);
    }

    private SAXParserHardener() {
    }
}
