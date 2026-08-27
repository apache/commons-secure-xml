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
import javax.xml.validation.SchemaFactoryConfigurationError;

/**
 * Creates new, hardened {@link SchemaFactory} instances.
 *
 * <p>Each factory method mirrors the {@link SchemaFactory} static factory method of the same name and signature, and every returned factory carries the
 * hardening guarantees documented for the {@link org.apache.commons.xml package}.</p>
 *
 * <p>Beyond the three universal guarantees:</p>
 * <ul>
 * <li>{@code xs:import}, {@code xs:include} and {@code xs:redefine} schemaLocation URIs are not resolved during schema compilation, and</li>
 * <li>{@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hints in instance documents are not resolved during validation.</li>
 * </ul>
 * <p>
 * The same guarantees apply to {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} instances produced from the
 * resulting {@link javax.xml.validation.Schema}.
 * </p>
 *
 * <p>On Java 9 or later the Multi-Release jar adds {@code newDefaultInstance()}, mirroring the {@link SchemaFactory} method of the same name and returning a
 * hardened factory.</p>
 */
public final class SafeSchemaFactory {

    /**
     * Returns a new, hardened {@link SchemaFactory} for the given schema language, obtained as by {@link SchemaFactory#newInstance(String)}.
     *
     * @param schemaLanguage The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @return A hardened factory.
     * @throws IllegalArgumentException        Thrown if no implementation of the schema language is available.
     * @throws NullPointerException            Thrown if {@code schemaLanguage} is {@code null}.
     * @throws SchemaFactoryConfigurationError Thrown if a configuration error is encountered.
     */
    public static SchemaFactory newInstance(final String schemaLanguage) {
        return SchemaHardener.harden(SchemaFactory.newInstance(schemaLanguage));
    }

    /**
     * Returns a new, hardened {@link SchemaFactory} of the given implementation class, obtained as by
     * {@link SchemaFactory#newInstance(String, String, ClassLoader)}.
     *
     * @param schemaLanguage   The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @param factoryClassName The fully qualified class name of the {@link SchemaFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalArgumentException Thrown if {@code factoryClassName} is {@code null}, or if the factory class cannot be loaded or instantiated, or does
     *                                  not support {@code schemaLanguage}.
     * @throws NullPointerException     Thrown if {@code schemaLanguage} is {@code null}.
     */
    public static SchemaFactory newInstance(final String schemaLanguage, final String factoryClassName, final ClassLoader classLoader) {
        return SchemaHardener.harden(SchemaFactory.newInstance(schemaLanguage, factoryClassName, classLoader));
    }

    private SafeSchemaFactory() {
        // static only
    }
}
