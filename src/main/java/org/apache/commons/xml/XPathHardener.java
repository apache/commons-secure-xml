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
 *         the bundled SAX parser, blocking a sysprop swap to a third-party parser (defense-in-depth); Xalan rejects the feature and is left unchanged.</li>
 *     <li><strong>FSP</strong> ({@link XMLConstants#FEATURE_SECURE_PROCESSING}): required. It is the only knob both the stock JDK and Xalan XPath engines expose,
 *         and switches on their secure-processing limits. {@link XPathFactory} has no attribute API for finer control.</li>
 * </ul>
 */
final class XPathHardener {

    /**
     * {@code jdk.xml.overrideDefaultParser}: pin to the JDK's bundled SAX parser; defense-in-depth against a sysprop swap to a third-party parser.
     */
    private static final String FEATURE_OVERRIDE_DEFAULT_PARSER = "jdk.xml.overrideDefaultParser";

    static XPathFactory harden(final XPathFactory factory) {
        if (SaxonProvider.isSaxon(factory.getClass())) {
            // Saxon: only a locked-down Configuration can close its URI-fetching functions and extension-function surface.
            return SaxonProvider.configure(factory);
        }
        // Best-effort: the stock JDK pins its bundled SAX parser (defense-in-depth); Xalan rejects the feature.
        setOptionalFeature(factory, FEATURE_OVERRIDE_DEFAULT_PARSER, false);
        // Required: enables the engine's secure-processing limits; XPathFactory has no attribute API for finer control.
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }

    private static void setFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            throw HardeningException.settingFailed("feature", feature, factory, e);
        }
    }

    private static void setOptionalFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            // Ignored: the implementation does not recognize this feature.
        }
    }

    private XPathHardener() {
    }
}
