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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;

class SaxonProviderTest {

    /** Reader used to force SecureConfiguration.makeParser through its SecureException translation path. */
    public static final class FailingXMLReader extends org.xml.sax.helpers.XMLFilterImpl {

        @Override
        public void setFeature(final String name, final boolean value) throws org.xml.sax.SAXNotSupportedException {
            throw new org.xml.sax.SAXNotSupportedException(name);
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
    @org.junit.jupiter.api.Tag("xpath3")
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
    @org.junit.jupiter.api.Tag("xpath3")
    void recognizesOpenSourceAndCommercialSaxonClasses() {
        assertTrue(SaxonProvider.isSaxon(loadSaxon("net.sf.saxon.TransformerFactoryImpl")));
        assertTrue(SaxonProvider.isSaxon(com.saxonica.ProviderMarker.class));
    }

    @Test
    @org.junit.jupiter.api.Tag("xpath3")
    void rejectsFactoriesThatDoNotImplementSaxonApis() {
        org.junit.jupiter.api.Assertions.assertThrows(SecureException.class,
                () -> SaxonProvider.configure(TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl", null)));
        org.junit.jupiter.api.Assertions.assertThrows(SecureException.class, () -> SaxonProvider
                .configure(XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null)));
    }

    @Test
    @org.junit.jupiter.api.Tag("xpath3")
    void rejectsSaxonCollectionResolutionWhenConfiguredToThrow() throws Exception {
        final XPathFactory factory = XPathFactory.class.cast(newSaxon("net.sf.saxon.xpath.XPathFactoryImpl"));
        SaxonProvider.configure(factory);
        final Object configuration = factory.getClass().getMethod("getConfiguration").invoke(factory);
        final Object finder = configuration.getClass().getMethod("getCollectionFinder").invoke(configuration);
        final java.lang.reflect.Method findCollection = finder.getClass().getMethod("findCollection", loadSaxon("net.sf.saxon.expr.XPathContext"),
                String.class);
        final String previous = System.getProperty(SecureException.THROW_ON_UNRESOLVED);
        try {
            System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
            final java.lang.reflect.InvocationTargetException exception = org.junit.jupiter.api.Assertions
                    .assertThrows(java.lang.reflect.InvocationTargetException.class, () -> findCollection.invoke(finder, null, "urn:collection"));
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
    @org.junit.jupiter.api.Tag("xpath3")
    void translatesSecureParserFailuresToSaxonConfigurationErrors() throws Exception {
        final TransformerFactory factory = TransformerFactory.class.cast(newSaxon("net.sf.saxon.TransformerFactoryImpl"));
        SaxonProvider.configure(factory);
        final Object configuration = factory.getClass().getMethod("getConfiguration").invoke(factory);
        final java.lang.reflect.Method makeParser = configuration.getClass().getMethod("makeParser", String.class);
        final java.lang.reflect.InvocationTargetException exception = org.junit.jupiter.api.Assertions
                .assertThrows(java.lang.reflect.InvocationTargetException.class, () -> makeParser.invoke(configuration, FailingXMLReader.class.getName()));
        final javax.xml.transform.TransformerFactoryConfigurationError error = org.junit.jupiter.api.Assertions
                .assertInstanceOf(javax.xml.transform.TransformerFactoryConfigurationError.class, exception.getCause());
        org.junit.jupiter.api.Assertions.assertInstanceOf(SecureException.class, error.getException());
    }
}
