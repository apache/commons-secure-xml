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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.vafer.jdependency.Clazz;
import org.vafer.jdependency.Clazzpath;

/**
 * Guards the shade footprint: the set of classes a consumer pulls in when they shade a single hardener entry point.
 *
 * <p>Using {@code jdependency}, the same library {@code maven-shade-plugin}'s {@code minimizeJar} uses, this test computes each entry point's transitive class
 * closure over the compiled {@code target/classes} and pins it to an expected set. It keeps the DOM, SAX and StAX hardeners from silently regaining a dependency
 * on classes they should not need (for example the sibling resolver floors, or another hardener), and records that the TrAX, XPath and schema entry
 * points still pull the whole library through the {@link XmlFactories} re-hardening cycle. Update the expected sets deliberately: a change here is a change to what
 * a downstream shade includes.</p>
 */
class ShadingFootprintTest {

    private static final String PKG = "org.apache.commons.xml.";

    /** Every hardener needs this shared exception (its {@code settingFailed}/{@code forbidden} message helpers). */
    private static final String CORE = "HardeningException";

    private static final Set<String> DOCUMENT_BUILDER_HARDENER = set(
            "DocumentBuilderHardener", "HardeningDocumentBuilder", "HardeningDocumentBuilderFactory", CORE,
            "Resolvers", "Resolvers$FallbackDenyResolver", "Resolvers$FallbackDenyLSResourceResolver", "Resolvers$FallbackDenyURIResolver",
            "Resolvers$FallbackDenyXMLResolver", "Resolvers$FallbackIgnoreXMLResolver");

    private static final Set<String> SAX_PARSER_HARDENER = set(
            "SAXParserHardener", "SAXParserHardener$DtdAwareDenyResolver", "SAXParserHardener$HardeningExpatXMLReader",
            "HardeningSAXParser", "HardeningSAXParserFactory", "HardeningXMLReader", CORE,
            "Resolvers", "Resolvers$FallbackDenyResolver", "Resolvers$FallbackDenyLSResourceResolver", "Resolvers$FallbackDenyURIResolver",
            "Resolvers$FallbackDenyXMLResolver", "Resolvers$FallbackIgnoreXMLResolver");

    private static final Set<String> STAX_HARDENER = set(
            "StaxHardener", "StaxHardener$DtdSubsetFloor", "HardeningXMLInputFactory", CORE,
            "Resolvers", "Resolvers$FallbackDenyResolver", "Resolvers$FallbackDenyLSResourceResolver", "Resolvers$FallbackDenyURIResolver",
            "Resolvers$FallbackDenyXMLResolver", "Resolvers$FallbackIgnoreXMLResolver");

    /** The TrAX/XPath/schema entry points all pull the whole library through {@link XmlFactories}; this is its class count (Phase 4 territory to reduce). */
    private static final int WHOLE_LIBRARY_SIZE = 33;

    /** Entry points reported by the {@link #reportFootprint()} diagnostic, most-focused first, ending with the whole library. */
    private static final String[] REPORTED = {
            "DocumentBuilderHardener", "SAXParserHardener", "StaxHardener", "TransformerHardener", "XPathHardener", "HardeningSchemaFactory", "XmlFactories"};

    private static Clazzpath clazzpath;
    private static Path classesDir;

    @BeforeAll
    static void indexCompiledClasses() throws Exception {
        classesDir = Paths.get(HardeningException.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        clazzpath = new Clazzpath();
        clazzpath.addClazzpathUnit(classesDir);
    }

    /** Prints each entry point's shade closure size (uncompressed {@code .class} bytes) and its share of the full library, to track the footprint over the refactor. */
    @AfterAll
    static void reportFootprint() {
        final long library = bytesOf(closureOf("XmlFactories"));
        final StringBuilder report = new StringBuilder("\nShade footprint (uncompressed .class bytes, % of full library):\n");
        for (final String entry : REPORTED) {
            final Set<String> closure = closureOf(entry);
            final long bytes = bytesOf(closure);
            report.append(String.format(Locale.ROOT, "  %-24s %2d classes  %7d bytes  %5.1f%%%n", entry, closure.size(), bytes, 100.0 * bytes / library));
        }
        System.out.print(report);
    }

    @Test
    void documentBuilderHardenerFootprint() {
        assertEquals(DOCUMENT_BUILDER_HARDENER, closureOf("DocumentBuilderHardener"));
    }

    @Test
    void saxParserHardenerFootprint() {
        assertEquals(SAX_PARSER_HARDENER, closureOf("SAXParserHardener"));
    }

    @Test
    void staxHardenerFootprint() {
        assertEquals(STAX_HARDENER, closureOf("StaxHardener"));
    }

    @Test
    void traxXPathAndSchemaPullTheWholeLibrary() {
        final Set<String> whole = closureOf("XmlFactories");
        assertEquals(WHOLE_LIBRARY_SIZE, whole.size(), "XmlFactories closure size drifted: " + whole);
        assertEquals(whole, closureOf("TransformerHardener"), "TransformerHardener no longer pulls exactly the whole library");
        assertEquals(whole, closureOf("XPathHardener"), "XPathHardener no longer pulls exactly the whole library");
        assertEquals(whole, closureOf("HardeningSchemaFactory"), "HardeningSchemaFactory no longer pulls exactly the whole library");
    }

    /** Transitive class closure of {@code PKG + simpleName}, restricted to this library's own package and reported by simple name. */
    private static Set<String> closureOf(final String simpleName) {
        final Clazz entry = clazzpath.getClazz(PKG + simpleName);
        if (entry == null) {
            throw new IllegalStateException("Not on the compiled classpath: " + PKG + simpleName);
        }
        final Set<String> names = new TreeSet<>();
        names.add(strip(entry.getName()));
        for (final Clazz dependency : entry.getTransitiveDependencies()) {
            if (dependency.getName().startsWith(PKG)) {
                names.add(strip(dependency.getName()));
            }
        }
        return names;
    }

    /** Sums the uncompressed {@code .class} file sizes of a closure's classes, as they would land in a shaded jar. */
    private static long bytesOf(final Set<String> simpleNames) {
        long total = 0;
        for (final String name : simpleNames) {
            try {
                total += Files.size(classesDir.resolve("org/apache/commons/xml/" + name + ".class"));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return total;
    }

    private static String strip(final String qualifiedName) {
        return qualifiedName.substring(PKG.length());
    }

    private static Set<String> set(final String... names) {
        return new TreeSet<>(Arrays.asList(names));
    }
}
