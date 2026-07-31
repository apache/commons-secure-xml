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

import java.io.InputStream;
import java.io.Reader;

import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.Source;

/**
 * {@link XMLInputFactory} wrapper that keeps the {@link Resolvers.FallbackDenyXMLResolver} floors {@link StaxHardener} installs on the entity-resolution hooks
 * non-removable by the caller.
 *
 * <p>Every resolver-valued entry point ({@link #setXMLResolver(XMLResolver)}, {@code setProperty(XMLInputFactory.RESOLVER, ...)} and the Woodstox
 * {@code com.ctc.wstx.*Resolver} keys) is routed uniformly: a caller who supplies their own {@link Resolvers.FallbackDenyXMLResolver} takes control and it is
 * passed straight to the delegate; otherwise the current resolver on that hook is read, and if it is one of our floors the caller's resolver is set as its
 * {@link Resolvers.FallbackDenyXMLResolver#setDelegate delegate} (an opt-in the floor cannot be removed by), or, if the hook is empty, the caller's resolver is
 * wrapped in a fresh floor. This matters because Woodstox does not chain resolvers: when a resolver returns {@code null}, {@code DefaultInputResolver} falls
 * through to fetching the systemId URL itself, so a caller-set resolver that returns {@code null} must still land behind the floor. {@link #getXMLResolver()} and
 * {@code getProperty} report the caller's resolver unwrapped.</p>
 */
final class HardeningXMLInputFactory extends XMLInputFactory {

    private final XMLInputFactory delegate;

    HardeningXMLInputFactory(final XMLInputFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setXMLResolver(final XMLResolver resolver) {
        setResolverProperty(XMLInputFactory.RESOLVER, resolver);
    }

    @Override
    public XMLResolver getXMLResolver() {
        return unwrap(delegate.getXMLResolver());
    }

    @Override
    public void setProperty(final String name, final Object value) {
        // If a resolver property has a value of the wrong type, pass it to the delegate to generate an appropriate exception.
        if (isResolverProperty(name) && (value == null || value instanceof XMLResolver)) {
            setResolverProperty(name, (XMLResolver) value);
        } else {
            delegate.setProperty(name, value);
        }
    }

    @Override
    public Object getProperty(final String name) {
        if (isResolverProperty(name)) {
            return unwrap((XMLResolver) delegate.getProperty(name));
        }
        return delegate.getProperty(name);
    }

    /**
     * Routes a caller-set resolver for the property {@code name} behind the floor currently installed on that hook.
     *
     * @param name     The resolver-valued property being set.
     * @param resolver The caller's resolver, or their own {@link Resolvers.FallbackDenyXMLResolver} to take control.
     */
    private void setResolverProperty(final String name, final XMLResolver resolver) {
        if (resolver instanceof Resolvers.FallbackDenyXMLResolver) {
            // The caller supplies their own floor: hand it to the delegate as-is.
            delegate.setProperty(name, resolver);
        } else {
            final Object current = delegate.getProperty(name);
            if (current instanceof Resolvers.FallbackDenyXMLResolver) {
                ((Resolvers.FallbackDenyXMLResolver) current).setDelegate(resolver);
            } else {
                delegate.setProperty(name, new Resolvers.FallbackDenyXMLResolver(resolver));
            }
        }
    }

    private static boolean isResolverProperty(final String name) {
        return XMLInputFactory.RESOLVER.equals(name)
                || StaxHardener.WSTX_DTD_RESOLVER.equals(name)
                || StaxHardener.WSTX_ENTITY_RESOLVER.equals(name)
                || StaxHardener.WSTX_UNDECLARED_ENTITY_RESOLVER.equals(name);
    }

    private static XMLResolver unwrap(final XMLResolver resolver) {
        return resolver instanceof Resolvers.FallbackDenyXMLResolver ? ((Resolvers.FallbackDenyXMLResolver) resolver).getDelegate() : resolver;
    }

    // <editor-fold defaultstate="collapsed" desc="Trivial delegation">
    @Override
    public XMLStreamReader createXMLStreamReader(final Reader reader) throws XMLStreamException {
        return delegate.createXMLStreamReader(reader);
    }

    @Override
    public XMLStreamReader createXMLStreamReader(final Source source) throws XMLStreamException {
        return delegate.createXMLStreamReader(source);
    }

    @Override
    public XMLStreamReader createXMLStreamReader(final InputStream stream) throws XMLStreamException {
        return delegate.createXMLStreamReader(stream);
    }

    @Override
    public XMLStreamReader createXMLStreamReader(final InputStream stream, final String encoding) throws XMLStreamException {
        return delegate.createXMLStreamReader(stream, encoding);
    }

    @Override
    public XMLStreamReader createXMLStreamReader(final String systemId, final InputStream stream) throws XMLStreamException {
        return delegate.createXMLStreamReader(systemId, stream);
    }

    @Override
    public XMLStreamReader createXMLStreamReader(final String systemId, final Reader reader) throws XMLStreamException {
        return delegate.createXMLStreamReader(systemId, reader);
    }

    @Override
    public XMLEventReader createXMLEventReader(final Reader reader) throws XMLStreamException {
        return delegate.createXMLEventReader(reader);
    }

    @Override
    public XMLEventReader createXMLEventReader(final String systemId, final Reader reader) throws XMLStreamException {
        return delegate.createXMLEventReader(systemId, reader);
    }

    @Override
    public XMLEventReader createXMLEventReader(final XMLStreamReader reader) throws XMLStreamException {
        return delegate.createXMLEventReader(reader);
    }

    @Override
    public XMLEventReader createXMLEventReader(final Source source) throws XMLStreamException {
        return delegate.createXMLEventReader(source);
    }

    @Override
    public XMLEventReader createXMLEventReader(final InputStream stream) throws XMLStreamException {
        return delegate.createXMLEventReader(stream);
    }

    @Override
    public XMLEventReader createXMLEventReader(final InputStream stream, final String encoding) throws XMLStreamException {
        return delegate.createXMLEventReader(stream, encoding);
    }

    @Override
    public XMLEventReader createXMLEventReader(final String systemId, final InputStream stream) throws XMLStreamException {
        return delegate.createXMLEventReader(systemId, stream);
    }

    @Override
    public XMLStreamReader createFilteredReader(final XMLStreamReader reader, final StreamFilter filter) throws XMLStreamException {
        return delegate.createFilteredReader(reader, filter);
    }

    @Override
    public XMLEventReader createFilteredReader(final XMLEventReader reader, final EventFilter filter) throws XMLStreamException {
        return delegate.createFilteredReader(reader, filter);
    }

    @Override
    public XMLReporter getXMLReporter() {
        return delegate.getXMLReporter();
    }

    @Override
    public void setXMLReporter(final XMLReporter reporter) {
        delegate.setXMLReporter(reporter);
    }

    @Override
    public boolean isPropertySupported(final String name) {
        return delegate.isPropertySupported(name);
    }

    @Override
    public void setEventAllocator(final XMLEventAllocator allocator) {
        delegate.setEventAllocator(allocator);
    }

    @Override
    public XMLEventAllocator getEventAllocator() {
        return delegate.getEventAllocator();
    }
    // </editor-fold>
}
