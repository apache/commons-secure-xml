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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

import org.junit.jupiter.api.Assumptions;
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

    /** A processing limit the JDK's XPath implementation recognizes through the Java 18 property API. */
    private static final String XPATH_GROUP_LIMIT = "jdk.xml.xpathExprGrpLimit";

    /**
     * The Java 18 {@code XPathFactory} property method of the given name, or an aborted test where the platform predates it.
     *
     * <p>Reached reflectively because this suite compiles against the Java 8 API, the same reason the wrapper delegates the pair through method handles: the
     * call has to resolve at run time, which is also exactly how a Java 18 caller reaches it.</p>
     */
    private static Method propertyMethod(final String name, final Class<?>... parameterTypes) {
        try {
            return XPathFactory.class.getMethod(name, parameterTypes);
        } catch (final NoSuchMethodException e) {
            Assumptions.abort("XPathFactory." + name + " requires Java 18 or later");
            throw new AssertionError("unreachable");
        }
    }

    @Test
    void delegatesTheJava18PropertyApi() throws Exception {
        // The wrapper is compiled against the Java 8 API, so without an explicit delegation the inherited default answers for it and every property the
        // implementation supports, including its own limits, becomes unreachable through a secured factory.
        final Method setProperty = propertyMethod("setProperty", String.class, String.class);
        final Method getProperty = propertyMethod("getProperty", String.class);
        final XPathFactory factory = SecureXPathFactory.newDefaultInstance();
        setProperty.invoke(factory, XPATH_GROUP_LIMIT, "5");
        assertEquals("5", getProperty.invoke(factory, XPATH_GROUP_LIMIT), "a property set on the secured factory must be read back from the delegate");
    }

    @Test
    void reportsAnUnknownPropertyLikeTheDelegate() {
        final Method getProperty = propertyMethod("getProperty", String.class);
        final XPathFactory factory = SecureXPathFactory.newDefaultInstance();
        final InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> getProperty.invoke(factory, "jdk.xml.noSuchProperty"));
        assertInstanceOf(IllegalArgumentException.class, thrown.getCause(), "an unrecognized property must surface the delegate's own rejection");
    }
}
