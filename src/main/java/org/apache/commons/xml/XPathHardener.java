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
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

/**
 * Capability-driven hardening for any {@link XPathFactory} on the classpath.
 *
 * <p>The XPath object model mirrors TrAX: the stock JDK and Apache Xalan ship an XPath 1.0 engine with no URI-fetching functions, while Saxon adds the XPath 3.1
 * {@code fn:doc}, {@code fn:collection} and {@code fn:unparsed-text} functions that can reach external resources. Rather than branching on the implementation
 * class, {@link #harden(XPathFactory)} probes what the factory supports and adapts:</p>
 * <ul>
 *     <li><strong>Saxon</strong> ({@code net.sf.saxon}): recognized by package prefix and handed to {@link SaxonProvider#configure(XPathFactory)}, so any public
 *         subclass routes to the same recipe as the registered factory. Its URI-fetching
 *         functions and reflection-based extension calls are reachable only through a locked-down Saxon {@code Configuration}, not the standard JAXP knobs; this
 *         is the XPath counterpart of the Saxon exception in {@link TransformerHardener}, kept as a documented package-prefix exception because the required
 *         hardening surface is reachable only through a vendor API.</li>
 *     <li><strong>FODP</strong> ({@code jdk.xml.overrideDefaultParser}, set to {@code false}): best-effort. On the stock JDK it pins the internal parser lookup to
 *         the bundled SAX parser, blocking a system property swap to a third-party parser (defense-in-depth); Xalan rejects the feature and is left unchanged.</li>
 *     <li><strong>FSP</strong> ({@link XMLConstants#FEATURE_SECURE_PROCESSING}): required. It is the only knob both the stock JDK and Xalan XPath engines expose,
 *         and switches on their secure-processing limits. {@link XPathFactory} has no attribute API for finer control.</li>
 *     <li><strong>{@link HardeningXPathFactory}</strong>: required. FSP governs only the engine, not the parser it provisions internally for the
 *         {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points; the wrapper performs that document build with a hardened parser instead, so
 *         the engine never parses.</li>
 * </ul>
 */
final class XPathHardener {

    /**
     * {@code jdk.xml.overrideDefaultParser}: pin to the JDK's bundled SAX parser; defense-in-depth against a system property swap to a third-party parser.
     */
    private static final String FEATURE_OVERRIDE_DEFAULT_PARSER = "jdk.xml.overrideDefaultParser";

    /**
     * Hardens the given factory, returning a hardened wrapper if necessary.
     *
     * @param factory The factory to harden.
     * @return A new hardened factory or the original factory, hardened, if it is a known Saxon factory.
     * @throws HardeningException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature.
     */
    static XPathFactory harden(final XPathFactory factory) {
        if (SaxonProvider.isSaxon(factory.getClass())) {
            // Saxon: only a locked-down Configuration can close its URI-fetching functions and extension-function surface.
            return SaxonProvider.configure(factory);
        }
        // Best-effort: the stock JDK pins its bundled SAX parser (defense-in-depth); Xalan rejects the feature.
        setOptionalFeature(factory, FEATURE_OVERRIDE_DEFAULT_PARSER, false);
        // Required: enables the engine's secure-processing limits; XPathFactory has no attribute API for finer control.
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Required: FSP does not reach the parser the engine provisions for InputSource-taking evaluate calls; the wrapper parses those itself.
        return new HardeningXPathFactory(factory);
    }

    /**
     * Sets a feature on the given factory, throwing a {@link HardeningException} if the implementation does not recognize it.
     *
     * @param factory The factory to harden.
     * @param feature The feature to set.
     * @param value   The value to set.
     * @throws HardeningException Thrown if this {@link XPathFactory} or the {@code XPath}s it creates cannot support this feature or if {@code feature} is
     *                            {@code null}.
     */
    private static void setFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final XPathFactoryConfigurationException e) {
            throw HardeningException.settingFailed("feature", feature, factory, e);
        }
    }

    private static void setOptionalFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final XPathFactoryConfigurationException e) {
            // Ignored: the implementation does not recognize this option.
        }
    }

    private XPathHardener() {
    }
}
