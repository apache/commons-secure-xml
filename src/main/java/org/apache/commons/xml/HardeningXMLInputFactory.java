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
import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
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
 * Creates new, hardened {@link XMLInputFactory} instances.
 * <p>
 * The three universal guarantees on {@link org.apache.commons.xml} apply; StAX exposes no additional vectors beyond them.
 * </p>
 * <p>
 * Not a {@link XMLInputFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-hardened factory through this class
 * by calling an inherited method such as {@code newDefaultFactory()}. The hardened factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class HardeningXMLInputFactory {

    /** Woodstox property: resolver consulted for the external DTD subset. */
    static final String WSTX_DTD_RESOLVER = "com.ctc.wstx.dtdResolver";
    /** Woodstox property: resolver consulted for declared external general entities. */
    static final String WSTX_ENTITY_RESOLVER = "com.ctc.wstx.entityResolver";
    /** Woodstox property: resolver consulted for undeclared entity references. */
    static final String WSTX_UNDECLARED_ENTITY_RESOLVER = "com.ctc.wstx.undeclaredEntityResolver";

    /**
     * Capability-driven hardening for any {@link XMLInputFactory} (StAX) on the classpath.
     *
     * <p>One recipe covers both the JDK Zephyr and Woodstox: the wrapper installs a non-removable {@link FallbackIgnoreXMLResolver} floor on
     * every entity-resolution hook, leaving the standard {@code SUPPORT_DTD} / {@code IS_SUPPORTING_EXTERNAL_ENTITIES} defaults untouched; see the wrapper's
     * Javadoc for the per-implementation hook routing.</p>
     *
     * @param factory the factory to harden; never {@code null}.
     * @return a hardened factory.
     */
    static XMLInputFactory harden(final XMLInputFactory factory) {
        // The wrapper installs the non-removable ignore-all resolver floor that resolves every external DTD and entity to empty content.
        return new Wrapper(factory);
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory}, as by {@link XMLInputFactory#newFactory()}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newFactory() {
        // XMLInputFactory.newInstance, not newFactory: the same specified lookup, but Android's StAX API predates newFactory.
        return harden(XMLInputFactory.newInstance());
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory} resolved from the given factory id.
     *
     * @param factoryId   The name of the factory to find; a system property or service id to look up, not the class name of the implementation.
     * @param classLoader The class loader used in the lookup; {@code null} means the current thread's context class loader.
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown in case of a service configuration error or if the implementation is not available or cannot be instantiated.
     * @throws NullPointerException      Thrown if {@code factoryId} is {@code null}.
     */
    public static XMLInputFactory newFactory(final String factoryId, final ClassLoader classLoader) {
        return harden(XMLInputFactory.newFactory(factoryId, classLoader));
    }

    /**
     * Returns a new, hardened {@link XMLInputFactory}.
     *
     * @return A hardened factory.
     * @throws IllegalStateException     Thrown if a required hardening setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newInstance() {
        return harden(XMLInputFactory.newInstance());
    }

    private HardeningXMLInputFactory() {
        // static only
    }

    /**
     * {@link XMLInputFactory} wrapper that installs a non-removable {@link FallbackIgnoreXMLResolver} floor on the delegate's entity-resolution hook and keeps it
     * non-removable by the caller.
     *
     * <p>The constructor installs the floor through {@code setXMLResolver}, which every implementation routes external resolution through (Woodstox fans it out to
     * both its DTD-subset and entity resolvers). Woodstox keeps one hook outside that fan-out, {@value HardeningXMLInputFactory#WSTX_UNDECLARED_ENTITY_RESOLVER}, which is
     * deliberately left empty: emptying the external subset leaves any entity it declared undeclared, and Woodstox then rejects the reference. The rejection is
     * implementation-prescribed and keeps the resource just as unfetched as the empty resolution the other implementations produce; a caller who wants those
     * references resolved can still set the property, and their resolver lands behind a floor like on every other resolver hook.</p>
     *
     * <p>Every resolver-valued entry point ({@link #setXMLResolver(XMLResolver)}, {@code setProperty(XMLInputFactory.RESOLVER, ...)} and the Woodstox
     * {@code com.ctc.wstx.*Resolver} keys) is routed uniformly: a caller who supplies their own {@link FallbackIgnoreXMLResolver} takes control and it is
     * passed straight to the delegate; otherwise the current resolver on that hook is read, and if it is one of our floors the caller's resolver is set as its
     * {@link FallbackIgnoreXMLResolver#setDelegate delegate} (an opt-in the floor cannot be removed by), or, if the hook is empty, the caller's resolver is
     * wrapped in a new floor. This matters because Woodstox does not chain resolvers: when a resolver returns {@code null}, {@code DefaultInputResolver} falls
     * through to fetching the systemId URL itself, so a caller-set resolver that returns {@code null} must still land behind the floor. {@link #getXMLResolver()} and
     * {@code getProperty} report the caller's resolver unwrapped.</p>
     *
     * @see org.apache.commons.xml
     */
    private static final class Wrapper extends XMLInputFactory {

        private static boolean isResolverProperty(final String name) {
            return XMLInputFactory.RESOLVER.equals(name)
                    || WSTX_DTD_RESOLVER.equals(name)
                    || WSTX_ENTITY_RESOLVER.equals(name)
                    || WSTX_UNDECLARED_ENTITY_RESOLVER.equals(name);
        }


        private static XMLResolver unwrap(final XMLResolver resolver) {
            return resolver instanceof FallbackIgnoreXMLResolver ? ((FallbackIgnoreXMLResolver) resolver).getDelegate() : resolver;
        }

        private final XMLInputFactory delegate;

        /**
         * Constructs a new instance.
         *
         * @param delegate the delegate to wrap; must not be {@code null}.
         * @throws NullPointerException if {@code delegate} is {@code null}.
         */
        private Wrapper(final XMLInputFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            delegate.setXMLResolver(new FallbackIgnoreXMLResolver(null));
        }

        @Override
        public XMLEventReader createFilteredReader(final XMLEventReader reader, final EventFilter filter) throws XMLStreamException {
            return delegate.createFilteredReader(reader, filter);
        }

        @Override
        public XMLStreamReader createFilteredReader(final XMLStreamReader reader, final StreamFilter filter) throws XMLStreamException {
            return delegate.createFilteredReader(reader, filter);
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
        public XMLEventReader createXMLEventReader(final Reader reader) throws XMLStreamException {
            return delegate.createXMLEventReader(reader);
        }

        @Override
        public XMLEventReader createXMLEventReader(final Source source) throws XMLStreamException {
            return delegate.createXMLEventReader(source);
        }

        @Override
        public XMLEventReader createXMLEventReader(final String systemId, final InputStream stream) throws XMLStreamException {
            return delegate.createXMLEventReader(systemId, stream);
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
        public XMLStreamReader createXMLStreamReader(final InputStream stream) throws XMLStreamException {
            return delegate.createXMLStreamReader(stream);
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final InputStream stream, final String encoding) throws XMLStreamException {
            return delegate.createXMLStreamReader(stream, encoding);
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final Reader reader) throws XMLStreamException {
            return delegate.createXMLStreamReader(reader);
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final Source source) throws XMLStreamException {
            return delegate.createXMLStreamReader(source);
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
        public XMLEventAllocator getEventAllocator() {
            return delegate.getEventAllocator();
        }

        @Override
        public Object getProperty(final String name) {
            if (isResolverProperty(name)) {
                return unwrap((XMLResolver) delegate.getProperty(name));
            }
            return delegate.getProperty(name);
        }

        @Override
        public XMLReporter getXMLReporter() {
            return delegate.getXMLReporter();
        }

        @Override
        public XMLResolver getXMLResolver() {
            return unwrap(delegate.getXMLResolver());
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
        public void setProperty(final String name, final Object value) {
            // If a resolver property has a value of the wrong type, pass it to the delegate to generate an appropriate exception.
            if (isResolverProperty(name) && (value == null || value instanceof XMLResolver)) {
                setResolverProperty(name, (XMLResolver) value);
            } else {
                delegate.setProperty(name, value);
            }
        }

        /**
         * Routes a caller-set resolver for the property {@code name} behind the floor currently installed on that hook.
         *
         * @param name     The resolver-valued property being set.
         * @param resolver The caller's resolver, or their own {@link FallbackIgnoreXMLResolver} to take control.
         */
        private void setResolverProperty(final String name, final XMLResolver resolver) {
            if (resolver instanceof FallbackIgnoreXMLResolver) {
                // The caller supplies their own floor: hand it to the delegate as-is.
                delegate.setProperty(name, resolver);
            } else {
                final Object current = delegate.getProperty(name);
                if (current instanceof FallbackIgnoreXMLResolver) {
                    ((FallbackIgnoreXMLResolver) current).setDelegate(resolver);
                } else {
                    delegate.setProperty(name, new FallbackIgnoreXMLResolver(resolver));
                }
            }
        }

        @Override
        public void setXMLReporter(final XMLReporter reporter) {
            delegate.setXMLReporter(reporter);
        }

        @Override
        public void setXMLResolver(final XMLResolver resolver) {
            setResolverProperty(XMLInputFactory.RESOLVER, resolver);
        }
    }
}
