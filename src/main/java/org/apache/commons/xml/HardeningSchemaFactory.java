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

import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Capability-driven hardening wrapper for any {@link SchemaFactory} on the classpath, the same recipe for every implementation. It is the entry point reached
 * by {@link XmlFactories#newSchemaFactory(String)}; there is no per-implementation branching, no {@code FEATURE_SECURE_PROCESSING} and no limit configuration on the
 * factory itself.
 *
 * <p>Three layers cooperate:</p>
 * <ol>
 *   <li>{@link HardeningSchemaFactory} installs an ignore-all {@link FallbackIgnoreLSResourceResolver} floor on the factory (blocking
 *       {@code xs:import}/{@code xs:include}/{@code xs:redefine} at compile time) and rewrites the Source on every {@code newSchema(Source[])} entry point
 *       through {@link SAXParserHardener#hardenSource(Source)}.</li>
 *   <li>{@link HardeningSchema} wraps every Validator/ValidatorHandler the inner Schema produces and re-installs the floor on each (blocking
 *       {@code xsi:schemaLocation} at validation time), since neither the JDK nor Xerces reliably propagates it through {@code Schema}.</li>
 *   <li>{@link HardeningValidator} rewrites the Source on every {@link Validator#validate(Source)} call.</li>
 * </ol>
 *
 * <p>The hardened reader supplied by {@link SAXParserHardener#hardenSource(Source)} already carries {@code FEATURE_SECURE_PROCESSING} and the processing limits, so a
 * DOCTYPE, external entity or Billion Laughs payload in the schema or instance document is bounded there rather than on this factory. The JAXP 1.5
 * {@code ACCESS_EXTERNAL_*} properties are deliberately not set: the resolver floor already blocks the same fetches on every implementation, and the JDK 8
 * {@code SchemaFactory} has a bug whereby those properties keep blocking even when a caller's own resolver would grant the access. The floor is a non-removable
 * lower bound: a caller-set {@link LSResourceResolver} is routed through it (opting a specific lookup in by returning a non-{@code null} result) rather than
 * replacing it, so hardening cannot be dropped by swapping the resolver.</p>
 */
final class HardeningSchemaFactory extends SchemaFactory {

    private static Source[] harden(final Source[] schemas) throws SAXException {
        final Source[] hardened = new Source[schemas.length];
        try {
            for (int i = 0; i < schemas.length; i++) {
                hardened[i] = SAXParserHardener.hardenSource(schemas[i]);
            }
        } catch (final TransformerConfigurationException e) {
            throw new SAXException("Failed to harden schema source", e);
        }
        return hardened;
    }

    private final SchemaFactory delegate;

    private final FallbackIgnoreLSResourceResolver floor = new FallbackIgnoreLSResourceResolver(null);

    HardeningSchemaFactory(final SchemaFactory delegate) {
        this.delegate = delegate;
        // Compile-time block for xs:import/include/redefine; the wrappers carry the rest (per-product resolver, source rewriting, limits via the reader).
        delegate.setResourceResolver(floor);
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return delegate.getErrorHandler();
    }

    @Override
    public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    @Override
    public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getProperty(name);
    }

    @Override
    public LSResourceResolver getResourceResolver() {
        return floor.getDelegate();
    }

    @Override
    public boolean isSchemaLanguageSupported(final String schemaLanguage) {
        return delegate.isSchemaLanguageSupported(schemaLanguage);
    }

    @Override
    public Schema newSchema() throws SAXException {
        return new HardeningSchema(delegate.newSchema());
    }

    @Override
    public Schema newSchema(final Source[] schemas) throws SAXException {
        return new HardeningSchema(delegate.newSchema(harden(schemas)));
    }

    @Override
    public void setErrorHandler(final ErrorHandler errorHandler) {
        delegate.setErrorHandler(errorHandler);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public void setProperty(final String name, final Object object) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setProperty(name, object);
    }


    @Override
    public void setResourceResolver(final LSResourceResolver resourceResolver) {
        // Route a caller resolver through the floor instead of replacing it, so the ignore-all lower bound cannot be removed.
        floor.setDelegate(resourceResolver);
    }
}
