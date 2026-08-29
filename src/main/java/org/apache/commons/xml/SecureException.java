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

/**
 * Thrown when a factory cannot be made secure.
 *
 * <p>Three failure modes share this type:</p>
 * <ul>
 *   <li>No bundled secure recipe matches the concrete factory class.</li>
 *   <li>A recipe tried to apply a secure setting and the implementation rejected it.</li>
 *   <li>The implementation could not provide the internal secure reader the Source-rewriting wrappers parse with.</li>
 * </ul>
 *
 * <p>The message names the unsupported factory class or the specific feature, attribute or property that failed; the cause, when present, is the original
 * checked or unchecked exception from the JAXP implementation.</p>
 *
 * <p>Package-private by design: callers should catch {@link IllegalStateException}, which this extends.</p>
 */
final class SecureException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * System property that switches unresolved external references from the default empty resolution to a thrown exception.
     * <p>
     * How to enable: set {@code -Dorg.apache.commons.xml.throwOnUnresolved=true}. The property is read at resolution time, so it also applies to factories
     * created before it was set; references resolved by a caller-supplied resolver are unaffected.
     * </p>
     */
    static final String THROW_ON_UNRESOLVED = "org.apache.commons.xml.throwOnUnresolved";

    /**
     * Builds the standard exception for a rejected secure setting.
     * @param name   the name of the feature, attribute or property that could not be set.
     * @param target the factory, parser, validator or reader that rejected the setting; its concrete class names the offending implementation.
     * @param cause  the original checked or unchecked exception from the JAXP implementation.
     *
     * @return the exception to throw.
     */
    static SecureException featureFailed(final String name, final Object target, final Throwable cause) {
        return new SecureException("Failed to set feature '" + name + "' on " + target.getClass().getName(), cause);
    }

    /**
     * Builds the standard "forbidden" message shared by every resolver floor when {@link #throwOnUnresolved()} rejects an unresolved reference.
     *
     * @param type      the resource kind, or {@code null} if not applicable.
     * @param namespace the namespace (or, for Woodstox, the entity name), or {@code null}.
     * @param publicId  the public identifier, or {@code null} if none.
     * @param systemId  the system identifier of the denied resource.
     * @param baseURI   the base URI for relative resolution, or {@code null}.
     * @return the message naming the denied lookup and the enabling property.
     */
    static String forbidden(final String type, final String namespace, final String publicId, final String systemId, final String baseURI) {
        return String.format("External resource fetch forbidden by %s: type=%s, namespace=%s, publicId=%s, systemId=%s, baseURI=%s",
                SecureException.THROW_ON_UNRESOLVED, type, namespace, publicId, systemId, baseURI);
    }

    /**
     * Builds the standard exception for a failed internal reader provisioning.
     *
     * <p>Every supported implementation provides a reader as a routine capability, so the wrapped {@code ParserConfigurationException} or
     * {@code SAXException} signals a broken environment, not a per-parse condition — hence unchecked.</p>
     *
     * @param cause the original checked exception from the JAXP implementation.
     * @return the exception to throw.
     */
    static SecureException readerFailed(final Throwable cause) {
        return new SecureException("Failed to create a secure XMLReader", cause);
    }

    /**
     * Whether unresolved external references must be rejected instead of resolved to empty content.
     *
     * <p>Read per resolution, so the {@value SecureException#THROW_ON_UNRESOLVED} system property also toggles factories that already exist.</p>
     *
     * @return {@code true} when the {@value SecureException#THROW_ON_UNRESOLVED} system property is set.
     */
    static boolean throwOnUnresolved() {
        return Boolean.getBoolean(SecureException.THROW_ON_UNRESOLVED);
    }

    SecureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
