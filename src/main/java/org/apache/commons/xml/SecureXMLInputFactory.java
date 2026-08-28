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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import javax.xml.stream.EventFilter;
import javax.xml.stream.FactoryConfigurationError;
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
 * Creates new, secure {@link XMLInputFactory} instances.
 * <p>
 * The three universal guarantees on {@link org.apache.commons.xml} apply; StAX exposes no additional vectors beyond them.
 * </p>
 * <p>
 * Not a {@link XMLInputFactory} itself, so none of the JAXP static factory methods is inherited: a caller cannot reach a non-secured factory through this class
 * by calling an inherited method such as {@code newDefaultFactory()}. The secure factories are instances of a nested, non-public wrapper class.
 * </p>
 *
 * @see org.apache.commons.xml
 */
public final class SecureXMLInputFactory {

    /**
     * {@link XMLInputFactory} wrapper that installs a non-removable {@link FallbackIgnoreXMLResolver} floor on the delegate's entity-resolution hook and keeps it
     * non-removable by the caller.
     *
     * <p>The constructor installs the floor through {@code setXMLResolver}, which every implementation routes external resolution through (Woodstox fans it out to
     * both its DTD-subset and entity resolvers). Woodstox keeps one hook outside that fan-out, {@value SecureXMLInputFactory#WSTX_UNDECLARED_ENTITY_RESOLVER}, which is
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
    /** Woodstox property: resolver consulted for the external DTD subset. */
    static final String WSTX_DTD_RESOLVER = "com.ctc.wstx.dtdResolver";
    /** Woodstox property: resolver consulted for declared external general entities. */
    static final String WSTX_ENTITY_RESOLVER = "com.ctc.wstx.entityResolver";
    /** Woodstox property: resolver consulted for undeclared entity references. */
    static final String WSTX_UNDECLARED_ENTITY_RESOLVER = "com.ctc.wstx.undeclaredEntityResolver";

    /** Class name of the JDK's built-in default implementation, the Java 8 fallback for {@link #newDefaultFactory()}. */
    private static final String JDK_XML_INPUT_FACTORY = "com.sun.xml.internal.stream.XMLInputFactoryImpl";

    private static final MethodHandle MH_newDefaultInstance = MethodHandleFactory.findStatic(XMLInputFactory.class, "newDefaultFactory",
            MethodType.methodType(XMLInputFactory.class));

    /**
     * Returns a new, secure {@link XMLInputFactory} of the system-default implementation.
     * <p>
     * Obtained as by {@code XMLInputFactory.newDefaultFactory()} where the platform provides it (Java 9 or later), and by instantiating the JDK's built-in
     * implementation directly on Java 8.
     * </p>
     *
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if the running platform provides neither {@code newDefaultFactory()} nor the JDK's built-in implementation
     *                                   (for example Android).
     */
    public static XMLInputFactory newDefaultFactory() {
        if (MH_newDefaultInstance != null) {
            return secure(MethodHandleFactory.invokeExact(() -> (XMLInputFactory) MH_newDefaultInstance.invokeExact(), FactoryConfigurationError.class));
        }
        try {
            // Java 8: the method does not exist, and XMLInputFactory has no class-name-taking lookup; instantiate the JDK's built-in default directly.
            return secure((XMLInputFactory) Class.forName(JDK_XML_INPUT_FACTORY).getConstructor().newInstance());
        } catch (final ReflectiveOperationException e) {
            // Where the class does not exist either (for example Android), report the miss like any StAX factory lookup: with FactoryConfigurationError.
            throw new FactoryConfigurationError(e, "Neither XMLInputFactory.newDefaultFactory() nor " + JDK_XML_INPUT_FACTORY + " is available");
        }
    }

    /**
     * Returns a new, secure {@link XMLInputFactory}, as by {@link XMLInputFactory#newFactory()}.
     *
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newFactory() {
        // XMLInputFactory.newInstance, not newFactory: the same specified lookup, but Android's StAX API predates newFactory.
        return secure(XMLInputFactory.newInstance());
    }

    /**
     * Returns a new, secure {@link XMLInputFactory} resolved from the given factory id.
     *
     * @param factoryId   The name of the factory to find; a system property or service id to look up, not the class name of the implementation.
     * @param classLoader The class loader used in the lookup; {@code null} means the current thread's context class loader.
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown in case of a service configuration error or if the implementation is not available or cannot be instantiated.
     * @throws NullPointerException      Thrown if {@code factoryId} is {@code null}.
     */
    public static XMLInputFactory newFactory(final String factoryId, final ClassLoader classLoader) {
        return secure(XMLInputFactory.newFactory(factoryId, classLoader));
    }

    /**
     * Returns a new, secure {@link XMLInputFactory}.
     *
     * @return A secure factory.
     * @throws IllegalStateException     Thrown if a required secure setting cannot be applied to the underlying implementation.
     * @throws FactoryConfigurationError Thrown if an instance of this factory cannot be loaded.
     */
    public static XMLInputFactory newInstance() {
        return secure(XMLInputFactory.newInstance());
    }

    /**
     * Capability-driven secure for any {@link XMLInputFactory} (StAX) on the classpath.
     *
     * <p>One recipe covers both the JDK Zephyr and Woodstox: the wrapper installs a non-removable {@link FallbackIgnoreXMLResolver} floor on
     * every entity-resolution hook, leaving the standard {@code SUPPORT_DTD} / {@code IS_SUPPORTING_EXTERNAL_ENTITIES} defaults untouched; see the wrapper's
     * Javadoc for the per-implementation hook routing.</p>
     *
     * @param factory the factory to secure; never {@code null}.
     * @return a secure factory.
     */
    static XMLInputFactory secure(final XMLInputFactory factory) {
        // The wrapper installs the non-removable ignore-all resolver floor that resolves every external DTD and entity to empty content.
        return new Wrapper(factory);
    }

    private SecureXMLInputFactory() {
        // static only
    }
}
