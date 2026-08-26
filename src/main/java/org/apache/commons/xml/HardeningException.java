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
 * Thrown when a factory cannot be hardened.
 *
 * <p>Two failure modes share this type:</p>
 * <ul>
 *   <li>No bundled hardening recipe matches the concrete factory class.</li>
 *   <li>A recipe tried to apply a hardening setting and the implementation rejected it.</li>
 * </ul>
 *
 * <p>The message names the unsupported factory class or the specific feature, attribute or property that failed; the cause, when present, is the original
 * checked or unchecked exception from the JAXP implementation.</p>
 *
 * <p>Package-private by design: callers should catch {@link IllegalStateException}, which this extends.</p>
 */
final class HardeningException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

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
                XmlFactories.THROW_ON_UNRESOLVED, type, namespace, publicId, systemId, baseURI);
    }

    /**
     * Builds the standard exception for a rejected hardening setting.
     *
     * @param kind   the kind of setting: {@code "feature"}, {@code "attribute"} or {@code "property"}.
     * @param name   the name of the feature, attribute or property that could not be set.
     * @param target the factory, parser, validator or reader that rejected the setting; its concrete class names the offending implementation.
     * @param cause  the original checked or unchecked exception from the JAXP implementation.
     * @return the exception to throw.
     */
    static HardeningException settingFailed(final String kind, final String name, final Object target, final Throwable cause) {
        return new HardeningException("Failed to set " + kind + " '" + name + "' on " + target.getClass().getName(), cause);
    }

    /**
     * Whether unresolved external references must be rejected instead of resolved to empty content.
     *
     * <p>Read per resolution, so the {@value XmlFactories#THROW_ON_UNRESOLVED} system property also toggles factories that already exist.</p>
     *
     * @return {@code true} when the {@value XmlFactories#THROW_ON_UNRESOLVED} system property is set.
     */
    static boolean throwOnUnresolved() {
        return Boolean.getBoolean(XmlFactories.THROW_ON_UNRESOLVED);
    }

    HardeningException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
