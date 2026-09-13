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

import org.apache.commons.xml.secure.SecureDocumentBuilderFactory
import org.apache.commons.xml.secure.SecureXPathFactory
import org.gradle.api.tasks.compile.JavaCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Dogfooding, not the artifact under test: this parses the POM below, while the tests run against the JAR in ../target.
        classpath("org.apache.commons:commons-secure-xml:1.0.0")
    }
}

// Blocked upstream: AGP 9 records JUnit 5 assumption aborts as failures, so the 87 platform-dependent skips this suite makes on Android turn red.
// AGP 8.6.1 with the same tests and the same JUnit 5 plugin reports them as skipped.
plugins {
    id("com.android.library") version "9.4.0"
    id("de.mannodermaus.android-junit5") version "2.0.1"
}

/** Returns the `version` of the Maven project itself: the direct child of the root element, never the `parent` block's. */
fun projectVersionOf(pom: File): String {
    // newInstance, not newNSInstance: the path below is spelled plainly, and would select nothing if the POM namespace were honoured.
    val documents = SecureDocumentBuilderFactory.newInstance()
    val version = SecureXPathFactory.newInstance().newXPath()
        .evaluate("/project/version", documents.newDocumentBuilder().parse(pom))
        .trim()
    if (version.isEmpty()) {
        throw GradleException("No <version> element in ${pom}")
    }
    return version
}

// Taken from the Maven build instead of repeated here, so the JAR this module looks for follows a version bump.
val libraryVersion = projectVersionOf(rootProject.file("../pom.xml"))
val libraryJar = rootProject.file("../target/commons-secure-xml-${libraryVersion}.jar")

android {
    namespace = "org.apache.commons.xml.secure.androidtests"
    compileSdk = 34

    defaultConfig {
        // java.lang.invoke, used by the newDefault* and newNS* lookups, exists from API level 26.
        minSdk = 26
        // androidx.test runner; Mannodermaus's android-junit5 plugin slots a JUnit 5 RunnerBuilder under it so AndroidJUnitRunner picks up Jupiter tests.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The plugin passes an empty configurationParameters argument, which am instrument mis-parses; give it a value.
        testInstrumentationRunnerArguments["configurationParameters"] = "junit.jupiter.execution.parallel.enabled=false"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Match the JaCoCo the Maven build reports with, so the on-device agent understands the same execution data format.
    testCoverage {
        jacocoVersion = "0.8.15"
    }

    buildTypes {
        getByName("debug") {
            enableAndroidTestCoverage = true
        }
    }

    sourceSets {
        getByName("androidTest") {
            java.srcDirs("../src/test/java")
            resources.srcDirs("../src/test/resources")
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                // API 33 is the first AOSP release shipping libexpat >= 2.4, which has the built-in billion-laughs check.
                // Earlier images (e.g. API 31 with libexpat 2.3.0) carry no native amplification protection.
                create("api33") {
                    device = "Pixel 6a"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

// ShadingFootprintTest is a JVM-only build check (it uses jdependency to read target/classes); exclude it from the Android test compile.
tasks.withType<JavaCompile>().configureEach {
    exclude("**/ShadingFootprintTest.java")
}

// Skip JAXP groups whose factories Android does not ship
junitPlatform {
    filters {
        // Pass single tag expression
        includeTags("dom | sax | schema | trax")
    }
}

dependencies {
    if (libraryJar.exists()) {
        implementation(files(libraryJar))
    } else {
        // Helpful failure: tell the user to build the jar before running androidTest tasks.
        configurations.named("implementation").configure {
            dependencies.add(
                project.dependencies.create(
                    files(libraryJar).builtBy(
                        tasks.register("missingLibraryJar") {
                            doFirst {
                                throw GradleException(
                                    "Library JAR not found at ${libraryJar}. Run `mvn -DskipTests package` from the project root first."
                                )
                            }
                        }
                    )
                )
            )
        }
    }

    // Apache Xerces: android.jar ships javax.xml.validation but no SchemaFactory implementation.
    androidTestImplementation("xerces:xercesImpl:2.12.2")

    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    androidTestImplementation("de.mannodermaus.junit5:android-test-core:2.0.1")
    androidTestRuntimeOnly("de.mannodermaus.junit5:android-test-runner:2.0.1")
    androidTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("org.mockito:mockito-android:4.11.0")
}

