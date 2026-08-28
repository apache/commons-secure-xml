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

import javax.xml.transform.Source;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.vafer.jdependency.Clazz;
import org.vafer.jdependency.Clazzpath;

/**
 * Guards the shade footprint: the set of classes a consumer pulls in when they shade a single factory entry point.
 *
 * <p>Using {@code jdependency}, the same library {@code maven-shade-plugin}'s {@code minimizeJar} uses, this test computes each entry point's transitive class
 * closure over the compiled {@code target/classes} and pins it to an expected set. It keeps each entry point from silently regaining a dependency on classes it
 * should not need (for example a sibling resolver floor or another factory class), so schema builds only on the shared SAX path, TrAX and XPath additionally on the
 * DOM path their Xalan getAssociatedStylesheet and InputSource rewrites parse through, while the six public entry points together pull the whole
 * library. Update the expected sets deliberately: a change here is a change to what a downstream shade includes.</p>
 *
 * <p>The test reads the compiled {@code .class} files from the code-source location, which only exists on a regular JVM: a native image carries no bytecode (and
 * nobody shades one), so the test is disabled there, just as it is excluded from the Android test compile.</p>
 */
@DisabledInNativeImage
class ShadingFootprintTest {

    private static final String PKG = "org.apache.commons.xml.";

    // @formatter:off
    private static final Set<String> DOCUMENT_BUILDER_FACTORY = set(
            "FallbackIgnoreEntityResolver2",
            "HardeningDocumentBuilder",
            "HardeningDocumentBuilderFactory",
            "HardeningDocumentBuilderFactory$1",
            "HardeningDocumentBuilderFactory$Wrapper",
            "HardeningException");
    // @formatter:on

    // @formatter:off
    private static final Set<String> SAX_PARSER_FACTORY = set(
            "FallbackIgnoreEntityResolver2",
            "HardeningException",
            "HardeningSAXParser",
            "HardeningSAXParserFactory",
            "HardeningSAXParserFactory$1",
            "HardeningSAXParserFactory$HardeningExpatXMLReader",
            "HardeningSAXParserFactory$Wrapper",
            "HardeningXMLReader");
    // @formatter:on

    // @formatter:off
    private static final Set<String> XML_INPUT_FACTORY = set(
            "FallbackIgnoreXMLResolver",
            "HardeningException",
            "HardeningXMLInputFactory",
            "HardeningXMLInputFactory$1",
            "HardeningXMLInputFactory$Wrapper");
    // @formatter:on

    /**
     * TrAX, XPath and schema re-harden their sub-parsers through {@link HardeningSAXParserFactory#harden(Source)}, so each builds on the full SAX closure below;
     * TrAX additionally parses the Xalan {@code getAssociatedStylesheet} source and XPath its InputSource-taking evaluate calls through the DOM entry point, so
     * their closures carry that set too.
     */
    // @formatter:off
    private static final Set<String> TRANSFORMER_FACTORY = saxParserFactoryPlus(
            "FallbackIgnoreEntityResolver2",
            "FallbackIgnoreURIResolver",
            "HardeningDocumentBuilder",
            "HardeningDocumentBuilderFactory",
            "HardeningDocumentBuilderFactory$1",
            "HardeningDocumentBuilderFactory$Wrapper",
            "HardeningException",
            "HardeningSAXParser",
            "HardeningSAXParserFactory",
            "HardeningSAXParserFactory$1",
            "HardeningSAXParserFactory$HardeningExpatXMLReader",
            "HardeningSAXParserFactory$Wrapper",
            "HardeningTemplates",
            "HardeningTemplatesHandler",
            "HardeningTransformer",
            "HardeningTransformerFactory",
            "HardeningTransformerFactory$1",
            "HardeningTransformerFactory$Wrapper",
            "HardeningTransformerHandler",
            "HardeningXMLFilter",
            "HardeningXMLReader",
            "SaxonProvider",
            "SaxonProvider$1",
            "SaxonProvider$HardenedConfiguration",
            "SaxonProvider$SaxonProviderConfigurer");
    // @formatter:on

    // @formatter:off
    private static final Set<String> XPATH_FACTORY = saxParserFactoryPlus(
            "FallbackIgnoreEntityResolver2",
            "FallbackIgnoreURIResolver",
            "HardeningDocumentBuilder",
            "HardeningDocumentBuilderFactory",
            "HardeningDocumentBuilderFactory$1",
            "HardeningDocumentBuilderFactory$Wrapper",
            "HardeningException",
            "HardeningSAXParser",
            "HardeningSAXParserFactory",
            "HardeningSAXParserFactory$1",
            "HardeningSAXParserFactory$HardeningExpatXMLReader",
            "HardeningSAXParserFactory$Wrapper",
            "HardeningXMLReader",
            "HardeningXPath",
            "HardeningXPathExpression",
            "HardeningXPathFactory",
            "HardeningXPathFactory$1",
            "HardeningXPathFactory$Wrapper",
            "SaxonProvider",
            "SaxonProvider$1",
            "SaxonProvider$HardenedConfiguration",
            "SaxonProvider$SaxonProviderConfigurer");
    // @formatter:on

    // @formatter:off
    private static final Set<String> SCHEMA_FACTORY = saxParserFactoryPlus(
            "FallbackIgnoreEntityResolver2",
            "FallbackIgnoreLSResourceResolver",
            "HardeningException",
            "HardeningSAXParser",
            "HardeningSAXParserFactory",
            "HardeningSAXParserFactory$1",
            "HardeningSAXParserFactory$HardeningExpatXMLReader",
            "HardeningSAXParserFactory$Wrapper",
            "HardeningSchema",
            "HardeningSchemaFactory",
            "HardeningSchemaFactory$1",
            "HardeningSchemaFactory$Wrapper",
            "HardeningValidator",
            "HardeningValidatorHandler",
            "HardeningXMLReader");
    // @formatter:on

    /**
     * Class count of the {@link #rootClosure()} DOM entry point, the baseline the {@link #reportFootprint()} percentages are computed against.
     */
    private static final int LIBRARY_CLASS_COUNT = 6;

    /**
     * Entry points reported by the {@link #reportFootprint()} diagnostic, most-focused first, ending with the whole library.
     */
    private static final String[] REPORTED = {"HardeningDocumentBuilderFactory", "HardeningSAXParserFactory", "HardeningXMLInputFactory",
            "HardeningTransformerFactory", "HardeningXPathFactory", "HardeningSchemaFactory"};

    private static Clazzpath clazzpath;
    private static Path classesDir;

    /**
     * Sums the uncompressed {@code .class} file sizes of a closure's classes, as they would land in a shaded jar.
     */
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

    /**
     * Transitive class closure of {@code PKG + simpleName}, restricted to this library's own package and reported by simple name.
     */
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

    @BeforeAll
    static void indexCompiledClasses() throws Exception {
        classesDir = Paths.get(HardeningException.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        clazzpath = new Clazzpath();
        clazzpath.addClazzpathUnit(classesDir);
    }

    /**
     * Prints each entry point's shade closure size (uncompressed {@code .class} bytes) and its share of the full library, to track the footprint over the
     * refactor.
     */
    @AfterAll
    static void reportFootprint() {
        final long library = bytesOf(rootClosure());
        final StringBuilder report = new StringBuilder("\nShade footprint (uncompressed .class bytes, % of full library):\n");
        for (final String entry : REPORTED) {
            final Set<String> closure = closureOf(entry);
            final long bytes = bytesOf(closure);
            report.append(String.format(Locale.ROOT, "  %-24s %2d classes  %7d bytes  %5.1f%%%n", entry, closure.size(), bytes, 100.0 * bytes / library));
        }
        if (Boolean.getBoolean(ShadingFootprintTest.class.getName() + ".reportFootprint")) {
            System.out.print(report);
        }
    }

    private static Set<String> rootClosure() {
        return closureOf("HardeningDocumentBuilderFactory");
    }

    /**
     * {@link #SAX_PARSER_FACTORY} plus the extra names; used where an entry point's closure is the SAX path plus its own classes.
     */
    private static Set<String> saxParserFactoryPlus(final String... more) {
        final Set<String> union = new TreeSet<>(SAX_PARSER_FACTORY);
        union.addAll(Arrays.asList(more));
        return union;
    }

    private static Set<String> set(final String... names) {
        return new TreeSet<>(Arrays.asList(names));
    }

    private static String strip(final String qualifiedName) {
        return qualifiedName.substring(PKG.length());
    }

    @Test
    void documentBuilderFactoryFootprint() {
        assertEquals(DOCUMENT_BUILDER_FACTORY, closureOf("HardeningDocumentBuilderFactory"));
    }

    @Test
    void rootClosureMatchesDocumentBuilderFootprint() {
        assertEquals(LIBRARY_CLASS_COUNT, rootClosure().size(), "HardeningDocumentBuilderFactory closure size drifted");
    }

    @Test
    void saxParserFactoryFootprint() {
        assertEquals(SAX_PARSER_FACTORY, closureOf("HardeningSAXParserFactory"));
    }

    @Test
    void schemaFactoryFootprint() {
        assertEquals(SCHEMA_FACTORY, closureOf("HardeningSchemaFactory"));
    }

    @Test
    void xmlInputFactoryFootprint() {
        assertEquals(XML_INPUT_FACTORY, closureOf("HardeningXMLInputFactory"));
    }

    @Test
    void transformerFactoryFootprint() {
        assertEquals(TRANSFORMER_FACTORY, closureOf("HardeningTransformerFactory"));
    }

    @Test
    void xPathFactoryFootprint() {
        assertEquals(XPATH_FACTORY, closureOf("HardeningXPathFactory"));
    }
}
