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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.XMLFilterImpl;

class SaxonProviderTest {

    /** Reader used to force SecureConfiguration.makeParser through its SecureException translation path. */
    public static final class FailingXMLReader extends XMLFilterImpl {

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotSupportedException {
            throw new SAXNotSupportedException(name);
        }
    }

    private static Class<?> loadSaxon(final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newSaxon(final String className) throws ReflectiveOperationException {
        return loadSaxon(className).getConstructor().newInstance();
    }

    @Test
    @Tag("xpath3")
    void configuresSaxonFactoriesAndSuppliesAnEmptySource() throws ReflectiveOperationException {
        final TransformerFactory transformerFactory = TransformerFactory.class.cast(newSaxon("net.sf.saxon.TransformerFactoryImpl"));
        final XPathFactory xpathFactory = XPathFactory.class.cast(newSaxon("net.sf.saxon.xpath.XPathFactoryImpl"));
        assertSame(transformerFactory, SaxonProvider.configure(transformerFactory));
        assertSame(xpathFactory, SaxonProvider.configure(xpathFactory));
        assertEquals("net.sf.saxon.lib.EmptySource", SaxonProvider.emptySourceSupplier().get().getClass().getName());
    }

    @Test
    void recognizesNonSaxonClass() {
        assertFalse(SaxonProvider.isSaxon(getClass()));
    }

    @Test
    @Tag("xpath3")
    void recognizesOpenSourceAndCommercialSaxonClasses() {
        assertTrue(SaxonProvider.isSaxon(loadSaxon("net.sf.saxon.TransformerFactoryImpl")));
        assertTrue(SaxonProvider.isSaxon(com.saxonica.ProviderMarker.class));
    }

    @Test
    @Tag("xpath3")
    void rejectsFactoriesThatDoNotImplementSaxonApis() {
        assertThrows(SecureException.class,
                () -> SaxonProvider.configure(TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl", null)));
        assertThrows(SecureException.class, () -> SaxonProvider
                .configure(XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null)));
    }

    @Test
    @Tag("xpath3")
    void rejectsSaxonCollectionResolutionWhenConfiguredToThrow() throws Exception {
        final XPathFactory factory = XPathFactory.class.cast(newSaxon("net.sf.saxon.xpath.XPathFactoryImpl"));
        SaxonProvider.configure(factory);
        final Object configuration = factory.getClass().getMethod("getConfiguration").invoke(factory);
        final Object finder = configuration.getClass().getMethod("getCollectionFinder").invoke(configuration);
        final Method findCollection = finder.getClass().getMethod("findCollection", loadSaxon("net.sf.saxon.expr.XPathContext"),
                String.class);
        final String previous = System.getProperty(SecureException.THROW_ON_UNRESOLVED);
        try {
            System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
            final InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> findCollection.invoke(finder, null, "urn:collection"));
            assertEquals("net.sf.saxon.trans.XPathException", exception.getCause().getClass().getName());
        } finally {
            if (previous == null) {
                System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
            } else {
                System.setProperty(SecureException.THROW_ON_UNRESOLVED, previous);
            }
        }
    }

    @Test
    @Tag("xpath3")
    void translatesSecureParserFailuresToSaxonConfigurationErrors() throws Exception {
        final TransformerFactory factory = TransformerFactory.class.cast(newSaxon("net.sf.saxon.TransformerFactoryImpl"));
        SaxonProvider.configure(factory);
        final Object configuration = factory.getClass().getMethod("getConfiguration").invoke(factory);
        final Method makeParser = configuration.getClass().getMethod("makeParser", String.class);
        final InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> makeParser.invoke(configuration, FailingXMLReader.class.getName()));
        final TransformerFactoryConfigurationError error = assertInstanceOf(TransformerFactoryConfigurationError.class, exception.getCause());
        assertInstanceOf(SecureException.class, error.getException());
    }
}
