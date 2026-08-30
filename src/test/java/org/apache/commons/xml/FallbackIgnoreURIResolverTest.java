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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class FallbackIgnoreURIResolverTest {

    @Test
    void resolvesDelegatedAndFallbackSources() throws Exception {
        final javax.xml.transform.dom.DOMSource empty = new javax.xml.transform.dom.DOMSource();
        final FallbackIgnoreURIResolver resolver = new FallbackIgnoreURIResolver(null, () -> empty, () -> false);
        assertSame(empty, resolver.resolve("href", "base"));
        final javax.xml.transform.dom.DOMSource delegated = new javax.xml.transform.dom.DOMSource();
        final javax.xml.transform.URIResolver delegate = (href, base) -> delegated;
        resolver.setDelegate(delegate);
        assertSame(delegate, resolver.getDelegate());
        assertSame(delegated, resolver.resolve("href", "base"));
        resolver.setDelegate(null);
        System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
        try {
            assertThrows(javax.xml.transform.TransformerException.class, () -> resolver.resolve("href", "base"));
        } finally {
            System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
        }
    }
}
