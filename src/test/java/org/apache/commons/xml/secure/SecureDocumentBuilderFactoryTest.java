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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dom")
class SecureDocumentBuilderFactoryTest {

    /** System property naming the {@link DocumentBuilderFactory} implementation, the JVM's mechanism for reconfiguring the default parser. */
    private static final String FACTORY_ID = "javax.xml.parsers.DocumentBuilderFactory";

    /**
     * Gets the implementation a secure factory delegates to, so the selection tests can observe which parser implementation a lookup picked.
     *
     * @param factory a secure factory returned by one of the {@code new*Instance} methods; never {@code null}.
     * @return The wrapped factory.
     */
    private static DocumentBuilderFactory getDelegate(final DocumentBuilderFactory factory) throws ReflectiveOperationException {
        final Field delegate = factory.getClass().getDeclaredField("delegate");
        delegate.setAccessible(true);
        return (DocumentBuilderFactory) delegate.get(factory);
    }

    /**
     * Selects the implementation {@link DocumentBuilderFactory#newInstance()} returns by setting the {@value #FACTORY_ID} system property.
     *
     * @param factoryClassName The implementation class name to install, or {@code null} to clear the property and restore the platform lookup.
     * @return The previous property value, {@code null} if it was not set; pass it back here to restore the original lookup.
     */
    private static String setFactoryIdProperty(final String factoryClassName) {
        final String previous = System.getProperty(FACTORY_ID);
        if (factoryClassName == null) {
            System.clearProperty(FACTORY_ID);
        } else {
            System.setProperty(FACTORY_ID, factoryClassName);
        }
        return previous;
    }

    @Test
    void createsSecureBuildersFromEveryStaticEntryPoint() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "the platform DOM is left unwrapped: it does not resolve user-defined entities");
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newDefaultInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newNSInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder());
    }

    @Test
    void explicitFactoryClassSelectsThatImplementation() throws Exception {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: the platform factory is used unwrapped");
        final Class<?> discovered = DocumentBuilderFactory.newInstance().getClass();
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newNSInstance(discovered.getName(), null);
        assertEquals(discovered, getDelegate(factory).getClass());
        assertTrue(factory.isNamespaceAware());
    }

    @Test
    void forwardsEverySupportedFactoryConfiguration() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "the platform DOM is left unwrapped: it does not resolve user-defined entities");
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        factory.setCoalescing(true);
        factory.setExpandEntityReferences(false);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setSchema(null);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(TestConstants.JAXP_SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertEquals(XMLConstants.W3C_XML_SCHEMA_NS_URI, factory.getAttribute(TestConstants.JAXP_SCHEMA_LANGUAGE));
        assertTrue(factory.isCoalescing());
        assertFalse(factory.isExpandEntityReferences());
        assertTrue(factory.isIgnoringComments());
        assertTrue(factory.isIgnoringElementContentWhitespace());
        assertTrue(factory.isNamespaceAware());
        assertFalse(factory.isValidating());
        assertFalse(factory.isXIncludeAware());
        assertNull(factory.getSchema());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertNotNull(factory.newDocumentBuilder());
    }

    @Test
    void newNSInstanceFollowsParserSelection() throws Exception {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: the platform factory is used unwrapped");
        final Class<?> discovered = DocumentBuilderFactory.newInstance().getClass();
        // no property: the JDK built-in default, unless an override is requested
        assertEquals(SecureDocumentBuilderFactory.JDK_DOCUMENT_BUILDER_FACTORY,
                getDelegate(SecureDocumentBuilderFactory.newNSInstance(false)).getClass().getName());
        assertEquals(discovered, getDelegate(SecureDocumentBuilderFactory.newNSInstance(true)).getClass());
        // the factory id property is the JDK's own default reconfiguration; both selections honor it
        final String previous = setFactoryIdProperty(discovered.getName());
        try {
            assertEquals(discovered, getDelegate(SecureDocumentBuilderFactory.newNSInstance(false)).getClass());
            assertEquals(discovered, getDelegate(SecureDocumentBuilderFactory.newNSInstance(true)).getClass());
        } finally {
            setFactoryIdProperty(previous);
        }
    }
}
