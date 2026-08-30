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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

class SecureDocumentBuilderFactoryTest {

    @Test
    void createsSecureBuildersFromEveryStaticEntryPoint() throws Exception {
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newDefaultInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newNSInstance().newDocumentBuilder());
        assertInstanceOf(SecureDocumentBuilder.class, SecureDocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder());
    }

    @Test
    void forwardsEverySupportedFactoryConfiguration() throws Exception {
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
        factory.setAttribute(TestConstants.ACCESS_EXTERNAL_DTD, "");
        assertTrue(factory.isCoalescing());
        assertFalse(factory.isExpandEntityReferences());
        assertTrue(factory.isIgnoringComments());
        assertTrue(factory.isIgnoringElementContentWhitespace());
        assertTrue(factory.isNamespaceAware());
        assertFalse(factory.isValidating());
        assertFalse(factory.isXIncludeAware());
        assertNull(factory.getSchema());
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertNotNull(factory.getAttribute(TestConstants.ACCESS_EXTERNAL_DTD));
        assertNotNull(factory.newDocumentBuilder());
    }

    @Test
    void honorsExplicitFactoryClassAndDefaultParserOverrides() throws Exception {
        final String className = DocumentBuilderFactory.newInstance().getClass().getName();
        assertTrue(SecureDocumentBuilderFactory.newNSInstance(className, null).isNamespaceAware());
        assertTrue(SecureDocumentBuilderFactory.newNSInstance(true).isNamespaceAware());
        final String property = "javax.xml.parsers.DocumentBuilderFactory";
        final String previous = System.getProperty(property);
        try {
            System.setProperty(property, className);
            assertTrue(SecureDocumentBuilderFactory.newNSInstance(false).isNamespaceAware());
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
