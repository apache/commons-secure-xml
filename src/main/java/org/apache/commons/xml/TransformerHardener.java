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
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;

/**
 * Capability-driven hardening for any {@link TransformerFactory} on the classpath.
 *
 * <p>Rather than branching on the implementation class, {@link #harden(TransformerFactory)} probes what the factory supports and adapts:</p>
 * <ul>
 *     <li><strong>Saxon</strong> ({@code net.sf.saxon}): recognized by package prefix and handed to {@link SaxonProvider#configure(TransformerFactory)} for the
 *         channels the standard JAXP knobs cannot close (reflection-based extension functions, the collection finder, the internal SAX parser). It is then
 *         wrapped in {@link HardeningTransformerFactory} like every other implementation to install the {@link FallbackIgnoreURIResolver} floor; the only
 *         difference is the empty-{@link Source} shape the floor returns, {@code EmptySource} for Saxon rather than the default empty DOM document.</li>
 *     <li><strong>FSP</strong> ({@link XMLConstants#FEATURE_SECURE_PROCESSING}): required. On XSLTC it enables the runtime evaluator limits; on Xalan it disables
 *         reflection-based extension functions.</li>
 *     <li><strong>{@link FallbackIgnoreURIResolver} floor</strong>: required. An ignore-all {@link URIResolver} floor, installed by
 *         {@link HardeningTransformerFactory} and carried onto every produced {@link Transformer}, resolves {@code xsl:import}/{@code xsl:include} at compile
 *         time and {@code document()} at runtime to an empty document, the one channel both XSLTC and Xalan route through. A caller-set {@link URIResolver} is
 *         routed through the floor rather than replacing it, so a caller can opt a specific URI in but cannot reopen the fetch.</li>
 *     <li><strong>{@link HardeningTransformerFactory}</strong>: required. Both implementations fall back to {@code SAXParserFactory.newInstance()} to parse a
 *         stylesheet or source document that does not carry its own reader, and only set FSP on it; wrapping the factory rewrites every {@link Source} through an
 *         {@link XmlFactories}-hardened reader instead.</li>
 * </ul>
 */
final class TransformerHardener {

    static TransformerFactory harden(final TransformerFactory factory) {
        // Required: enables secure processing (XSLTC runtime limits; Xalan's extension-function block).
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        if (SaxonProvider.isSaxon(factory.getClass())) {
            // Saxon keeps its vendor Configuration for the channels JAXP cannot close,
            // then goes through the same wrapper as every other impl for the URIResolver floor;
            // EmptySource is the empty-source shape Saxon's consumers expect.
            return new HardeningTransformerFactory((SAXTransformerFactory) SaxonProvider.configure(factory), SaxonProvider.emptySourceSupplier());
        }
        // Required: source/stylesheet parsing provisions its own SAX reader otherwise; the wrapper routes every Source through a hardened one and installs the
        // ignore-all URIResolver floor (blocking xsl:import/include at compile time and document() at runtime) that a caller-set resolver cannot remove.
        return new HardeningTransformerFactory((SAXTransformerFactory) factory);
    }

    private static void setFeature(final TransformerFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw HardeningException.settingFailed("feature", feature, factory, e);
        }
    }

    private TransformerHardener() {
    }
}
