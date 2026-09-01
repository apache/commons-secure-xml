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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SecureXMLInputFactory} and the {@link XMLInputFactory} wrapper it installs.
 * <p>
 * The wrapper's delegation and resolver-routing logic is exercised against a recording stand-in factory, so every branch is deterministic on every platform.
 * The public factory methods are additionally exercised end-to-end against the platform's real implementation, including the Woodstox-specific resolver hooks
 * where that implementation is present.
 * </p>
 */
@Tag("stax")
class SecureXMLInputFactoryTest {

    /**
     * A recording stand-in {@link XMLInputFactory} for the delegation tests.
     * <p>
     * Every call is recorded in {@link #calls} together with the runtime class and identity hash code of each argument, so the wrapper can be asserted to
     * forward the caller's exact arguments. The resolver hook the fake reports through {@code getProperty} and {@code getXMLResolver} is whatever the wrapper
     * or a test last installed, so the tests can steer the wrapper into each routing branch deterministically.
     * </p>
     */
    private static final class RecordingXMLInputFactory extends XMLInputFactory {

        /** The {@link XMLEventReader} every event-flavor creation method returns. */
        static final XMLEventReader EVENT_SENTINEL = proxy(XMLEventReader.class);

        /** The {@link XMLStreamReader} every stream-flavor creation method returns. */
        static final XMLStreamReader STREAM_SENTINEL = proxy(XMLStreamReader.class);

        /** A stand-in {@link XMLEventAllocator} for the round-trip tests. */
        static final XMLEventAllocator ALLOCATOR_SENTINEL = proxy(XMLEventAllocator.class);

        /** A stand-in {@link XMLReporter} for the round-trip tests. */
        static final XMLReporter REPORTER_SENTINEL = proxy(XMLReporter.class);

        /** A stand-in {@link EventFilter} for the delegation tests. */
        static final EventFilter EVENT_FILTER_SENTINEL = proxy(EventFilter.class);

        /** A stand-in {@link StreamFilter} for the delegation tests. */
        static final StreamFilter STREAM_FILTER_SENTINEL = proxy(StreamFilter.class);

        /**
         * Formats a recorded call so a test can assert the wrapper forwarded the exact arguments.
         */
        static String call(final String method, final Object... args) {
            final StringBuilder entry = new StringBuilder(method).append('(');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    entry.append(", ");
                }
                final Object arg = args[i];
                entry.append(arg == null ? "null" : arg.getClass().getName()).append('@').append(System.identityHashCode(arg));
            }
            return entry.append(')').toString();
        }

        /**
         * Builds an unbacked instance of the given interface whose boolean and int methods answer their neutral values and whose other methods answer
         * {@code null}; the sentinel readers returned from the fake's creation methods.
         */
        private static <T> T proxy(final Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
                if (method.getReturnType() == boolean.class) {
                    return Boolean.FALSE;
                }
                if (method.getReturnType() == int.class) {
                    return 0;
                }
                return null;
            });
        }

        /** Recorded calls in order, each formatted by {@link #call}. */
        final List<String> calls = new ArrayList<>();

        /** The resolver-valued hook this factory reports; the wrapper's floor or whatever a test installs. */
        Object resolverHook;

        /** The allocator last installed via {@code setEventAllocator}. */
        XMLEventAllocator allocator;

        /** The reporter last installed via {@code setXMLReporter}. */
        XMLReporter reporter;

        /** The answer {@code isPropertySupported} gives; {@code true} by default. */
        boolean supported = true;

        @Override
        public XMLEventReader createFilteredReader(final XMLEventReader reader, final EventFilter filter) {
            record("createFilteredReader", reader, filter);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLStreamReader createFilteredReader(final XMLStreamReader reader, final StreamFilter filter) {
            record("createFilteredReader", reader, filter);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final InputStream stream) {
            record("createXMLEventReader", stream);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final InputStream stream, final String encoding) {
            record("createXMLEventReader", stream, encoding);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final Reader reader) {
            record("createXMLEventReader", reader);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final Source source) {
            record("createXMLEventReader", source);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final String systemId, final InputStream stream) {
            record("createXMLEventReader", systemId, stream);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final String systemId, final Reader reader) {
            record("createXMLEventReader", systemId, reader);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLEventReader createXMLEventReader(final XMLStreamReader reader) {
            record("createXMLEventReader", reader);
            return EVENT_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final InputStream stream) {
            record("createXMLStreamReader", stream);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final InputStream stream, final String encoding) {
            record("createXMLStreamReader", stream, encoding);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final Reader reader) {
            record("createXMLStreamReader", reader);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final Source source) {
            record("createXMLStreamReader", source);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final String systemId, final InputStream stream) {
            record("createXMLStreamReader", systemId, stream);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLStreamReader createXMLStreamReader(final String systemId, final Reader reader) {
            record("createXMLStreamReader", systemId, reader);
            return STREAM_SENTINEL;
        }

        @Override
        public XMLEventAllocator getEventAllocator() {
            record("getEventAllocator");
            return allocator;
        }

        @Override
        public Object getProperty(final String name) {
            record("getProperty", name);
            return resolverHook;
        }

        @Override
        public XMLReporter getXMLReporter() {
            record("getXMLReporter");
            return reporter;
        }

        @Override
        public XMLResolver getXMLResolver() {
            record("getXMLResolver");
            return (XMLResolver) resolverHook;
        }

        @Override
        public boolean isPropertySupported(final String name) {
            record("isPropertySupported", name);
            return supported;
        }

        /**
         * Records a call with the runtime class and identity hash code of each argument, the format {@link #calls} entries use.
         */
        private void record(final String method, final Object... args) {
            calls.add(call(method, args));
        }

        @Override
        public void setEventAllocator(final XMLEventAllocator eventAllocator) {
            record("setEventAllocator", eventAllocator);
            allocator = eventAllocator;
        }

        @Override
        public void setProperty(final String name, final Object value) {
            record("setProperty", name, value);
            if (value == null || value instanceof XMLResolver) {
                resolverHook = value;
            }
        }

        @Override
        public void setXMLReporter(final XMLReporter xmlReporter) {
            record("setXMLReporter", xmlReporter);
            reporter = xmlReporter;
        }

        @Override
        public void setXMLResolver(final XMLResolver resolver) {
            record("setXMLResolver", resolver);
            resolverHook = resolver;
        }
    }

    private static final String BENIGN_XML = "<?xml version=\"1.0\"?>\n<root><child>hello</child></root>\n";

    private static final String SYSTEM_ID = "http://example.invalid/document.xml";

    /**
     * Drains every event from the event reader and returns the accumulated character and CDATA data.
     */
    private static String drainEvents(final XMLEventReader reader) throws XMLStreamException {
        final StringBuilder text = new StringBuilder();
        try {
            while (reader.hasNext()) {
                final XMLEvent event = reader.nextEvent();
                if (event.isCharacters() || event.getEventType() == XMLStreamConstants.CDATA) {
                    text.append(event.asCharacters().getData());
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    /**
     * Drains every event from the stream reader and returns the accumulated character data.
     */
    private static String drainStream(final XMLStreamReader reader) throws XMLStreamException {
        final StringBuilder text = new StringBuilder();
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.CHARACTERS) {
                    text.append(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    @Test
    void getPropertyReportsForeignResolverUnchanged() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLResolver foreign = (publicID, systemID, baseURI, namespace) -> "foreign";
        fake.setProperty(XMLInputFactory.RESOLVER, foreign);
        assertSame(foreign, secure.getProperty(XMLInputFactory.RESOLVER), "a non-floor resolver must be reported unchanged");
        assertSame(foreign, secure.getXMLResolver(), "getXMLResolver must report a non-floor resolver unchanged");
    }

    @Test
    void getPropertyReportsNullResolver() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        fake.setProperty(XMLInputFactory.RESOLVER, null);
        assertNull(secure.getProperty(XMLInputFactory.RESOLVER), "an empty hook must report no resolver");
        assertNull(secure.getXMLResolver(), "an empty hook must report no resolver");
    }

    @Test
    void getXMLResolverInitiallyNull() {
        assertNull(SecureXMLInputFactory.newInstance().getXMLResolver(), "a fresh secure factory must report no caller resolver");
        assertNull(SecureXMLInputFactory.newDefaultFactory().getXMLResolver(), "a fresh secure factory must report no caller resolver");
    }

    @Test
    void newDefaultFactoryParsesBenignDocument() throws Exception {
        final XMLInputFactory factory = SecureXMLInputFactory.newDefaultFactory();
        assertEquals(Boolean.TRUE, factory.getProperty(XMLInputFactory.SUPPORT_DTD), "a secure factory must keep the implementation's DTD default");
        assertTrue(drainStream(factory.createXMLStreamReader(new StringReader(BENIGN_XML))).contains("hello"), "stream reader must parse the document");
        assertTrue(drainEvents(factory.createXMLEventReader(new StringReader(BENIGN_XML))).contains("hello"), "event reader must parse the document");
    }

    @Test
    void newFactoryNullFactoryIdThrows() {
        assertThrows(NullPointerException.class, () -> SecureXMLInputFactory.newFactory(null, null), "a null factory id must be rejected");
    }

    @Test
    void newFactoryParsesBenignDocument() throws Exception {
        final XMLInputFactory factory = SecureXMLInputFactory.newFactory();
        assertTrue(drainStream(factory.createXMLStreamReader(new StringReader(BENIGN_XML))).contains("hello"), "stream reader must parse the document");
        assertTrue(drainEvents(factory.createXMLEventReader(new StringReader(BENIGN_XML))).contains("hello"), "event reader must parse the document");
    }

    @Test
    void newFactoryWithFactoryIdReturnsUsableSecureFactory() throws Exception {
        final String factoryId = "org.apache.commons.xml.secure.test.inputFactory";
        System.setProperty(factoryId, XMLInputFactory.newInstance().getClass().getName());
        try {
            final XMLInputFactory factory = SecureXMLInputFactory.newFactory(factoryId, getClass().getClassLoader());
            assertNull(factory.getXMLResolver(), "a fresh secure factory must report no caller resolver");
            assertTrue(drainStream(factory.createXMLStreamReader(new StringReader(BENIGN_XML))).contains("hello"), "factory must parse the document");
        } finally {
            System.clearProperty(factoryId);
        }
    }

    @Test
    void newInstanceParsesBenignDocument() throws Exception {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        assertTrue(drainStream(factory.createXMLStreamReader(new StringReader(BENIGN_XML))).contains("hello"), "stream reader must parse the document");
        assertTrue(drainEvents(factory.createXMLEventReader(new StringReader(BENIGN_XML))).contains("hello"), "event reader must parse the document");
    }

    @Test
    void privateConstructorIsInvokable() throws Exception {
        final Constructor<SecureXMLInputFactory> constructor = SecureXMLInputFactory.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance(), "the private constructor must exist and be invokable");
    }

    @Test
    void secureNullDelegateThrows() {
        assertThrows(NullPointerException.class, () -> SecureXMLInputFactory.secure(null), "a null delegate must be rejected");
    }

    @Test
    void setPropertyCallerFloorTakesControl() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> "resolved";
        final FallbackIgnoreXMLResolver ownFloor = new FallbackIgnoreXMLResolver(caller);
        secure.setProperty(XMLInputFactory.RESOLVER, ownFloor);
        assertSame(ownFloor, fake.resolverHook, "the caller's own floor must be handed to the delegate as-is");
        assertSame(caller, secure.getXMLResolver(), "getXMLResolver must report the delegate of the caller's floor");
        assertSame(caller, secure.getProperty(XMLInputFactory.RESOLVER), "getProperty must report the delegate of the caller's floor");
    }

    @Test
    void setPropertyNullResolverClearsCallerDelegate() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final FallbackIgnoreXMLResolver floor = (FallbackIgnoreXMLResolver) fake.resolverHook;
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        secure.setXMLResolver(caller);
        secure.setProperty(XMLInputFactory.RESOLVER, null);
        assertNull(floor.getDelegate(), "a null resolver property must clear the floor's delegate");
        assertNull(secure.getXMLResolver(), "getXMLResolver must report no caller resolver");
    }

    @Test
    void setPropertyRoutesEveryResolverHookUniformly() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> "resolved";
        for (final String hook : new String[] { XMLInputFactory.RESOLVER, SecureXMLInputFactory.WSTX_DTD_RESOLVER, SecureXMLInputFactory.WSTX_ENTITY_RESOLVER,
                SecureXMLInputFactory.WSTX_UNDECLARED_ENTITY_RESOLVER }) {
            fake.setProperty(hook, null);
            secure.setProperty(hook, caller);
            assertSame(caller, secure.getProperty(hook), "the caller's resolver must be reported unwrapped on " + hook);
        }
    }

    @Test
    void setPropertyWrapsCallerWhenHookIsNotAFloor() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> "resolved";
        for (final Object foreign : new Object[] { null, (XMLResolver) (publicID, systemID, baseURI, namespace) -> "foreign" }) {
            fake.setProperty(XMLInputFactory.RESOLVER, foreign);
            secure.setProperty(XMLInputFactory.RESOLVER, caller);
            assertInstanceOf(FallbackIgnoreXMLResolver.class, fake.resolverHook,
              "a caller resolver must land behind a floor");
            assertSame(caller, ((FallbackIgnoreXMLResolver) fake.resolverHook).getDelegate(), "the floor must delegate to the caller's resolver");
            assertSame(caller, secure.getXMLResolver(), "getXMLResolver must report the caller's resolver unwrapped");
        }
    }

    @Test
    void setPropertyWrongTypeForResolverHookReachesDelegate() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final Object wrongType = "not a resolver";
        secure.setProperty(XMLInputFactory.RESOLVER, wrongType);
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("setProperty", XMLInputFactory.RESOLVER, wrongType)),
                "a wrong-typed value must reach the delegate so it can reject it");
    }

    @Test
    void setPropertyWrongTypeForResolverHookSurfacesDelegateException() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        assertThrows(ClassCastException.class, () -> factory.setProperty(XMLInputFactory.RESOLVER, "not a resolver"),
                "the delegate must surface its own rejection of a wrong-typed resolver");
    }

    @Test
    void settingAResolverInstallsAFreshFloorInsteadOfMutatingTheInstalledOne() {
        // The implementations copy the floor reference into every reader they create, so mutating the installed floor would change the resolution policy of
        // readers created before the call, including ones already parsing. Replacing it leaves what those readers captured alone.
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final Object captured = fake.resolverHook;
        secure.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
        assertNotSame(captured, fake.resolverHook, "setting a resolver must install a fresh floor, not re-delegate the one already on the hook");
        assertNull(((FallbackIgnoreXMLResolver) captured).getDelegate(), "the floor an existing reader captured must keep resolving to empty");
    }

    @Test
    void setXMLResolverNullClearsCallerDelegate() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final FallbackIgnoreXMLResolver floor = (FallbackIgnoreXMLResolver) fake.resolverHook;
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        secure.setXMLResolver(caller);
        secure.setXMLResolver(null);
        assertNull(floor.getDelegate(), "a null caller resolver must clear the floor's delegate");
        assertNull(secure.getXMLResolver(), "getXMLResolver must report no caller resolver");
    }

    @Test
    void setXMLResolverRoutesCallerBehindInstalledFloor() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        secure.setXMLResolver(caller);
        // The hook keeps a floor with the caller behind it; whether that is the floor already there or a fresh one is the subject of
        // settingAResolverInstallsAFreshFloorInsteadOfMutatingTheInstalledOne.
        assertInstanceOf(FallbackIgnoreXMLResolver.class, fake.resolverHook,
          "a caller resolver must land behind a floor, not replace it on the delegate's hook");
        assertSame(caller, ((FallbackIgnoreXMLResolver) fake.resolverHook).getDelegate(), "the caller's resolver must be the floor's delegate");
        assertSame(caller, secure.getXMLResolver(), "getXMLResolver must report the caller's resolver unwrapped");
        assertSame(caller, secure.getProperty(XMLInputFactory.RESOLVER), "getProperty must report the caller's resolver unwrapped");
    }

    @Test
    void unsupportedResolverHookSurfacesDelegateError() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        Assumptions.assumeFalse(factory.isPropertySupported(SecureXMLInputFactory.WSTX_DTD_RESOLVER), "requires an implementation without the Woodstox hooks");
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        assertThrows(IllegalArgumentException.class, () -> factory.setProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER, caller),
                "the delegate must surface its own rejection of an unknown resolver hook");
        assertThrows(IllegalArgumentException.class, () -> factory.getProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER),
                "the delegate must surface its own rejection of an unknown resolver hook");
    }

    @Test
    void woodstoxDtdHookRoutesBehindInstalledFloor() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        Assumptions.assumeTrue(factory.isPropertySupported(SecureXMLInputFactory.WSTX_DTD_RESOLVER), "requires the Woodstox DTD resolver hook");
        final XMLResolver first = (publicID, systemID, baseURI, namespace) -> null;
        factory.setProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER, first);
        assertSame(first, factory.getProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER), "the Woodstox hook must report the caller's resolver unwrapped");
        final XMLResolver second = (publicID, systemID, baseURI, namespace) -> "resolved";
        factory.setProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER, second);
        assertSame(second, factory.getProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER), "a second caller resolver must replace the first behind the floor");
    }

    @Test
    void woodstoxResolverHooksStayIndependent() {
        // Woodstox routes setXMLResolver to both its DTD-subset and entity hooks, so one floor object sits on several of them. Setting one hook must not
        // answer the others, which it would if the shared floor were mutated in place.
        final XMLInputFactory secure = SecureXMLInputFactory.newInstance();
        final XMLResolver dtd = (publicID, systemID, baseURI, namespace) -> null;
        try {
            secure.setProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER, dtd);
        } catch (final IllegalArgumentException notWoodstox) {
            Assumptions.abort("the implementation does not support " + SecureXMLInputFactory.WSTX_DTD_RESOLVER);
            return;
        }
        assertSame(dtd, secure.getProperty(SecureXMLInputFactory.WSTX_DTD_RESOLVER), "the hook the caller named must report their resolver");
        assertNull(secure.getProperty(SecureXMLInputFactory.WSTX_ENTITY_RESOLVER), "a resolver set on the DTD hook must not answer the entity hook");
    }

    @Test
    void woodstoxUndeclaredEntityHookWrapsCallerResolver() {
        final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
        Assumptions.assumeTrue(factory.isPropertySupported(SecureXMLInputFactory.WSTX_UNDECLARED_ENTITY_RESOLVER),
                "requires the Woodstox undeclared-entity resolver hook");
        final XMLResolver caller = (publicID, systemID, baseURI, namespace) -> null;
        factory.setProperty(SecureXMLInputFactory.WSTX_UNDECLARED_ENTITY_RESOLVER, caller);
        assertSame(caller, factory.getProperty(SecureXMLInputFactory.WSTX_UNDECLARED_ENTITY_RESOLVER),
                "the Woodstox hook must report the caller's resolver unwrapped");
    }

    @Test
    void wrapperDelegatesAllocatorAndReporter() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLEventAllocator allocator = RecordingXMLInputFactory.ALLOCATOR_SENTINEL;
        secure.setEventAllocator(allocator);
        assertSame(allocator, secure.getEventAllocator(), "the allocator must round-trip through the delegate");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("setEventAllocator", allocator)), "the exact allocator must be forwarded");
        final XMLReporter reporter = RecordingXMLInputFactory.REPORTER_SENTINEL;
        secure.setXMLReporter(reporter);
        assertSame(reporter, secure.getXMLReporter(), "the reporter must round-trip through the delegate");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("setXMLReporter", reporter)), "the exact reporter must be forwarded");
    }

    @Test
    void wrapperDelegatesIsPropertySupported() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        assertTrue(secure.isPropertySupported(XMLInputFactory.SUPPORT_DTD), "the delegate's answer must be reported");
        fake.supported = false;
        assertFalse(secure.isPropertySupported(XMLInputFactory.SUPPORT_DTD), "the delegate's answer must be reported");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("isPropertySupported", XMLInputFactory.SUPPORT_DTD)),
                "the exact property name must be forwarded");
    }

    @Test
    void wrapperDelegatesNonResolverProperties() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        secure.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("setProperty", XMLInputFactory.SUPPORT_DTD, Boolean.TRUE)),
                "a non-resolver property must reach the delegate unmodified");
        fake.resolverHook = Boolean.TRUE;
        assertEquals(Boolean.TRUE, secure.getProperty(XMLInputFactory.SUPPORT_DTD), "a non-resolver property must be reported unmodified");
    }

    @Test
    void wrapperDelegatesReaderCreationToDelegate() throws Exception {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        final XMLStreamReader streamSentinel = RecordingXMLInputFactory.STREAM_SENTINEL;
        final XMLEventReader eventSentinel = RecordingXMLInputFactory.EVENT_SENTINEL;
        final EventFilter eventFilter = RecordingXMLInputFactory.EVENT_FILTER_SENTINEL;
        final StreamFilter streamFilter = RecordingXMLInputFactory.STREAM_FILTER_SENTINEL;
        final InputStream stream = new ByteArrayInputStream(BENIGN_XML.getBytes(StandardCharsets.UTF_8));
        final StringReader reader = new StringReader(BENIGN_XML);
        final Source source = new StreamSource(new StringReader(BENIGN_XML));
        assertSame(streamSentinel, secure.createXMLStreamReader(stream));
        assertSame(streamSentinel, secure.createXMLStreamReader(stream, "UTF-8"));
        assertSame(streamSentinel, secure.createXMLStreamReader(reader));
        assertSame(streamSentinel, secure.createXMLStreamReader(source));
        assertSame(streamSentinel, secure.createXMLStreamReader(SYSTEM_ID, stream));
        assertSame(streamSentinel, secure.createXMLStreamReader(SYSTEM_ID, reader));
        assertSame(eventSentinel, secure.createXMLEventReader(stream));
        assertSame(eventSentinel, secure.createXMLEventReader(stream, "UTF-8"));
        assertSame(eventSentinel, secure.createXMLEventReader(reader));
        assertSame(eventSentinel, secure.createXMLEventReader(source));
        assertSame(eventSentinel, secure.createXMLEventReader(SYSTEM_ID, stream));
        assertSame(eventSentinel, secure.createXMLEventReader(SYSTEM_ID, reader));
        assertSame(eventSentinel, secure.createXMLEventReader(streamSentinel));
        assertSame(eventSentinel, secure.createFilteredReader(eventSentinel, eventFilter));
        assertSame(streamSentinel, secure.createFilteredReader(streamSentinel, streamFilter));
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createXMLStreamReader", stream)), "the exact stream must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createXMLStreamReader", reader)), "the exact reader must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createXMLStreamReader", source)), "the exact source must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createXMLStreamReader", SYSTEM_ID, stream)), "the exact system id must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createXMLEventReader", stream, "UTF-8")), "the exact encoding must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createFilteredReader", eventSentinel, eventFilter)),
                "the exact event filter must be forwarded");
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("createFilteredReader", streamSentinel, streamFilter)),
                "the exact stream filter must be forwarded");
    }

    @Test
    void wrapperInstallsFloorOnDelegateHook() {
        final RecordingXMLInputFactory fake = new RecordingXMLInputFactory();
        final XMLInputFactory secure = SecureXMLInputFactory.secure(fake);
        assertNotNull(secure);
        assertTrue(fake.calls.contains(RecordingXMLInputFactory.call("setXMLResolver", fake.resolverHook)),
                "the floor must be installed through the delegate's setXMLResolver");
        assertInstanceOf(FallbackIgnoreXMLResolver.class, fake.resolverHook,
          "the constructor must install the ignore-all floor on the delegate's resolver hook");
        assertNull(((FallbackIgnoreXMLResolver) fake.resolverHook).getDelegate(), "the installed floor must have no caller delegate");
    }
}
