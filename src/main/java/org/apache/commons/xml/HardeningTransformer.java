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
import java.util.Properties;
import java.util.function.Supplier;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

/**
 * {@link Transformer} wrapper that rewrites the Source on every {@link Transformer#transform(Source, Result)} call through
 * {@link HardeningSAXParserFactory#harden(Source)} before delegating, and keeps an ignore-all {@link URIResolver} floor so runtime {@code document()} calls a
 * caller does not resolve return empty rather than being fetched.
 * <p>
 * The floor is installed on the delegate transformer at construction, seeded with the factory's compile-time resolver; {@link #setURIResolver(URIResolver)}
 * routes a caller's resolver through it rather than replacing it, so the block cannot be dropped. {@link #reset()} re-establishes the floor, seeded again with
 * the factory's compile-time resolver, matching the just-constructed state.
 * </p>
 */
final class HardeningTransformer extends Transformer {

    private final Transformer delegate;

    /**
     * Compile-time URIResolver snapshot the floor is seeded with, both at construction and again on {@link #reset()}.
     */
    private final URIResolver uriResolver;

    private final FallbackIgnoreURIResolver floor;

    /**
     * Constructs a new instance.
     *
     * @param delegate    the delegate to wrap; must not be {@code null}.
     * @param uriResolver the compile-time URIResolver snapshot to seed the floor with; may be {@code null}.
     * @param emptySource the empty-{@link Source} supplier for the produced Transformers; {@code null} for the default empty DOM document.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningTransformer(final Transformer delegate, final URIResolver uriResolver, final Supplier<Source> emptySource) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.uriResolver = uriResolver;
        this.floor = new FallbackIgnoreURIResolver(uriResolver, emptySource);
        delegate.setURIResolver(floor);
    }

    @Override
    public void clearParameters() {
        delegate.clearParameters();
    }

    @Override
    public ErrorListener getErrorListener() {
        return delegate.getErrorListener();
    }

    @Override
    public Properties getOutputProperties() {
        return delegate.getOutputProperties();
    }

    @Override
    public String getOutputProperty(final String name) {
        return delegate.getOutputProperty(name);
    }

    @Override
    public Object getParameter(final String name) {
        return delegate.getParameter(name);
    }

    @Override
    public URIResolver getURIResolver() {
        return floor.getDelegate();
    }

    @Override
    public void reset() {
        delegate.reset();
        floor.setDelegate(uriResolver);
        delegate.setURIResolver(floor);
    }

    @Override
    public void setErrorListener(final ErrorListener listener) {
        delegate.setErrorListener(listener);
    }

    @Override
    public void setOutputProperties(final Properties properties) {
        delegate.setOutputProperties(properties);
    }

    @Override
    public void setOutputProperty(final String name, final String value) {
        delegate.setOutputProperty(name, value);
    }

    @Override
    public void setParameter(final String name, final Object value) {
        delegate.setParameter(name, value);
    }

    @Override
    public void setURIResolver(final URIResolver resolver) {
        floor.setDelegate(resolver);
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public void transform(final Source xmlSource, final Result outputTarget) throws TransformerException {
        try {
            delegate.transform(HardeningSAXParserFactory.harden(xmlSource), outputTarget);
        } catch (final TransformerConfigurationException e) {
            throw new TransformerException(e);
        }
    }
}
