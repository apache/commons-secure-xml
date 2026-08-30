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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecureExceptionTest {

    @Test
    void formatsFailuresAndReadsUnresolvedProperty() {
        final RuntimeException cause = new RuntimeException("cause");
        assertSame(cause, SecureException.featureFailed("feature", this, cause).getCause());
        assertTrue(SecureException.forbidden("type", "namespace", "public", "system", "base").contains("system"));
        assertSame(cause, SecureException.readerFailed(cause).getCause());
        System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
        assertFalse(SecureException.throwOnUnresolved());
        System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
        try {
            assertTrue(SecureException.throwOnUnresolved());
        } finally {
            System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
        }
    }
}
