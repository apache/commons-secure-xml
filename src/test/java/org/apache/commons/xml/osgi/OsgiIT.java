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

package org.apache.commons.xml.osgi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Runs the unit test suite inside an OSGi framework, against the built bundle.
 *
 * <p>
 * The tests catch class-loading assumptions that only hold on a flat classpath: an OSGi bundle class loader sees its own classes, the packages it imports and
 * {@code java.*}, but not JDK-internal packages such as {@code com.sun.*}. The built jar is installed with its generated manifest, so the bundle also proves
 * that its {@code Import-Package} header resolves.
 * </p>
 * <p>
 * The test output directory, which the build gives a fragment manifest, is attached to that bundle, so the tests share its class loader and keep their
 * package-private access. The test harness (JUnit, Mockito, JAXB) is not under test and is boot-delegated to the class path. Only the JDK's built-in JAXP
 * implementations are used, as in the {@code test-stockjdk} execution: provider lookups cannot see other bundles without a mediator such as SPI Fly.
 * </p>
 * <p>
 * The framework is whichever single {@link FrameworkFactory} the class path provides; the build runs this class once with Apache Felix and once with Eclipse
 * Equinox.
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OsgiIT {

    /**
     * Factory methods that reach the JDK's built-in implementations without a provider lookup: {@code SecureSchemaFactory} also initializes the
     * {@code LSResourceResolver} floor.
     */
    private static final String[][] DEFAULT_FACTORIES = {{"SecureDocumentBuilderFactory", "newDefaultInstance"},
        {"SecureSAXParserFactory", "newDefaultInstance"}, {"SecureSchemaFactory", "newDefaultInstance"}, {"SecureTransformerFactory", "newDefaultInstance"},
        {"SecureXPathFactory", "newDefaultInstance"}, {"SecureXMLInputFactory", "newDefaultFactory"}};

    /**
     * Package roots the framework delegates to the class path: the test harness only, never the library or JDK internals.
     */
    private static final String[] HARNESS_PACKAGES = {"org.junit", "org.opentest4j", "org.apiguardian", "org.mockito", "net.bytebuddy", "org.objenesis",
        "javax.xml.bind", "com.sun.xml.bind", "org.glassfish.jaxb", "com.sun.istack", "javax.activation", "jakarta.activation"};

    /**
     * Symbolic name of the library bundle.
     */
    private static final String HOST_SYMBOLIC_NAME = "org.apache.commons.xml.secure";

    /**
     * Test classes that cannot run inside a framework: {@code ShadingFootprintTest} reads {@code target/classes} directly.
     */
    private static final List<String> NOT_IN_OSGI = Collections.singletonList("org.apache.commons.xml.secure.ShadingFootprintTest");

    /**
     * Test methods that cannot run inside a framework.
     *
     * <p>
     * They name a test class as the JAXP factory, which JAXP loads through the context class loader: that is the class path, which holds a second copy of the
     * test classes.
     * </p>
     */
    private static final List<String> METHODS_NOT_IN_OSGI = Arrays.asList("SecureDocumentBuilderFactoryTest#newDocumentBuilderWrapsDeclaredExceptions",
            "SecureSAXParserFactoryTest#newXmlReaderWrapsDeclaredExceptions");

    /**
     * Location of the test classes inside the fragment.
     */
    private static final String TEST_PACKAGE = HOST_SYMBOLIC_NAME.replace('.', '/');

    private static Framework framework;

    private static Bundle host;

    private static Path requiredPath(final String property) {
        final String value = System.getProperty(property);
        assertNotNull(value, "System property '" + property + "' must be set by the build");
        return Paths.get(value).toAbsolutePath();
    }

    @BeforeAll
    static void startFramework() throws Exception {
        final List<FrameworkFactory> factories = new ArrayList<>();
        ServiceLoader.load(FrameworkFactory.class).forEach(factories::add);
        assertEquals(1, factories.size(), () -> "Expected exactly one OSGi framework on the class path, found " + factories);
        final Path buildJar = requiredPath("buildJar");
        final Path work = requiredPath("osgiWorkDirectory");
        Files.createDirectories(work);
        final Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, work.resolve("storage").toString());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        config.put(Constants.FRAMEWORK_BUNDLE_PARENT, Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK);
        // A trailing ".*" only matches subpackages, so each root is listed twice.
        // sun.reflect, jdk.internal.reflect: the JDK defines generated reflection accessors under the bundle loader.
        // leaked: XSLTC rejects the host of Felix bundle:// URLs (for example "5.0"), so the XML fixtures resolve as file: URLs instead.
        config.put(Constants.FRAMEWORK_BOOTDELEGATION, Stream.concat(Stream.of(HARNESS_PACKAGES).flatMap(p -> Stream.of(p, p + ".*")),
                Stream.of("sun.reflect", "jdk.internal.reflect", "leaked")).collect(Collectors.joining(",")));
        // Both frameworks can fall back to the parent loader on their own; disable it so only the explicit list above is delegated.
        config.put("felix.bootdelegation.implicit", "false");
        config.put("osgi.compatibility.bootdelegation", "false");
        config.put("osgi.context.bootdelegation", "false");
        framework = factories.get(0).newFramework(config);
        framework.start();
        final BundleContext context = framework.getBundleContext();
        host = context.installBundle(buildJar.toUri().toString());
        // The test output directory carries the fragment manifest written by maven-bundle-plugin; install it in place.
        context.installBundle("reference:" + requiredPath("testClassesDirectory").toUri());
        host.start();
        assertEquals(Bundle.ACTIVE, host.getState(), "library bundle must start");
    }

    @AfterAll
    static void stopFramework() throws Exception {
        if (framework != null) {
            framework.stop();
            final FrameworkEvent event = framework.waitForStop(10_000);
            assertEquals(FrameworkEvent.STOPPED, event.getType(),
                    () -> "framework did not stop cleanly: event type " + event.getType() + ", " + event.getThrowable());
        }
    }

    /**
     * Runs one test class, loaded through the library bundle, and fails with the inner failures if any.
     */
    private static void runTestClass(final Class<?> testClass) {
        final SummaryGeneratingListener listener = new SummaryGeneratingListener();
        final PostDiscoveryFilter notInOsgi = descriptor -> descriptor.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .filter(source -> METHODS_NOT_IN_OSGI.contains(source.getJavaClass().getSimpleName() + "#" + source.getMethodName()))
                .map(source -> FilterResult.excluded("cannot run inside an OSGi framework"))
                .orElseGet(() -> FilterResult.included(null));
        // The context class loader stays the class path's, as in a plain framework launcher.
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass))
                // Same exclusion as the test-stockjdk execution: xpath3 needs Saxon.
                .filters(TagFilter.excludeTags("xpath3"), notInOsgi)
                .build(), listener);
        final TestExecutionSummary summary = listener.getSummary();
        if (summary.getTotalFailureCount() > 0) {
            final StringWriter failures = new StringWriter();
            summary.printFailuresTo(new PrintWriter(failures), 25);
            throw new AssertionError(summary.getTotalFailureCount() + " failure(s) in " + testClass.getName() + " under " + framework.getSymbolicName()
                    + System.lineSeparator() + failures);
        }
    }

    /**
     * Checks that the default factories do not depend on the context class loader, with the library bundle's class loader as one.
     *
     * <p>
     * Runs first: a class initializer, such as that of the {@code LSResourceResolver} floor, runs once per framework.
     * </p>
     */
    @Test
    @Order(1)
    void defaultFactoriesIgnoreContextClassLoader() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader contextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(host.adapt(BundleWiring.class).getClassLoader());
        try {
            assertAll(Stream.of(DEFAULT_FACTORIES).map(factory -> () -> {
                try {
                    assertNotNull(host.loadClass(HOST_SYMBOLIC_NAME + "." + factory[0]).getMethod(factory[1]).invoke(null), factory[0] + "." + factory[1]);
                } catch (final InvocationTargetException e) {
                    throw e.getCause();
                }
            }));
        } finally {
            thread.setContextClassLoader(contextClassLoader);
        }
    }

    @TestFactory
    @Order(2)
    Stream<DynamicTest> unitTestsPassInsideFramework() throws ClassNotFoundException {
        final List<DynamicTest> tests = new ArrayList<>();
        final Enumeration<URL> entries = host.findEntries(TEST_PACKAGE, "*Test.class", true);
        assertNotNull(entries, "test fragment must be attached to the library bundle");
        while (entries.hasMoreElements()) {
            final String path = entries.nextElement().getPath();
            final String className = path.substring(path.indexOf(TEST_PACKAGE), path.length() - ".class".length()).replace('/', '.');
            if (!NOT_IN_OSGI.contains(className)) {
                final Class<?> testClass = host.loadClass(className);
                tests.add(DynamicTest.dynamicTest(testClass.getSimpleName(), () -> runTestClass(testClass)));
            }
        }
        assertFalse(tests.isEmpty(), "no test classes found in the test fragment");
        tests.sort((a, b) -> a.getDisplayName().compareTo(b.getDisplayName()));
        return tests.stream();
    }
}
