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

import static org.apache.commons.xml.JaxpSetters.setOptionalProperty;
import static org.apache.commons.xml.JaxpSetters.trySetProperty;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

/**
 * Capability-driven hardening for any {@link XMLInputFactory} (StAX) on the classpath.
 *
 * <p>Rather than branching on the implementation class, {@link #harden(XMLInputFactory)} consolidates the JDK Zephyr and Woodstox recipes into one pass that
 * probes which properties each factory accepts and adapts:</p>
 * <ul>
 *     <li><strong>External DTD subset</strong>: skipped via Zephyr's {@value #ZEPHYR_IGNORE_EXTERNAL_DTD} (best-effort), so a DOCTYPE-only document parses
 *         without a fetch attempt instead of tripping the deny-all resolver below. Woodstox skips it through {@value #WSTX_DTD_RESOLVER} instead.</li>
 *     <li><strong>External entities</strong>: denied through a non-removable {@link Resolvers.FallbackDenyXMLResolver} floor on the entity-resolution hook,
 *         leaving the standard {@code SUPPORT_DTD} / {@code IS_SUPPORTING_EXTERNAL_ENTITIES} defaults untouched. Woodstox exposes fine-grained hooks, so when all
 *         three apply the factory is Woodstox: {@value #WSTX_DTD_RESOLVER} (empty external subset, but a thrown error on external parameter entities, which share
 *         that hook), {@value #WSTX_ENTITY_RESOLVER} (the floor, denying declared external general entities) and {@value #WSTX_UNDECLARED_ENTITY_RESOLVER}
 *         (silently drop undeclared references left by the skipped subset). Any factory that does not accept that trio (the JDK Zephyr, or an unrecognized
 *         implementation) instead gets the floor through {@code setXMLResolver}. Either way the factory is wrapped in a {@link HardeningXMLInputFactory} that
 *         routes a caller-set resolver through the floor rather than letting it replace the deny-all block.</li>
 * </ul>
 */
final class StaxHardener {

    /** Woodstox property: resolver consulted for the external DTD subset. */
    static final String WSTX_DTD_RESOLVER = "com.ctc.wstx.dtdResolver";

    /** Woodstox property: resolver consulted for declared external general entities. */
    static final String WSTX_ENTITY_RESOLVER = "com.ctc.wstx.entityResolver";

    /** Woodstox property: resolver consulted for undeclared entity references. */
    static final String WSTX_UNDECLARED_ENTITY_RESOLVER = "com.ctc.wstx.undeclaredEntityResolver";

    /** Zephyr property: skip external DTD subset loading entirely, so a DOCTYPE-only document parses without a fetch attempt. */
    private static final String ZEPHYR_IGNORE_EXTERNAL_DTD = "http://java.sun.com/xml/stream/properties/ignore-external-dtd";

    /**
     * Woodstox DTD-subset floor: a {@link Resolvers.FallbackIgnoreXMLResolver} that returns the empty input for the external DTD subset (its inherited policy)
     * but throws on external parameter entities.
     *
     * <p>Woodstox calls this hook with {@code entityName == null} for the subset and {@code entityName != null} for parameter-entity expansion (that
     * discriminator is the 4th {@code resolveEntity} argument; the JDK Zephyr always passes {@code null} there). Applied best-effort, ignored by implementations
     * that do not recognize the property.</p>
     */
    private static final class DtdSubsetFloor extends Resolvers.FallbackIgnoreXMLResolver {

        DtdSubsetFloor() {
            super(null);
        }

        @Override
        protected Object onUnresolved(final String publicID, final String systemID, final String baseURI, final String entityName) throws XMLStreamException {
            // External parameter entity (entityName != null): deny, reusing the standard hardening message.
            if (entityName != null) {
                throw denied(publicID, systemID, baseURI, entityName);
            }
            // Subset (entityName == null): skip it with the empty input from the ignore floor.
            return super.onUnresolved(publicID, systemID, baseURI, entityName);
        }
    }

    static XMLInputFactory harden(final XMLInputFactory factory) {
        // Optional: Zephyr's StAX equivalent of XERCES_LOAD_EXTERNAL_DTD=false skips the external DTD subset entirely.
        setOptionalProperty(factory, ZEPHYR_IGNORE_EXTERNAL_DTD, true);

        // Each hook carries its own FallbackDenyXMLResolver floor; a caller can opt specific entities in through it, but cannot remove it (see
        // HardeningXMLInputFactory, which routes a caller-set resolver into the floor rather than replacing it). The DTD-subset and undeclared-entity hooks skip
        // (empty input) rather than deny on an unresolved lookup, so a DOCTYPE-only document still parses.
        if (!(trySetProperty(factory, WSTX_DTD_RESOLVER, new DtdSubsetFloor())
                && trySetProperty(factory, WSTX_ENTITY_RESOLVER, new Resolvers.FallbackDenyXMLResolver(null))
                && trySetProperty(factory, WSTX_UNDECLARED_ENTITY_RESOLVER, new Resolvers.FallbackIgnoreXMLResolver(null)))) {
            // Fallback (JDK Zephyr or unrecognized): the single resolver carries the deny-all floor.
            factory.setXMLResolver(new Resolvers.FallbackDenyXMLResolver(null));
        }
        return new HardeningXMLInputFactory(factory);
    }

    private StaxHardener() {
    }
}
