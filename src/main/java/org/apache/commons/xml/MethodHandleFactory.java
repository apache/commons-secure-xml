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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * MethodHandle utility.
 */
class MethodHandleFactory {

    @FunctionalInterface
    interface ThrowableCallable<V> {

        V call() throws Throwable;
    }

    /**
     * Finds a static method handle for the given class, method name, and method type.
     *
     * @param refc the class to search for the method.
     * @param name the name of the method.
     * @param type the method type.
     * @return the method handle, or {@code null} if not found.
     * @throws SecurityException    if a security manager is present and it <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>.
     * @throws NullPointerException if any argument is null.
     */
    static MethodHandle findStatic(final Class<?> refc, final String name, final MethodType type) {
        try {
            return MethodHandles.publicLookup().findStatic(refc, name, type);
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    static <T, E extends Throwable> T invokeExact(final ThrowableCallable<T> methodHandle, final Class<E> rethrow) throws E {
        try {
            return methodHandle.call();
        } catch (final Throwable e) {
            if (e.getClass().isInstance(rethrow)) {
                throw rethrow.cast(e);
            }
            // Unreachable: the looked-up method declares no other exceptions.
            throw new IllegalStateException(e);
        }
    }
}
