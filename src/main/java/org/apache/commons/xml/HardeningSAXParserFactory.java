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

import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Universal SAX factory wrapper that funnels every produced parser through {@link SAXParserHardener#hardenReader(XMLReader)}.
 * <p>
 * {@link SAXParserFactory} exposes only a feature API and no property API, so the per-parse hardening (limits, entity blocking, implementation-specific fixups)
 * has to run on each {@link XMLReader} the factory produces. This wrapper returns a {@link HardeningSAXParser}, which applies that hardening lazily to both the
 * SAX 2 {@link XMLReader} and the SAX 1 {@link org.xml.sax.Parser} it exposes.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningSAXParserFactory extends SAXParserFactory {

    /**
     * Returns a new, hardened {@link SAXParserFactory}.
     * <p>
     * Beyond the three universal guarantees on {@link org.apache.commons.xml}, XInclude resolution is denied by default. When
     * {@link SAXParserFactory#setXIncludeAware(boolean) setXIncludeAware(true)} is called on the returned factory, the parser will process {@code xi:include}
     * elements but every external resource lookup is rejected. To permit specific trusted resources, install an {@link org.xml.sax.EntityResolver
     * EntityResolver} on the {@link org.xml.sax.XMLReader} that allow-lists them; any href the resolver does not explicitly allow stays blocked.
     * </p>
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown from {@link SAXParserFactory} in case of a {@link java.util.ServiceConfigurationError service configuration
     *                                   error} or if the implementation is not available or cannot be instantiated.
     */
    public static SAXParserFactory newInstance() {
        return SAXParserHardener.harden(SAXParserFactory.newInstance());
    }

    private final SAXParserFactory delegate;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningSAXParserFactory(final SAXParserFactory delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean getFeature(final String name) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    @Override
    public Schema getSchema() {
        return delegate.getSchema();
    }

    @Override
    public boolean isNamespaceAware() {
        return delegate.isNamespaceAware();
    }

    @Override
    public boolean isValidating() {
        return delegate.isValidating();
    }

    @Override
    public boolean isXIncludeAware() {
        return delegate.isXIncludeAware();
    }

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return new HardeningSAXParser(delegate.newSAXParser());
    }

    @Override
    public void setFeature(final String name, final boolean value) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public void setNamespaceAware(final boolean awareness) {
        delegate.setNamespaceAware(awareness);
    }

    @Override
    public void setSchema(final Schema schema) {
        delegate.setSchema(schema);
    }

    @Override
    public void setValidating(final boolean validating) {
        delegate.setValidating(validating);
    }

    @Override
    public void setXIncludeAware(final boolean state) {
        delegate.setXIncludeAware(state);
    }
}
