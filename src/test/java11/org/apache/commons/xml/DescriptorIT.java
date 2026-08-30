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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

/**
 * Guards the generated OSGi and JPMS descriptors against a regression that makes any of the library's optional dependencies mandatory.
 *
 * <p>Both descriptors are generated from bytecode by tools whose output can shift across versions: bnd derives the {@code Import-Package} header, and jdeps
 * (via moditect) derives {@code module-info}. Rather than launch an OSGi framework or a separate module layer, this test reads the two descriptors straight
 * out of the built jar and asserts the same invariant against each: the only mandatory dependency may be on the platform itself. Every other dependency, such
 * as Saxon HE or Xerces, must be optional, so a deployment that does not provide it still resolves.</p>
 *
 * <p>"Platform" means the packages an OSGi system bundle always exports, or the {@code java.*} modules the JDK always supplies. Those are the one set of
 * dependencies that does not need to be optional.</p>
 *
 * <p>This test reads {@code module-info} through {@link ModuleDescriptor}, a Java 9 API the project's release-8 test sources cannot reference. It therefore
 * lives in {@code src/test/java11} and is compiled and run only under the {@code java11-tests} profile, which activates on JDK 11 or later.</p>
 */
class DescriptorIT {

    /**
     * Package prefixes an OSGi system bundle always exports, so an import of them need not be optional. {@code java} and {@code javax} cover the JRE; the two
     * {@code org} entries cover the DOM and SAX APIs the JRE also ships.
     */
    private static final List<String> PLATFORM_PACKAGE_PREFIXES = List.of("java", "javax", "org.w3c", "org.xml.sax");

    /**
     * Opens the built artifact named by the failsafe {@code buildJar} system property.
     */
    private static JarFile openBuildJar() throws IOException {
        final String jar = System.getProperty("buildJar");
        assertNotNull(jar, "System property 'buildJar' must point at the built artifact");
        return new JarFile(jar);
    }

    @Test
    void everyModuleRequiresIsPlatformOrOptional() throws IOException {
        try (JarFile jar = openBuildJar()) {
            final JarEntry entry = findModuleInfo(jar);
            assertNotNull(entry, "Built jar must contain a module descriptor (root or under META-INF/versions/)");
            final ModuleDescriptor descriptor;
            try (InputStream in = jar.getInputStream(entry)) {
                descriptor = ModuleDescriptor.read(in);
            }

            for (final Requires requires : descriptor.requires()) {
                final boolean platform = requires.name().startsWith("java.");
                final boolean optional = requires.modifiers().contains(Requires.Modifier.STATIC);
                assertTrue(platform || optional,
                        "Non-platform module '" + requires.name() + "' must be an optional (static) requires, was: " + requires);
            }
        }
    }

    @Test
    void everyBundleImportIsPlatformOrOptional() throws IOException {
        try (JarFile jar = openBuildJar()) {
            final Manifest manifest = jar.getManifest();
            assertNotNull(manifest, "Built jar must contain a manifest");
            final String importPackage = manifest.getMainAttributes().getValue("Import-Package");
            assertNotNull(importPackage, "Bundle must declare an Import-Package header");

            for (final String clause : splitClauses(importPackage)) {
                final String pkg = packageName(clause);
                final boolean platform = isPlatformPackage(pkg);
                final boolean optional = clause.replace(" ", "").contains("resolution:=optional");
                assertTrue(platform || optional,
                        "Import of non-platform package '" + pkg + "' must be optional, was: " + clause.trim());
            }
        }
    }

    /**
     * Locates the module descriptor in the jar. A Java 8 library carries it as a Multi-Release entry under {@code META-INF/versions/<N>/module-info.class}
     * rather than at the root, so this accepts either location.
     */
    private static JarEntry findModuleInfo(final JarFile jar) {
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String name = entry.getName();
            if (name.equals("module-info.class") || (name.startsWith("META-INF/versions/") && name.endsWith("/module-info.class"))) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Returns whether {@code pkg} is exported by an OSGi system bundle, that is, it sits under one of {@link #PLATFORM_PACKAGE_PREFIXES}.
     */
    private static boolean isPlatformPackage(final String pkg) {
        for (final String prefix : PLATFORM_PACKAGE_PREFIXES) {
            if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits an OSGi header into its clauses on commas, ignoring commas inside double-quoted attribute values such as {@code version="[1.0,2.0)"}.
     */
    private static List<String> splitClauses(final String header) {
        final List<String> clauses = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < header.length(); i++) {
            final char c = header.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                clauses.add(header.substring(start, i));
                start = i + 1;
            }
        }
        clauses.add(header.substring(start));
        return clauses;
    }

    /**
     * Returns the package name of a clause, that is, everything before its first {@code ;} attribute separator.
     */
    private static String packageName(final String clause) {
        final int semicolon = clause.indexOf(';');
        return (semicolon < 0 ? clause : clause.substring(0, semicolon)).trim();
    }
}
