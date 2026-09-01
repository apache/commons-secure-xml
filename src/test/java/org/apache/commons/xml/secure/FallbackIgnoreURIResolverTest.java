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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.w3c.dom.Document;

class FallbackIgnoreURIResolverTest {

    @Test
    // Mockito generates the mock class and its plugin proxies at run time — impossible in a closed-world native image,
    // so the stubbed factory this error path needs cannot be built there.
    @DisabledInNativeImage
    void newEmptyDocumentWrapsConfigurationFailure() throws Exception {
        final DocumentBuilderFactory factory = mock(DocumentBuilderFactory.class);
        final ParserConfigurationException failure = new ParserConfigurationException("test");
        when(factory.newDocumentBuilder()).thenThrow(failure);
        final Method method = FallbackIgnoreURIResolver.class.getDeclaredMethod("newEmptyDocument", DocumentBuilderFactory.class);
        method.setAccessible(true);
        final InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> method.invoke(null, factory));
        final IllegalStateException error = (IllegalStateException) exception.getCause();
        assertSame(failure, error.getCause());
    }

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

    @Test
    void resolvesFreshEmptyDocumentPerResolution() throws Exception {
        final FallbackIgnoreURIResolver resolver = new FallbackIgnoreURIResolver(null, null, () -> false);
        final Document first = (Document) ((DOMSource) resolver.resolve("href", "base")).getNode();
        assertNull(first.getDocumentElement());
        first.appendChild(first.createElement("planted"));
        final Document second = (Document) ((DOMSource) resolver.resolve("href", "base")).getNode();
        assertNotSame(first, second);
        assertNull(second.getDocumentElement());
    }
}
