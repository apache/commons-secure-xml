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

/**
 * <a href="https://commons.apache.org/xml">Apache Commons Secure XML</a> provides secure-by-default JAXP factory creation for Java. A single method call
 * returns a secure JAXP factory that can be used to <em>safely</em> parse XML files.
 * <p>
 * Every method returns <em>new, secure</em> factory instances. No caching or pooling is performed; callers on a hot path are responsible for their own caching.
 * </p>
 * <p>
 * A returned factory is not necessarily an instance of the underlying implementation. It might be (and usually is) a wrapper around it, so it cannot be cast to
 * the implementation's own class. Everything else about the implementation's behavior is preserved: features, properties, and attributes delegate to it, and
 * only the security behavior differs.
 * </p>
 * <p>
 * Preserved behavior includes the choice of internal parsers. Each TrAX, XPath, or schema implementation has its own way of instantiating them, and the library
 * respects it:
 * </p>
 * <ul>
 * <li>Stock JDK factories use the JDK parsers by default, and expose the {@code jdk.xml.overrideDefaultParser} feature (and Java system property of the same
 * name) to switch to parsers instantiated through {@link java.util.ServiceLoader}.</li>
 * <li>Saxon selects its parsers through its own configuration.</li>
 * </ul>
 * <p>
 * Whichever parser is selected, it is secure.
 * </p>
 * <h2>Security Guarantees</h2>
 * <p>
 * Every factory returned by this library makes the same three guarantees, regardless of which JAXP implementation is on the classpath:
 * </p>
 * <ul>
 * <li><strong>External DTDs are not fetched.</strong></li>
 * <li><strong>External entities are not resolved.</strong></li>
 * <li><strong>Internal entity expansion is bounded</strong> by the processing limits the parser applies, so DoS payloads such as Billion Laughs are rejected
 * before they exhaust resources. The library enables {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} wherever the JAXP API mandates it, and the limits
 * are then the implementation's secure defaults. The StAX API defines no such feature, and no Android parser supports it, so there the bound is whatever the
 * implementation applies on its own, and this guarantee is <strong>best-effort</strong>.</li>
 * </ul>
 * <p>
 * These guarantees are defined on OpenJDK 8 or later (and JDK distributions built from it). No version of Android supports
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING}, so on Android (API level 26 or later) the securing is applied as best-effort without a guarantee,
 * tested as complete starting with API level 33; see the threat model's "Assumptions about the environment".
 * </p>
 * <p>
 * The guarantees hold whether or not the caller opts into DTD validation ({@link javax.xml.parsers.DocumentBuilderFactory#setValidating(boolean)
 * setValidating(true)}) or attaches a compiled XSD via {@link javax.xml.parsers.DocumentBuilderFactory#setSchema(javax.xml.validation.Schema) setSchema}: every
 * external resource the validation would otherwise fetch (the DTD itself, an {@code xsi:schemaLocation} hint, an external entity referenced from the DTD)
 * remains blocked.
 * </p>
 * <p>
 * Each method adds factory-specific guarantees on top of the three above, documented on the corresponding {@code newXxxFactory()} method.
 * </p>
 * <p>
 * Each factory class mirrors every static factory method of its JAXP counterpart:
 * </p>
 * <ul>
 * <li>the class-name/class-loader overloads and the StAX {@code newFactory} family (JDK 8),</li>
 * <li>{@code newDefaultInstance()} (Java 9), and</li>
 * <li>the namespace-aware {@code newNSInstance()} family (Java 13).</li>
 * </ul>
 * <p>
 * All of them work, with the same semantics, on every supported runtime, including Java 8.
 * The {@code newDefaultInstance} methods are an <strong>opt-out of JAXP pluggability</strong>:
 * they pin the platform's built-in implementation instead of whatever a classpath lookup would resolve,
 * which suits a library with minimal XML requirements that does not want to delegate the choice of implementation to the application developer.
 * The DOM, SAX and schema variants fall back to the standard lookup where the runtime provides neither the Java 9 method nor the JDK's built-in class
 * (for example, Android, whose own lookup is pinned to the platform parser, and whose schema lookup falls back to exactly the Xerces implementation).
 * </p>
 * <p>
 * An unresolved external reference resolves to empty content by default, so the parse continues without the resource. To reject it with an exception instead,
 * set the system property {@code org.apache.commons.xml.secure.throwOnUnresolved} to {@code true}; the property is read at resolution time, and references resolved by
 * a caller-supplied resolver are unaffected.
 * </p>
 * <h2>Caller-supplied URIs</h2>
 * <p>
 * A top-level URI passed directly by the caller is fetched as-is: {@code StreamSource(systemId)}, {@code DocumentBuilder.parse(String)}, or a {@code SAXSource}
 * built from a system id all cause the JAXP implementation to open that URI without consulting the secure layer. Use a {@link javax.xml.transform.URIResolver}
 * or {@link org.xml.sax.EntityResolver} if you need to restrict the top-level fetch.
 * </p>
 * <h2>Thread safety</h2>
 * <p>
 * The returned factories inherit the thread-safety properties of the underlying JAXP implementation, which in practice means they are <strong>not guaranteed to
 * be thread-safe</strong>. Create a new factory per thread or synchronize externally.
 * </p>
 */

package org.apache.commons.xml.secure;
