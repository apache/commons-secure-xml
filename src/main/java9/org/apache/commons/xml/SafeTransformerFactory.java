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

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Creates new, hardened {@link TransformerFactory} instances.
 *
 * <p>Each factory method mirrors the {@link TransformerFactory} static factory method of the same name and signature, and every returned factory carries the
 * hardening guarantees documented for the {@link org.apache.commons.xml package}.</p>
 *
 * <p>Beyond the three universal guarantees: {@code xsl:import}, {@code xsl:include} and {@code document()} URIs are not resolved.</p>
 * <p>
 * The guarantees govern what the transform reads, not what it writes: an output instruction like {@code xsl:result-document} still writes wherever the
 * stylesheet directs, so an untrusted stylesheet's output destinations must be restricted outside the library.
 * </p>
 * <p>
 * The guarantees apply to every parser the factory creates internally for the standard {@link TransformerFactory} entry points: stylesheet compilation
 * ({@link TransformerFactory#newTemplates(javax.xml.transform.Source) newTemplates(Source)},
 * {@link TransformerFactory#newTransformer(javax.xml.transform.Source) newTransformer(Source)}) and source-document reading at
 * {@code Transformer.transform(Source, Result)} time.
 * </p>
 * <p>
 * The {@link javax.xml.transform.sax.SAXTransformerFactory} extension methods ({@code newTransformerHandler(..)}, {@code newTemplatesHandler()},
 * {@code newXMLFilter(..)}), if reachable by casting the returned factory, produce objects carrying the same guarantees.
 * </p>
 *
 * <p>On Java 9 or later the Multi-Release jar adds {@code newDefaultInstance()}, mirroring the {@link TransformerFactory} method of the same name and
 * returning a hardened factory.</p>
 */
public final class SafeTransformerFactory {

    /**
     * Returns a new, hardened {@link TransformerFactory}, obtained as by {@link TransformerFactory#newInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException                Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws TransformerFactoryConfigurationError Thrown if the implementation is not available or cannot be instantiated.
     */
    public static TransformerFactory newInstance() {
        return TransformerHardener.harden(TransformerFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link TransformerFactory} of the given implementation class, obtained as by
     * {@link TransformerFactory#newInstance(String, ClassLoader)}.
     *
     * @param factoryClassName The fully qualified class name of the {@link TransformerFactory} implementation.
     * @param classLoader      The class loader used to load the factory class; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException                Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws TransformerFactoryConfigurationError Thrown if {@code factoryClassName} is {@code null} or the factory class cannot be loaded or instantiated.
     */
    public static TransformerFactory newInstance(final String factoryClassName, final ClassLoader classLoader) {
        return TransformerHardener.harden(TransformerFactory.newInstance(factoryClassName, classLoader));
    }

    /**
     * Returns a new, hardened {@link TransformerFactory} of the system-default implementation, obtained as by {@link TransformerFactory#newDefaultInstance()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static TransformerFactory newDefaultInstance() {
        return TransformerHardener.harden(TransformerFactory.newDefaultInstance());
    }

    private SafeTransformerFactory() {
        // static only
    }
}
