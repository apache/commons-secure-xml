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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("xpath")
class SecureXPathFactoryTest {

    @Test
    void createsAndConfiguresAFactoryForTheDefaultObjectModel() throws Exception {
        final XPathFactory factory = SecureXPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI);
        final XPathFunctionResolver resolver = (name, arity) -> null;
        factory.setXPathFunctionResolver(resolver);
        final XPathVariableResolver variableResolver = name -> null;
        factory.setXPathVariableResolver(variableResolver);
        assertTrue(factory.isObjectModelSupported(XPathFactory.DEFAULT_OBJECT_MODEL_URI));
        final SecureXPath xpath = (SecureXPath) factory.newXPath();
        assertSame(resolver, xpath.getXPathFunctionResolver());
        assertSame(variableResolver, xpath.getXPathVariableResolver());
    }

    @Test
    void createsSecureXPathFromStaticEntryPoints() {
        assertInstanceOf(SecureXPath.class, SecureXPathFactory.newInstance().newXPath());
        assertInstanceOf(SecureXPath.class, SecureXPathFactory.newDefaultInstance().newXPath());
    }

    @Test
    void preservesANullXPathFromTheDelegate() {
        final XPathFactory delegate = new XPathFactory() {

            @Override
            public boolean getFeature(final String name) {
                return false;
            }

            @Override
            public boolean isObjectModelSupported(final String objectModel) {
                return true;
            }

            @Override
            public XPath newXPath() {
                return null;
            }

            @Override
            public void setFeature(final String name, final boolean value) {
            }

            @Override
            public void setXPathFunctionResolver(final XPathFunctionResolver resolver) {
            }

            @Override
            public void setXPathVariableResolver(final XPathVariableResolver resolver) {
            }
        };
        assertNull(SecureXPathFactory.secure(delegate).newXPath());
    }

    @Test
    void wrapsARejectedRequiredFeatureInSecureException() {
        final XPathFactory rejectingFactory = new XPathFactory() {

            @Override
            public boolean getFeature(final String name) {
                return false;
            }

            @Override
            public boolean isObjectModelSupported(final String objectModel) {
                return true;
            }

            @Override
            public XPath newXPath() {
                return null;
            }

            @Override
            public void setFeature(final String name, final boolean value) throws XPathFactoryConfigurationException {
                throw new XPathFactoryConfigurationException(name);
            }

            @Override
            public void setXPathFunctionResolver(final XPathFunctionResolver resolver) {
            }

            @Override
            public void setXPathVariableResolver(final XPathVariableResolver resolver) {
            }
        };
        assertThrows(SecureException.class, () -> SecureXPathFactory.secure(rejectingFactory));
    }
}
