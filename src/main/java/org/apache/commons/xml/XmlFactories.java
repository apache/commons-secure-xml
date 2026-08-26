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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.SchemaFactoryConfigurationError;
import javax.xml.xpath.XPathFactory;

/**
 * Entry point for obtaining hardened JAXP factories.
 *
 * <p>Every method on this class returns a <em>new, hardened</em> factory instance. No caching or pooling is performed; callers on a hot path are responsible
 * for their own caching.</p>
 *
 * <h2>Hardening guarantees</h2>
 *
 * <p>Every factory returned by this class makes the same three guarantees, regardless of which JAXP implementation is on the classpath:</p>
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
 * <p>Each method on this class adds factory-specific guarantees on top of the three above, documented on the corresponding {@code newXxxFactory()} method.</p>
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
 * <p>This class itself is thread-safe: all methods are static and stateless.</p>
 */
public final class XmlFactories {

    /**
     * System property that switches unresolved external references from the default empty resolution to a thrown exception.
     * <p>
     * How to enable: set {@code -Dorg.apache.commons.xml.throwOnUnresolved=true}. The property is read at resolution time, so it also applies to factories
     * created before it was set; references resolved by a caller-supplied resolver are unaffected.
     * </p>
     */
    static final String THROW_ON_UNRESOLVED = "org.apache.commons.xml.throwOnUnresolved";

    /**
     * Returns a new, hardened {@link DocumentBuilderFactory}.
     * <p>
     * Beyond the three universal guarantees on {@link XmlFactories}, XInclude resolution is denied by default. When
     * {@link DocumentBuilderFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on the returned factory, the parser will process
     * {@code xi:include} elements but every external resource lookup is rejected. To permit specific trusted resources, install an
     * {@link org.xml.sax.EntityResolver EntityResolver} on the {@link DocumentBuilder} that allow-lists them; any href the resolver does not explicitly allow
     * stays blocked.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws IllegalStateException     Thrown if a (non-Andoid) factory cannot support the secure processing feature
     *                                   {@link XMLConstants#FEATURE_SECURE_PROCESSING}.
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service configuration error} or if the
     *                                   implementation is not available or cannot be instantiated.
     */
    public static DocumentBuilderFactory newDocumentBuilderFactory() {
        return DocumentBuilderHardener.harden(DocumentBuilderFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link SAXParserFactory}.
     * <p>
     * Beyond the three universal guarantees on {@link XmlFactories}, XInclude resolution is denied by default. When
     * {@link SAXParserFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on the returned factory, the parser will process {@code xi:include}
     * elements but every external resource lookup is rejected. To permit specific trusted resources, install an {@link org.xml.sax.EntityResolver
     * EntityResolver} on the {@link org.xml.sax.XMLReader} that allow-lists them; any href the resolver does not explicitly allow stays blocked.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException     if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from {@link SAXParserFactory} in case of a {@link java.util.ServiceConfigurationError service configuration
     *                                   error} or if the implementation is not available or cannot be instantiated.
     */
    public static SAXParserFactory newSAXParserFactory() {
        return SAXParserHardener.harden(SAXParserFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link SchemaFactory} for the given schema language.
     * <p>
     * Beyond the three universal guarantees on {@link XmlFactories}:
     * </p>
     * <ul>
     * <li>{@code xs:import}, {@code xs:include} and {@code xs:redefine} schemaLocation URIs are not resolved during schema compilation, and</li>
     * <li>{@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hints in instance documents are not resolved during validation.</li>
     * </ul>
     * <p>
     * The same guarantees apply to {@link javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} instances produced from the
     * resulting {@link javax.xml.validation.Schema}.
     * </p>
     *
     * @param schemaLanguage The schema language, as accepted by {@link SchemaFactory#newInstance(String)}.
     * @return A hardened factory.
     * @throws IllegalArgumentException        Thrown if no implementation of the schema language is available.
     * @throws NullPointerException            Thrown if {@code schemaLanguage} is {@code null}.
     * @throws SchemaFactoryConfigurationError Thrown if a configuration error is encountered.
     */
    public static SchemaFactory newSchemaFactory(final String schemaLanguage) {
        return new HardeningSchemaFactory(SchemaFactory.newInstance(schemaLanguage));
    }

    /**
     * Returns a new, hardened {@link TransformerFactory}.
     * <p>
     * Beyond the three universal guarantees on {@link XmlFactories}: {@code xsl:import}, {@code xsl:include} and {@code document()} URIs are not resolved.
     * </p>
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
     * @return A hardened factory.
     * @throws IllegalStateException if a required hardening setting cannot be applied to the underlying implementation.
     */
    public static TransformerFactory newTransformerFactory() {
        return TransformerHardener.harden(TransformerFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory}.
     * <p>
     * The three universal guarantees on {@link XmlFactories} apply; StAX exposes no additional vectors beyond them.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newXMLInputFactory() {
        return StaxHardener.harden(XMLInputFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XPathFactory} for the default XPath object model.
     * <p>
     * Beyond the three universal guarantees on {@link XmlFactories}, URI-fetching XPath 3.1+ functions ({@code doc()}, {@code collection()},
     * {@code unparsed-text()}) are not resolved.
     * </p>
     * <p>
     * The guarantees also cover the document parse behind {@code XPath.evaluate(String, InputSource)} and {@code XPathExpression.evaluate(InputSource)}: the
     * input document is built through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} instead of the engine's internal parser.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws RuntimeException      Thrown if there is a failure in creating an {@link XPathFactory} for the default object model.
     */
    public static XPathFactory newXPathFactory() {
        return XPathHardener.harden(XPathFactory.newInstance());
    }

    private XmlFactories() {
        // static only
    }
}
