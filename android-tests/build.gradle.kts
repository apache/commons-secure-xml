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

import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.library") version "8.6.1"
    id("de.mannodermaus.android-junit5") version "1.14.0.0"
}

val libraryVersion = "1.0.0-SNAPSHOT"
val libraryJar = rootProject.file("../target/commons-secure-xml-${libraryVersion}.jar")

android {
    namespace = "org.apache.commons.xml.androidtests"
    compileSdk = 34

    defaultConfig {
        // java.lang.invoke, used by the newDefault* and newNS* lookups, exists from API level 26.
        minSdk = 26
        // androidx.test runner; Mannodermaus's android-junit5 plugin slots a JUnit 5 RunnerBuilder under it so AndroidJUnitRunner picks up Jupiter tests.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
            devices {
                // API 33 is the first AOSP release shipping libexpat >= 2.4, which has the built-in billion-laughs check.
                // Earlier images (e.g. API 31 with libexpat 2.3.0) carry no native amplification protection.
                maybeCreate<ManagedVirtualDevice>("api33").apply {
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
    androidTestImplementation("de.mannodermaus.junit5:android-test-core:1.4.0")
    androidTestRuntimeOnly("de.mannodermaus.junit5:android-test-runner:1.4.0")
    androidTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

