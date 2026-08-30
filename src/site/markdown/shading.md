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

# Shading

Do you want to secure your XML parsers,
but adding an external dependency is against your project policy,
or you use only a small subset of the JAXP interfaces,
which does not justify roughly 72 kB of additional disk space?
We hear you.

The `Secure*` entry points have been designed with shading in mind.
The transitive closure of each of the six factory classes pulls in only what is necessary.
Shading with the Maven Shade Plugin's `<minimizeJar>` option
therefore keeps only the classes your application reaches,
and produces compressed sizes like these:

| Entry point                    | Classes | Shaded size |
|--------------------------------|--------:|------------:|
| `SecureXMLInputFactory`        |       7 |       ~7 kB |
| `SecureDocumentBuilderFactory` |       8 |       ~8 kB |
| `SecureSAXParserFactory`       |      10 |      ~10 kB |
| `SecureSchemaFactory`          |      17 |      ~18 kB |
| `SecureXPathFactory`           |      24 |      ~26 kB |
| `SecureTransformerFactory`     |      27 |      ~35 kB |

The sizes were measured on version 1.0.0.
The closures are pinned by a build check
(`ShadingFootprintTest`, built on the same dependency analysis `<minimizeJar>` performs),
so an entry point cannot silently regain a dependency on classes it does not need.
`SecureTransformerFactory` and `SecureXPathFactory` are the largest closures
because they reparse sub-sources through the SAX and DOM entry points;
`SecureSchemaFactory` builds on the SAX path alone.

## Security Implications

How you consume the library decides how fast a future security fix reaches your users.

### External Dependency

An ordinary [external dependency](dependency-info.html) is the recommended way to consume the library.
Vulnerable code is replaced by bumping one version number,
or even by a dependency-management override applied without touching your code,
so a fix reaches deployments without waiting for you to release.

### Shaded Copy

A shaded copy cannot be replaced from the outside:
shipping a fix requires rebuilding and re-releasing your own artifact.
What shading does preserve is the upgrade path:
every rebuild against a newer release of the library
picks up its securing recipes and fixes automatically,
and the relocated copy cannot conflict with
another version of `commons-xml` on the application's classpath.

### Vendored Code and Copy-Pasted Recipes

Vendored copies of the securing code,
and the cargo-culted "secure your parser" recipes that circulate online,
are the most space-effective option and the worst for security:
they freeze at copy time,
and no dependency tool will ever flag them as vulnerable or outdated.
If you maintain such code anyway, two changes give the most protection:

- Throw on unsupported securing properties and features
  instead of swallowing the exception.
  Security should be a guarantee,
  not a best effort that depends on
  which JAXP implementation happens to be on the classpath at runtime.
- Construct factories with `newDefaultInstance()` (Java 9 and later)
  instead of `newInstance()`.
  Pinning the platform's built-in implementation
  removes the danger of a third-party implementation on the classpath
  replacing the one your recipe was written against.

## Relocating with the Maven Shade Plugin

The configuration below shades and relocates `commons-xml` alone,
while every other dependency of your project stays an ordinary external dependency:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <executions>
    <execution>
      <phase>package</phase>
      <goals>
        <goal>shade</goal>
      </goals>
      <configuration>
        <minimizeJar>true</minimizeJar>
        <artifactSet>
          <includes>
            <include>org.apache.commons:commons-xml</include>
          </includes>
        </artifactSet>
        <relocations>
          <relocation>
            <pattern>org.apache.commons.xml</pattern>
            <shadedPattern>com.example.app.internal.xml</shadedPattern>
          </relocation>
        </relocations>
        <filters>
          <filter>
            <artifact>org.apache.commons:commons-xml</artifact>
            <excludes>
              <exclude>META-INF/versions/9/module-info.class</exclude>
            </excludes>
          </filter>
        </filters>
      </configuration>
    </execution>
  </executions>
</plugin>
```

A few notes on the configuration:

- Replace `com.example.app.internal.xml` with a package of your own,
  outside your public API.
- The `<artifactSet>` restricts shading to `commons-xml`;
  the plugin removes it from the dependency-reduced POM,
  so downstream consumers never see it.
- The `<filter>` drops the library's multi-release module descriptor,
  which describes the unshaded jar and must not leak into yours.
- `<minimizeJar>` keeps only the entry points you call,
  which is what produces the sizes in the table above.
- When you distribute the shaded artifact,
  keep the attribution from the library's `META-INF/LICENSE.txt` and `META-INF/NOTICE.txt`,
  as the Apache License requires.

The securing behavior of a shaded copy is identical to the external dependency:
the guarantees of the [Threat Model](threat_model.html)
apply to the release you shaded.
