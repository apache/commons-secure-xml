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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * MethodHandle utility.
 */
final class MethodHandleFactory {

    @FunctionalInterface
    interface ThrowableCallable<V> {

        V call() throws Throwable;
    }

    /**
     * Finds a static method handle for the given class, method name, where the class is also the return type.
     *
     * @param refcAndReturnType the class to search for the method and the return type
     * @param name              the name of the method
     * @return the method handle, or {@code null} if not found
     * @throws NullPointerException thrown if any argument is null
     * @throws SecurityException    thrown if a security manager is present and it <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     */
    static MethodHandle findStatic(final Class<?> refcAndReturnType, final String name) {
        try {
            return MethodHandles.publicLookup().findStatic(refcAndReturnType, name, MethodType.methodType(refcAndReturnType));
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Finds an instance method handle for the given class, method name and signature.
     * <p>
     * Used to reach a method a later Java release added to a class this library compiles against an earlier one; the handle is {@code null} where the
     * running platform does not have it.
     * </p>
     *
     * @param refc           the class to search for the method
     * @param name           the name of the method
     * @param returnType     the method's return type
     * @param parameterTypes the method's parameter types
     * @return the method handle, or {@code null} if not found
     * @throws NullPointerException thrown if any argument is null
     * @throws SecurityException    thrown if a security manager is present and it <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     */
    static MethodHandle findVirtual(final Class<?> refc, final String name, final Class<?> returnType, final Class<?>... parameterTypes) {
        try {
            return MethodHandles.publicLookup().findVirtual(refc, name, MethodType.methodType(returnType, parameterTypes));
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    static <T, E extends Throwable> T invokeExact(final ThrowableCallable<T> methodHandle, final Class<E> rethrow) throws E {
        try {
            return methodHandle.call();
        } catch (final Throwable e) {
            if (rethrow.isInstance(e)) {
                throw rethrow.cast(e);
            }
            if (e instanceof Error) {
                // A JVM error (OutOfMemoryError, ...) must keep its type; only exceptions are wrapped.
                throw (Error) e;
            }
            // The looked-up method declares no checked exceptions besides rethrow's type, so this wraps runtime exceptions only.
            throw new IllegalStateException(e);
        }
    }

    private MethodHandleFactory() {
        // Prevent instantiation.
    }
}
