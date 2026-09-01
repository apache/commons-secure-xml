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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MethodHandleFactory}, the reflective lookup helper behind {@link SecureXMLInputFactory#newDefaultFactory()}.
 * <p>
 * The helper is exercised directly, including the miss and rethrow paths that no standard JVM factory lookup reaches, so the platform-specific fallback logic
 * of the secure factory has full branch coverage.
 * </p>
 */
class MethodHandleFactoryTest {

    @Test
    void findStaticReturnsHandleForExistingMethod() {
        final MethodHandle handle = MethodHandleFactory.findStatic(XMLInputFactory.class, "newInstance");
        assertNotNull(handle, "an existing static method must resolve to a handle");
    }

    @Test
    void findStaticReturnsNullForMissingMethod() {
        assertNull(MethodHandleFactory.findStatic(XMLInputFactory.class, "noSuchMethod"),
                "a missing method must resolve to null");
    }

    @Test
    void invokeExactRethrowsDeclaredException() {
        final FactoryConfigurationError declared = new FactoryConfigurationError("boom");
        final FactoryConfigurationError thrown = assertThrows(FactoryConfigurationError.class, () -> MethodHandleFactory.invokeExact(() -> {
            throw declared;
        }, FactoryConfigurationError.class), "an exception of the declared type must be rethrown");
        assertSame(declared, thrown, "the declared exception must propagate unchanged");
    }

    @Test
    void invokeExactRethrowsUndeclaredError() {
        final OutOfMemoryError error = new OutOfMemoryError("boom");
        final OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class, () -> MethodHandleFactory.invokeExact(() -> {
            throw error;
        }, FactoryConfigurationError.class), "a JVM error must keep its type");
        assertSame(error, thrown, "the error must propagate unchanged");
    }

    @Test
    void invokeExactReturnsValue() {
        assertEquals("value", MethodHandleFactory.invokeExact(() -> "value", IllegalStateException.class), "the callable's value must be returned");
    }

    @Test
    void invokeExactWrapsUnexpectedException() {
        final IllegalStateException wrapped = assertThrows(IllegalStateException.class, () -> MethodHandleFactory.invokeExact(() -> {
            throw new IllegalArgumentException("boom");
        }, FactoryConfigurationError.class), "an undeclared exception must be wrapped");
        assertInstanceOf(IllegalArgumentException.class, wrapped.getCause(), "the unexpected exception must be the wrapper's cause");
    }
}
