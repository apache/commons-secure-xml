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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests resolver initialization using fresh JVM class definitions and the JDK DOM implementation.
 *
 * <p>
 * Excluded from Android compilation because Android does not support this JVM class-loading mechanism.
 * </p>
 */
@DisabledInNativeImage
class FallbackIgnoreLSResourceResolverInitializationTest {

    private static void checkInitialization(final boolean failRetry) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader original = thread.getContextClassLoader();
        final String source = failRetry ? "missing.DOMImplementationSource" : "com.sun.org.apache.xerces.internal.dom.DOMXSImplementationSourceImpl";
        final ClassNotFoundException firstFailure = new ClassNotFoundException("Rejected by test TCCL");
        final ClassLoader restricted = new ClassLoader(original) {

            @Override
            protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                if (source.equals(name)) {
                    throw firstFailure;
                }
                return super.loadClass(name, resolve);
            }
        };
        final String previous = System.setProperty(DOMImplementationRegistry.PROPERTY, source);
        thread.setContextClassLoader(restricted);
        try {
            if (failRetry) {
                final ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class,
                        FallbackIgnoreLSResourceResolverInitializationTest::initializeResolver);
                final Throwable retry = error.getCause();
                assertInstanceOf(ClassNotFoundException.class, retry);
                assertEquals(1, retry.getSuppressed().length);
                assertSame(firstFailure, retry.getSuppressed()[0]);
            } else {
                final Field domLs = initializeResolver().getDeclaredField("DOM_LS");
                domLs.setAccessible(true);
                assertNotNull(domLs.get(null));
            }
            assertSame(restricted, thread.getContextClassLoader(), "Initialization must restore the caller's TCCL");
        } finally {
            thread.setContextClassLoader(original);
            if (previous == null) {
                System.clearProperty(DOMImplementationRegistry.PROPERTY);
            } else {
                System.setProperty(DOMImplementationRegistry.PROPERTY, previous);
            }
        }
    }

    /**
     * Defines only the resolver in a fresh loader, so each test executes its static initializer independently.
     */
    private static Class<?> initializeResolver() throws Exception {
        final String name = FallbackIgnoreLSResourceResolver.class.getName();
        final byte[] bytes;
        try (InputStream input = FallbackIgnoreLSResourceResolver.class.getResourceAsStream("FallbackIgnoreLSResourceResolver.class");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertNotNull(input);
            final byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        final ClassLoader isolated = new ClassLoader(FallbackIgnoreLSResourceResolver.class.getClassLoader()) {

            @Override
            protected Class<?> loadClass(final String className, final boolean resolve) throws ClassNotFoundException {
                if (!name.equals(className)) {
                    return super.loadClass(className, resolve);
                }
                synchronized (getClassLoadingLock(className)) {
                    Class<?> result = findLoadedClass(className);
                    if (result == null) {
                        result = defineClass(className, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(result);
                    }
                    return result;
                }
            }
        };
        return Class.forName(name, true, isolated);
    }

    @Test
    void initializationRestoresContextLoaderAfterRetry() throws Exception {
        checkInitialization(false);
    }

    @Test
    void initializationRetainsBothFailuresAndRestoresContextLoader() throws Exception {
        checkInitialization(true);
    }
}
