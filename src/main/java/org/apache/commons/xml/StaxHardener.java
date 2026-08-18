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

import javax.xml.stream.XMLInputFactory;

/**
 * Capability-driven hardening for any {@link XMLInputFactory} (StAX) on the classpath.
 *
 * <p>One recipe covers both the JDK Zephyr and Woodstox: {@link HardeningXMLInputFactory} installs a non-removable {@link FallbackIgnoreXMLResolver} floor on
 * every entity-resolution hook, leaving the standard {@code SUPPORT_DTD} / {@code IS_SUPPORTING_EXTERNAL_ENTITIES} defaults untouched; see that wrapper's
 * Javadoc for the per-implementation hook routing.</p>
 */
final class StaxHardener {

    /** Woodstox property: resolver consulted for the external DTD subset. */
    static final String WSTX_DTD_RESOLVER = "com.ctc.wstx.dtdResolver";

    /** Woodstox property: resolver consulted for declared external general entities. */
    static final String WSTX_ENTITY_RESOLVER = "com.ctc.wstx.entityResolver";

    /** Woodstox property: resolver consulted for undeclared entity references. */
    static final String WSTX_UNDECLARED_ENTITY_RESOLVER = "com.ctc.wstx.undeclaredEntityResolver";

    static XMLInputFactory harden(final XMLInputFactory factory) {
        // HardeningXMLInputFactory installs the non-removable ignore-all resolver floor that resolves every external DTD and entity to empty content.
        return new HardeningXMLInputFactory(factory);
    }

    private StaxHardener() {
    }
}
