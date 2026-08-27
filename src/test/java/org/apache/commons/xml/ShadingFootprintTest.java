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
 * Guards the shade footprint: the set of classes a consumer pulls in when they shade a single hardener entry point.
 *
 * <p>Using {@code jdependency}, the same library {@code maven-shade-plugin}'s {@code minimizeJar} uses, this test computes each entry point's transitive class
 * closure over the compiled {@code target/classes} and pins it to an expected set. It keeps each hardener from silently regaining a dependency on classes it
 * should not need (for example a sibling resolver floor or another hardener), so schema builds only on the shared SAX path, TrAX and XPath additionally on the
 * DOM path their Xalan getAssociatedStylesheet and InputSource rewrites parse through, while the six public {@code Safe*} entry points together pull the whole
 * library. Update the expected sets deliberately: a change here is a change to what a downstream shade includes.</p>
 *
 * <p>The test reads the compiled {@code .class} files from the code-source location, which only exists on a regular JVM: a native image carries no bytecode (and
 * nobody shades one), so the test is disabled there, just as it is excluded from the Android test compile.</p>
 */
@DisabledInNativeImage
class ShadingFootprintTest {

    private static final String PKG = "org.apache.commons.xml.";

    /**
     * Shared exception carrying the {@code settingFailed} message helper; pulled in by every hardener that applies a JAXP setting.
     */
    private static final String HARDENING_EXCEPTION = "HardeningException";

    private static final Set<String> DOCUMENT_BUILDER_HARDENER = set("DocumentBuilderHardener", "HardeningDocumentBuilder", "HardeningDocumentBuilderFactory"
            , HARDENING_EXCEPTION, "FallbackIgnoreEntityResolver2");

    private static final Set<String> SAX_PARSER_HARDENER = set("SAXParserHardener",
            "SAXParserHardener$HardeningExpatXMLReader", "HardeningSAXParser", "HardeningSAXParserFactory", "HardeningXMLReader", HARDENING_EXCEPTION,
            "FallbackIgnoreEntityResolver2");

    private static final Set<String> STAX_HARDENER = set("StaxHardener", "HardeningXMLInputFactory", "FallbackIgnoreXMLResolver", HARDENING_EXCEPTION);

    /**
     * TrAX, XPath and schema re-harden their sub-parsers through {@link SAXParserHardener#hardenSource(Source)}, so each builds on the full SAX closure below;
     * TrAX additionally parses the Xalan {@code getAssociatedStylesheet} source and XPath its InputSource-taking evaluate calls through the DOM hardener, so
     * their closures carry that set too.
     */
    private static final Set<String> TRANSFORMER_HARDENER = saxParsersHardenerPlus("TransformerHardener", "HardeningTransformerFactory",
            "HardeningTransformer", "HardeningTransformerHandler", "HardeningTemplates", "HardeningTemplatesHandler", "HardeningXMLFilter",
            "FallbackIgnoreURIResolver", "SaxonProvider", "SaxonProvider$1", "SaxonProvider$HardenedConfiguration"
            , "SaxonProvider$SaxonProviderConfigurer", "DocumentBuilderHardener", "HardeningDocumentBuilder", "HardeningDocumentBuilderFactory");

    private static final Set<String> XPATH_HARDENER = saxParsersHardenerPlus("XPathHardener", "FallbackIgnoreURIResolver", "SaxonProvider",
            "SaxonProvider$1", "SaxonProvider$HardenedConfiguration", "SaxonProvider$SaxonProviderConfigurer", "HardeningXPathFactory", "HardeningXPath",
            "HardeningXPathExpression", "DocumentBuilderHardener", "HardeningDocumentBuilder", "HardeningDocumentBuilderFactory");

    private static final Set<String> SCHEMA_HARDENER = saxParsersHardenerPlus("SchemaHardener", "HardeningSchemaFactory", "HardeningValidator",
            "HardeningValidatorHandler", "HardeningSchema", "FallbackIgnoreLSResourceResolver");

    /**
     * The six public {@code Safe*} entry points together reference every hardener and pull the whole library; this is their combined class count. One more
     * than the old single-entry-point count plus the six entry points: {@code SafeSchemaFactory} routes through {@link SchemaHardener}, which the old entry
     * point bypassed.
     */
    private static final int LIBRARY_CLASS_COUNT = 41;

    /**
     * The public entry points, one per JAXP factory type; their combined closure is the whole library.
     */
    private static final String[] SAFE_ENTRY_POINTS = {"SafeDocumentBuilderFactory", "SafeSAXParserFactory", "SafeSchemaFactory", "SafeTransformerFactory",
            "SafeXMLInputFactory", "SafeXPathFactory"};

    /**
     * Entry points reported by the {@link #reportFootprint()} diagnostic, hardeners first, then the public {@code Safe*} entry points.
     */
    private static final String[] REPORTED = {"DocumentBuilderHardener", "SAXParserHardener", "StaxHardener", "TransformerHardener", "XPathHardener",
            "SchemaHardener", "SafeDocumentBuilderFactory", "SafeSAXParserFactory", "SafeSchemaFactory", "SafeTransformerFactory", "SafeXMLInputFactory",
            "SafeXPathFactory"};

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
        final long library = bytesOf(safeClosureUnion());
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

    /**
     * Union of the {@link #SAFE_ENTRY_POINTS} closures; between them they reference every hardener, so this is the whole library.
     */
    private static Set<String> safeClosureUnion() {
        final Set<String> union = new TreeSet<>();
        for (final String entry : SAFE_ENTRY_POINTS) {
            union.addAll(closureOf(entry));
        }
        return union;
    }

    /**
     * {@link #SAX_PARSER_HARDENER} plus the extra names; used where an entry point's closure is the SAX path plus its own classes.
     */
    private static Set<String> saxParsersHardenerPlus(final String... more) {
        final Set<String> union = new TreeSet<>(SAX_PARSER_HARDENER);
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
    void documentBuilderHardenerFootprint() {
        assertEquals(DOCUMENT_BUILDER_HARDENER, closureOf("DocumentBuilderHardener"));
    }

    @Test
    void safeEntryPointsTogetherPullTheWholeLibrary() {
        assertEquals(LIBRARY_CLASS_COUNT, safeClosureUnion().size(), "Safe* combined closure size drifted");
    }

    @Test
    void saxParserHardenerFootprint() {
        assertEquals(SAX_PARSER_HARDENER, closureOf("SAXParserHardener"));
    }

    @Test
    void schemaHardenerFootprint() {
        assertEquals(SCHEMA_HARDENER, closureOf("SchemaHardener"));
    }

    @Test
    void staxHardenerFootprint() {
        assertEquals(STAX_HARDENER, closureOf("StaxHardener"));
    }

    @Test
    void transformerHardenerFootprint() {
        assertEquals(TRANSFORMER_HARDENER, closureOf("TransformerHardener"));
    }

    @Test
    void xPathHardenerFootprint() {
        assertEquals(XPATH_HARDENER, closureOf("XPathHardener"));
    }
}
