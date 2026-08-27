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
 * Apache Commons XML provides secure-by-default JAXP factory creation for Java. A single method call returns a hardened JAXP factory that can be used to
 * <em>safely</em> parse XML files. The entry points are one factory class per JAXP factory type, whose methods mirror the JAXP static factory methods:
 *
 * <ul>
 *   <li>{@link org.apache.commons.xml.SafeDocumentBuilderFactory}</li>
 *   <li>{@link org.apache.commons.xml.SafeSAXParserFactory}</li>
 *   <li>{@link org.apache.commons.xml.SafeSchemaFactory}</li>
 *   <li>{@link org.apache.commons.xml.SafeTransformerFactory}</li>
 *   <li>{@link org.apache.commons.xml.SafeXMLInputFactory}</li>
 *   <li>{@link org.apache.commons.xml.SafeXPathFactory}</li>
 * </ul>
 *
 * <p>Every method on these classes returns a <em>new, hardened</em> factory instance. No caching or pooling is performed; callers on a hot path are
 * responsible for their own caching.</p>
 *
 * <h2>Hardening guarantees</h2>
 *
 * <p>Every factory returned by these classes makes the same three guarantees, regardless of which JAXP implementation is on the classpath:</p>
 *
 * <ul>
 *   <li><strong>External DTDs are not fetched.</strong></li>
 *   <li><strong>External entities are not resolved.</strong></li>
 *   <li><strong>Internal entity expansion is bounded</strong> by the platform's secure-processing limit, so DoS payloads such as Billion Laughs are rejected
 *       before they exhaust resources.</li>
 * </ul>
 *
 * <p>These guarantees are defined on OpenJDK 8 or later (and JDK distributions built from it). No version of Android supports
 * {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING}, so on Android (API level 19 or later) the hardening is applied as best-effort without a guarantee,
 * tested as complete starting with API level 33; see the threat model's "Assumptions about the environment".</p>
 *
 * <p>The guarantees hold whether or not the caller opts into DTD validation
 * ({@link javax.xml.parsers.DocumentBuilderFactory#setValidating(boolean) setValidating(true)}) or attaches a compiled XSD via
 * {@link javax.xml.parsers.DocumentBuilderFactory#setSchema(javax.xml.validation.Schema) setSchema}: every external resource the validation would otherwise
 * fetch (the DTD itself, an {@code xsi:schemaLocation} hint, an external entity referenced from the DTD) remains blocked.</p>
 *
 * <p>Each factory class adds factory-specific guarantees on top of the three above, documented on the class itself.</p>
 *
 * <p>An unresolved external reference resolves to empty content by default, so the parse continues without the resource. To reject it with an exception
 * instead, set the system property {@code org.apache.commons.xml.throwOnUnresolved} to {@code true}; the property is read at resolution time, and references
 * resolved by a caller-supplied resolver are unaffected.</p>
 *
 * <h2>Caller-supplied URIs</h2>
 *
 * <p>A top-level URI passed directly by the caller is fetched as-is: {@code StreamSource(systemId)}, {@code DocumentBuilder.parse(String)}, or a
 * {@code SAXSource} built from a system id all cause the JAXP implementation to open that URI without consulting the hardening layer. Use a
 * {@link javax.xml.transform.URIResolver} or {@link org.xml.sax.EntityResolver} if you need to restrict the top-level fetch.</p>
 *
 * <h2>Thread safety</h2>
 *
 * <p>The returned factories inherit the thread-safety properties of the underlying JAXP implementation, which in practice means they are <strong>not
 * guaranteed to be thread-safe</strong>. Create a new factory per thread or synchronize externally.</p>
 *
 * <p>The factory classes themselves are thread-safe: all methods are static and stateless.</p>
 */

package org.apache.commons.xml;
