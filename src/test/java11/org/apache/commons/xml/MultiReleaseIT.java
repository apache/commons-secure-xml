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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;

/**
 * Guards the Multi-Release layout and behavior of the built jar.
 *
 * <p>The {@code Safe*} factory classes ship as three progressively richer versions: the base release-8 classes, a {@code META-INF/versions/9} layer adding the
 * Java 9 JAXP factory methods, and a {@code META-INF/versions/13} layer adding the {@code newNSInstance} family on the two parser factories. Surefire runs
 * against the exploded {@code target/classes} directory, where the JVM never applies Multi-Release selection, so only a test against the built jar can verify
 * that a versioned class is actually picked up and that its added methods return hardened factories.</p>
 *
 * <p>The versioned layers are compiled only when the build JDK supports them (profiles {@code java9-multi-release} and {@code java13-multi-release}), so each
 * group of assertions is gated on its layer being present in the jar rather than on the JDK version.</p>
 */
class MultiReleaseIT {

    private static final String PKG_DIR = "org/apache/commons/xml/";

    private static final String VERSIONS_9 = "META-INF/versions/9/" + PKG_DIR;

    private static final String VERSIONS_13 = "META-INF/versions/13/" + PKG_DIR;

    private static final List<String> SAFE_CLASSES = List.of("SafeDocumentBuilderFactory", "SafeSAXParserFactory", "SafeSchemaFactory",
            "SafeTransformerFactory", "SafeXMLInputFactory", "SafeXPathFactory");

    private static String buildJarPath() {
        final String jar = System.getProperty("buildJar");
        assertNotNull(jar, "System property 'buildJar' must point at the built artifact");
        return jar;
    }

    /**
     * Loads the given {@code Safe*} class from the built jar and invokes its no-arg static factory method.
     *
     * <p>The class loader parents on the platform loader on purpose: failsafe also puts the exploded {@code target/classes} on the application class path, and
     * a default-parented loader would resolve the classes from there, where Multi-Release selection never applies.</p>
     */
    private static Object invokeFromJar(final String className, final String methodName) throws Exception {
        final URL jarUrl = Paths.get(buildJarPath()).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jarUrl}, ClassLoader.getPlatformClassLoader())) {
            return loader.loadClass("org.apache.commons.xml." + className).getMethod(methodName).invoke(null);
        }
    }

    private static byte[] entryBytes(final JarFile jar, final String name) throws IOException {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(name))) {
            return in.readAllBytes();
        }
    }

    /**
     * Classfile major version of the entry, from bytes 6-7 of the classfile header.
     */
    private static int majorOf(final JarFile jar, final String name) throws IOException {
        final byte[] bytes = entryBytes(jar, name);
        return (bytes[6] & 0xFF) << 8 | bytes[7] & 0xFF;
    }

    /**
     * Whether the classfile contains the ASCII string, used to probe a constant-pool method name without parsing the constant pool.
     */
    private static boolean containsAscii(final byte[] classFile, final String text) {
        final byte[] needle = text.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= classFile.length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && classFile[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return true;
            }
        }
        return false;
    }

    @Test
    void versionedLayersArePackagedCompletely() throws IOException {
        try (JarFile jar = new JarFile(buildJarPath())) {
            assumeTrue(jar.getJarEntry(VERSIONS_9 + "SafeDocumentBuilderFactory.class") != null, "jar built without the versions/9 layer");

            final Manifest manifest = jar.getManifest();
            assertEquals("true", manifest.getMainAttributes().getValue("Multi-Release"), "Multi-Release manifest attribute");

            for (final String name : SAFE_CLASSES) {
                assertNotNull(jar.getJarEntry(PKG_DIR + name + ".class"), "base layer must contain " + name);
                assertNotNull(jar.getJarEntry(VERSIONS_9 + name + ".class"), "versions/9 must contain " + name);
                assertFalse(containsAscii(entryBytes(jar, PKG_DIR + name + ".class"), "newDefault"),
                        "base layer " + name + " must not expose Java 9+ factory methods");
            }

            if (jar.getJarEntry(VERSIONS_13 + "SafeDocumentBuilderFactory.class") != null) {
                for (final String name : SAFE_CLASSES) {
                    final boolean expected = name.equals("SafeDocumentBuilderFactory") || name.equals("SafeSAXParserFactory");
                    assertEquals(expected, jar.getJarEntry(VERSIONS_13 + name + ".class") != null,
                            "versions/13 must contain exactly the two parser factories, mismatch for " + name);
                }
            }
        }
    }

    @Test
    void classfileMajorsMatchTheirLayer() throws IOException {
        try (JarFile jar = new JarFile(buildJarPath())) {
            assumeTrue(jar.getJarEntry(VERSIONS_9 + "SafeDocumentBuilderFactory.class") != null, "jar built without the versions/9 layer");
            assertEquals(52, majorOf(jar, PKG_DIR + "SafeDocumentBuilderFactory.class"), "base layer must be release 8 bytecode");
            assertEquals(53, majorOf(jar, VERSIONS_9 + "SafeDocumentBuilderFactory.class"), "versions/9 must be release 9 bytecode");
            if (jar.getJarEntry(VERSIONS_13 + "SafeDocumentBuilderFactory.class") != null) {
                assertEquals(57, majorOf(jar, VERSIONS_13 + "SafeDocumentBuilderFactory.class"), "versions/13 must be release 13 bytecode");
            }
        }
    }

    /**
     * The OSGi execution-environment requirement must stay at Java 8: bnd computes it from the base layer only, and the versioned layers must not raise it.
     */
    @Test
    void requireCapabilityStaysAtJava8() throws IOException {
        try (JarFile jar = new JarFile(buildJarPath())) {
            final String capability = jar.getManifest().getMainAttributes().getValue("Require-Capability");
            assertNotNull(capability, "Bundle must declare a Require-Capability header");
            assertTrue(capability.contains("(version=1.8)"), "osgi.ee requirement must stay at Java 8, was: " + capability);
        }
    }

    @Test
    void java9MethodsReturnHardenedFactories() throws Exception {
        try (JarFile jar = new JarFile(buildJarPath())) {
            assumeTrue(jar.getJarEntry(VERSIONS_9 + "SafeDocumentBuilderFactory.class") != null, "jar built without the versions/9 layer");
        }
        final DocumentBuilderFactory documentBuilderFactory = (DocumentBuilderFactory) invokeFromJar("SafeDocumentBuilderFactory", "newDefaultInstance");
        assertTrue(documentBuilderFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        final SAXParserFactory saxParserFactory = (SAXParserFactory) invokeFromJar("SafeSAXParserFactory", "newDefaultInstance");
        assertTrue(saxParserFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        final SchemaFactory schemaFactory = (SchemaFactory) invokeFromJar("SafeSchemaFactory", "newDefaultInstance");
        assertTrue(schemaFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        final TransformerFactory transformerFactory = (TransformerFactory) invokeFromJar("SafeTransformerFactory", "newDefaultInstance");
        assertTrue(transformerFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        final XMLInputFactory xmlInputFactory = (XMLInputFactory) invokeFromJar("SafeXMLInputFactory", "newDefaultFactory");
        assertEquals(Boolean.TRUE, xmlInputFactory.getProperty(XMLInputFactory.SUPPORT_DTD));
        final XPathFactory xPathFactory = (XPathFactory) invokeFromJar("SafeXPathFactory", "newDefaultInstance");
        assertTrue(xPathFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    void java13NSMethodsReturnNamespaceAwareHardenedFactories() throws Exception {
        try (JarFile jar = new JarFile(buildJarPath())) {
            assumeTrue(jar.getJarEntry(VERSIONS_13 + "SafeDocumentBuilderFactory.class") != null, "jar built without the versions/13 layer");
        }
        for (final String method : List.of("newNSInstance", "newDefaultNSInstance")) {
            final DocumentBuilderFactory documentBuilderFactory = (DocumentBuilderFactory) invokeFromJar("SafeDocumentBuilderFactory", method);
            assertTrue(documentBuilderFactory.isNamespaceAware(), method + " must be namespace-aware");
            assertTrue(documentBuilderFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
            final SAXParserFactory saxParserFactory = (SAXParserFactory) invokeFromJar("SafeSAXParserFactory", method);
            assertTrue(saxParserFactory.isNamespaceAware(), method + " must be namespace-aware");
            assertTrue(saxParserFactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        }
    }
}
