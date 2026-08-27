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

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;

/**
 * Creates new, hardened {@link XMLInputFactory} instances.
 *
 * <p>Each factory method mirrors the {@link XMLInputFactory} static factory method of the same name and signature, and every returned factory carries the
 * hardening guarantees documented for the {@link org.apache.commons.xml package}. StAX exposes no additional vectors beyond the three universal
 * guarantees.</p>
 *
 * <p>On Java 9 or later the Multi-Release jar adds {@code newDefaultFactory()}, mirroring the {@link XMLInputFactory} method of the same name and returning a
 * hardened factory.</p>
 */
public final class SafeXMLInputFactory {

    /**
     * Returns a new, hardened {@link XMLInputFactory}, obtained as by {@link XMLInputFactory#newFactory()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newFactory() {
        // XMLInputFactory.newInstance, not newFactory: the same specified lookup, but Android's StAX API predates newFactory.
        return StaxHardener.harden(XMLInputFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory} resolved from the given factory id, obtained as by
     * {@link XMLInputFactory#newFactory(String, ClassLoader)}.
     * <p>
     * The {@code factoryId} names a system property or service id to look up, same as {@link XMLInputFactory#newFactory(String, ClassLoader)}; it is not the
     * class name of the implementation.
     * </p>
     *
     * @param factoryId   The name of the factory to find; same treatment as a system property.
     * @param classLoader The class loader used in the lookup; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown in case of a service configuration error or if the implementation is not available or cannot be instantiated.
     * @throws NullPointerException      Thrown if {@code factoryId} is {@code null}.
     */
    public static XMLInputFactory newFactory(final String factoryId, final ClassLoader classLoader) {
        return StaxHardener.harden(XMLInputFactory.newFactory(factoryId, classLoader));
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory} of the system-default implementation, obtained as by {@link XMLInputFactory#newDefaultFactory()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static XMLInputFactory newDefaultFactory() {
        return StaxHardener.harden(XMLInputFactory.newDefaultFactory());
    }

    private SafeXMLInputFactory() {
        // static only
    }
}
