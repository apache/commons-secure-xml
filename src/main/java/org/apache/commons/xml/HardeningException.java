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

    HardeningException(final String message) {
        super(message);
    }

    HardeningException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
