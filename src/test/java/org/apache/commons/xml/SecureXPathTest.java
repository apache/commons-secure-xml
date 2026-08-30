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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.Collections;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class SecureXPathTest {

    @Test
    void delegatesEveryXPathMethod() throws Exception {
        final SecureXPath xpath = new SecureXPath(XPathFactory.newInstance().newXPath(), false);
        final NamespaceContext context = new NamespaceContext() {

            @Override
            public String getNamespaceURI(final String prefix) {
                return XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(final String namespaceUri) {
                return null;
            }

            @Override
            public java.util.Iterator<String> getPrefixes(final String namespaceUri) {
                return Collections.<String>emptyList().iterator();
            }
        };
        xpath.setNamespaceContext(context);
        xpath.setXPathFunctionResolver((name, arity) -> null);
        xpath.setXPathVariableResolver(name -> null);
        assertNotNull(xpath.getNamespaceContext());
        assertNotNull(xpath.getXPathFunctionResolver());
        assertNotNull(xpath.getXPathVariableResolver());
        assertNotNull(xpath.compile("/root"));
        assertEquals("value", xpath.evaluate("/root/text()", new InputSource(new StringReader("<root>value</root>"))));
        assertEquals("value", xpath.evaluate("/root/text()", new InputSource(new StringReader("<root>value</root>")), javax.xml.xpath.XPathConstants.STRING));
        assertEquals("value", xpath.evaluate("/root/text()", SecureXPath.parse(new InputSource(new StringReader("<root>value</root>")), false)));
        assertEquals("value", xpath.evaluate("/root/text()", SecureXPath.parse(new InputSource(new StringReader("<root>value</root>")), false),
                javax.xml.xpath.XPathConstants.STRING));
        xpath.reset();
    }

    @Test
    void preservesANullCompiledExpressionFromTheDelegate() throws Exception {
        final javax.xml.xpath.XPath delegate = new javax.xml.xpath.XPath() {

            @Override
            public javax.xml.xpath.XPathExpression compile(final String expression) {
                return null;
            }

            @Override
            public String evaluate(final String expression, final InputSource source) {
                return null;
            }

            @Override
            public Object evaluate(final String expression, final InputSource source, final javax.xml.namespace.QName returnType) {
                return null;
            }

            @Override
            public String evaluate(final String expression, final Object item) {
                return null;
            }

            @Override
            public Object evaluate(final String expression, final Object item, final javax.xml.namespace.QName returnType) {
                return null;
            }

            @Override
            public NamespaceContext getNamespaceContext() {
                return null;
            }

            @Override
            public javax.xml.xpath.XPathFunctionResolver getXPathFunctionResolver() {
                return null;
            }

            @Override
            public javax.xml.xpath.XPathVariableResolver getXPathVariableResolver() {
                return null;
            }

            @Override
            public void reset() {
            }

            @Override
            public void setNamespaceContext(final NamespaceContext context) {
            }

            @Override
            public void setXPathFunctionResolver(final javax.xml.xpath.XPathFunctionResolver resolver) {
            }

            @Override
            public void setXPathVariableResolver(final javax.xml.xpath.XPathVariableResolver resolver) {
            }
        };
        assertNull(new SecureXPath(delegate, false).compile("/root"));
    }

    @Test
    void wrapsParseFailuresAsXPathExpressionExceptions() {
        final javax.xml.xpath.XPathExpressionException exception = assertThrows(javax.xml.xpath.XPathExpressionException.class,
                () -> SecureXPath.parse(new InputSource(new StringReader("<root>")), false));
        assertNotNull(exception.getCause());
    }
}
