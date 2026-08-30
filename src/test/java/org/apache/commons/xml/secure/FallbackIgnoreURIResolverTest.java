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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.junit.jupiter.api.Test;

class FallbackIgnoreURIResolverTest {

    @Test
    void resolvesDelegatedAndFallbackSources() throws Exception {
        final DOMSource empty = new DOMSource();
        final FallbackIgnoreURIResolver resolver = new FallbackIgnoreURIResolver(null, () -> empty, () -> false);
        assertSame(empty, resolver.resolve("href", "base"));
        final DOMSource delegated = new DOMSource();
        final URIResolver delegate = (href, base) -> delegated;
        resolver.setDelegate(delegate);
        assertSame(delegate, resolver.getDelegate());
        assertSame(delegated, resolver.resolve("href", "base"));
        resolver.setDelegate(null);
        System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
        try {
            assertThrows(TransformerException.class, () -> resolver.resolve("href", "base"));
        } finally {
            System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
        }
    }
}
