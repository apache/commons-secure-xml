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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.EntityResolver2;

class FallbackIgnoreEntityResolver2Test {

    @Test
    void resolvesDelegatesAndAllFallbackPaths() throws Exception {
        final FallbackIgnoreEntityResolver2 floor = new FallbackIgnoreEntityResolver2(null);
        assertEquals("https://example.test/base/entity.dtd", floor.resolveEntity("name", "public", "https://example.test/base/", "entity.dtd").getSystemId());
        assertEquals("entity.dtd", floor.resolveEntity("name", "public", "not a URI", "entity.dtd").getSystemId());
        assertNotNull(floor.resolveEntity("public", null));
        final InputSource expected = new InputSource();
        final EntityResolver plain = (publicId, systemId) -> expected;
        floor.setDelegate(plain);
        assertSame(expected, floor.resolveEntity("name", "public", "https://example.test/base/", "entity.dtd"));
        final EntityResolver2 extended = new DefaultHandler2() {

            @Override
            public InputSource resolveEntity(final String name, final String publicId, final String base, final String system) {
                return expected;
            }
        };
        floor.setDelegate(extended);
        assertSame(expected, floor.resolveEntity("name", "public", "base", "system"));
        floor.setDelegate(null);
        System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
        try {
            assertThrows(SAXException.class, () -> floor.resolveEntity("p", "s"));
        } finally {
            System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
        }
    }
}
