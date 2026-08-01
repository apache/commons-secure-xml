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

import javax.xml.validation.SchemaFactory;

/**
 * Hardening for any {@link SchemaFactory} on the classpath.
 *
 * <p>Unlike the other hardeners there is no per-implementation branching and no feature or limit configuration on the factory itself: schema compilation and
 * validation reach external resources only through the resolver hook, so wrapping the factory with a non-removable deny-all resolver floor is enough on every
 * implementation. The reader used to parse schema and instance documents is hardened separately, through
 * {@link SAXParserHardener#hardenSource(javax.xml.transform.Source)}.</p>
 */
final class SchemaHardener {

    /**
     * Hardens an existing {@link SchemaFactory}.
     *
     * <p>Beyond the three universal guarantees (no external DTD fetch, no external entity resolution, bounded internal entity expansion):</p>
     * <ul>
     *   <li>{@code xs:import}, {@code xs:include} and {@code xs:redefine} schemaLocation URIs are not resolved during schema compilation, and</li>
     *   <li>{@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hints in instance documents are not resolved during validation.</li>
     * </ul>
     *
     * <p>The same guarantees apply to {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} instances produced from the
     * resulting {@link javax.xml.validation.Schema}.</p>
     *
     * @param factory the factory to harden; never {@code null}.
     * @return a hardened factory.
     */
    static SchemaFactory harden(final SchemaFactory factory) {
        return new HardeningSchemaFactory(factory);
    }

    private SchemaHardener() {
    }
}
