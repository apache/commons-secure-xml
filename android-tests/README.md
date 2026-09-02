<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Android instrumented tests

Runs the attack-test suite from `../src/test/java` against the Android runtime, exercising
the harmony based DOM and SAX factories that ship with Android. The Maven build does not include this
module; it is a standalone Gradle build kept separate so the default `mvn` goal stays JVM only.

## Prerequisites

- JDK 17 on `PATH` (AGP 8.x requires it).
- Android SDK with `platforms/android-34` and `build-tools/34.0.0` installed;
  export `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to point at it.

  ```shell
  export ANDROID_HOME=/path/to/android
  ```
- Either an attached emulator/device (`adb devices` shows it) or the AGP managed device bundled into this build (`api33`, AOSP system image).
- The library JAR built by the parent Maven build:

  ```shell
  mvn -f.. -DskipTests package
  ```

## Running

Against an attached emulator/device:

```shell
./gradlew connectedAndroidTest
```

Against the bundled AGP managed device
(downloads the AOSP API 33 system image on first run,
then provisions and tears down a headless emulator for each invocation):

```shell
./gradlew api33DebugAndroidTest
```

## Coverage

The run is instrumented with JaCoCo,
pinned to the version the Maven build reports with,
and leaves one execution data file per device under `build/outputs/code_coverage`.
The full round trip:

1. Build the JAR the instrumented tests run against:

   ```shell
   mvn -f.. -DskipTests package
   ```

2. Run the tests on a device, either an attached one or the bundled managed device:

   ```shell
   ./gradlew connectedDebugAndroidTest
   ./gradlew api33DebugAndroidTest
   ```

3. Fold the device data into the project's coverage:

   ```shell
   mvn -f.. -Pjacoco verify site
   ```

   The JVM suite runs under the JaCoCo agent in this step,
   so both halves end up in `target/jacoco.exec`:
   the `jacoco` profile merges whatever this module produced into it in the site lifecycle,
   just before the report is written.
   The coverage check runs earlier, on the JVM data alone,
   so its minimums mean the same thing whether a device run is lying around;
   the device data widens the report, not the bar.

Repeat step 2 whenever the library changes.
The execution data identifies each class by its bytecode,
and JaCoCo drops data that no longer matches the compiled class,
so a device run left over from an older JAR quietly lowers the numbers instead of inflating them.

### Maximum coverage

This library is written to work from JDK 8 through 25 and on Android.
Since new JAXP methods were introduced in JDK 9, 13, and 18,
maximum coverage is obtained by the following recipe:

```shell
export JDK8=/path/to/jdk8
export JDK21=/path/to/jdk21
export ANDROID_HOME=/path/to/android
JAVA_HOME=$JDK21 mvn -f.. -Pjacoco clean package
./gradlew api33DebugAndroidTest
JAVA_HOME=$JDK8 mvn -f.. -Pjacoco test
JAVA_HOME=$JDK21 mvn -f.. -Pjacoco verify site
```

## Excluded test groups

The build runs the `dom`, `sax`, `schema`, and `trax` tags and excludes the rest:

- `stax`: there is no `XMLInputFactory` on Android.
- `xpath`: Android ships an XPath implementation,
  but it is currently untested.
- `xpath3`: relies on Saxon, which is not on the Android classpath.

DOM, SAX, TrAX, and schema paths are exercised in full;
the schema tests run against the Apache Xerces the `androidTest` classpath brings in,
since Android ships `javax.xml.validation` without a `SchemaFactory` implementation.
