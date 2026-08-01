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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.EntityResolver;

/**
 * Capability-driven hardening for any {@link DocumentBuilderFactory} on the classpath.
 *
 * <p>Rather than branching on the implementation class, {@link #harden(DocumentBuilderFactory)} probes what the factory supports and adapts:</p>
 * <ul>
 *     <li><strong>Android</strong> (Harmony / KXmlParser): recognized by class name and left untouched. It exposes no {@link XMLConstants#FEATURE_SECURE_PROCESSING
 *         FSP}, no JAXP 1.5 {@code ACCESS_EXTERNAL_*} and no attribute API at all, while KXmlParser silently drops user-defined entities, so there is nothing to
 *         apply.</li>
 *     <li><strong>FSP</strong>: required. It switches on the implementation's built-in security manager, which is what carries the processing limits.</li>
 *     <li><strong>{@code XERCES_LOAD_EXTERNAL_DTD}</strong>: optional. Where supported, it skips the external DTD subset on non-validating parsers so a
 *         DOCTYPE-only document parses without a fetch attempt. If not supported, the fetch will throw instead, due to the following settings.</li>
 *     <li><strong>Deny-all resolver floor</strong>: every produced {@link DocumentBuilder} is wrapped by a {@link HardeningDocumentBuilderFactory} that keeps a
 *         deny-all {@link EntityResolver} floor. That floor blocks external DTD, entity, schema and {@code xi:include} fetches in one place: the stock JDK's
 *         XInclude processor ignores {@code ACCESS_EXTERNAL_*} and consults the {@link EntityResolver} instead, so no {@code ACCESS_EXTERNAL_*} attributes are
 *         needed here. A caller can chain its own resolver onto the floor to allow-list resources, but cannot remove it.</li>
 * </ul>
 */
final class DocumentBuilderHardener {

    /** Class name of Android's Harmony-based {@link DocumentBuilderFactory}, which exposes no hardening surface. */
    private static final String ANDROID_DOCUMENT_BUILDER_FACTORY = "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl";

    /** Xerces feature: load the external DTD subset for non-validating parsers. */
    private static final String XERCES_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    static DocumentBuilderFactory harden(final DocumentBuilderFactory factory) {
        // Android exposes no FSP, ACCESS_EXTERNAL_* or attribute API, and KXmlParser drops user-defined entities; nothing to apply.
        if (ANDROID_DOCUMENT_BUILDER_FACTORY.equals(factory.getClass().getName())) {
            return factory;
        }
        // Required: enables the implementation's security manager, which carries the limits.
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // Optional: skip the external DTD subset on non-validating parsers so DOCTYPE-only documents parse without a blocked fetch attempt.
        setOptionalFeature(factory, XERCES_LOAD_EXTERNAL_DTD, false);
        // Required: HardeningDocumentBuilderFactory installs a deny-all EntityResolver floor on every DocumentBuilder.
        // That floor blocks external DTD, entity, schema and xi:include fetches in one place: no ACCESS_EXTERNAL_* attributes are needed here.
        // Callers can chain their resolvers, but not override the floor.
        return new HardeningDocumentBuilderFactory(factory);
    }

    private DocumentBuilderHardener() {
    }
}
