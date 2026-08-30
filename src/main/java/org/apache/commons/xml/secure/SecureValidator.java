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

package org.apache.commons.xml.secure;

import java.io.IOException;
import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.validation.Validator;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * {@link Validator} wrapper that rewrites the Source on every {@link Validator#validate(Source)} and {@link Validator#validate(Source, Result)} call through
 * {@link SecureSAXParserFactory#secure(Source, boolean)} before delegating, and keeps an ignore-all {@link LSResourceResolver} floor so {@code xsi:schemaLocation} is not resolved at
 * validation time. {@link #reset()} re-establishes the bare ignore-all floor, matching the just-constructed state.
 */
final class SecureValidator extends Validator {

    private final Validator delegate;

    private final FallbackIgnoreLSResourceResolver floor = new FallbackIgnoreLSResourceResolver(null);

    /**
     * Snapshot of the factory's {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} outcome at creation, like the JDK copies the feature onto its
     * validators.
     */
    private final boolean overrideDefaultParser;

    /**
     * Constructs a new instance.
     *
     * @param delegate         the delegate to wrap; must not be {@code null}.
     * @param overrideDefaultParser whether the source rewrites should use the pluggable parser lookup instead of the platform's built-in parser.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    SecureValidator(final Validator delegate, final boolean overrideDefaultParser) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.overrideDefaultParser = overrideDefaultParser;
        // Block xsi:schemaLocation resolution; neither the JDK nor Xerces reliably propagates the factory's resolver to its Validators. The floor is a
        // non-removable lower bound: a caller opts specific lookups in by setting their own resolver, but cannot drop the ignore-all block.
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
    public void reset() {
        delegate.reset();
        floor.setDelegate(null);
        delegate.setResourceResolver(floor);
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

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public void validate(final Source source, final Result result) throws SAXException, IOException {
        delegate.validate(SecureSAXParserFactory.secure(source, overrideDefaultParser), result);
    }
}
