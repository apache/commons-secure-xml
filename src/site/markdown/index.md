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

# Apache Commons Secure XML

Apache Commons Secure XML is part of the
[Apache Commons](https://commons.apache.org/index.html) project.

Apache Commons Secure XML provides secure-by-default JAXP factory creation,
abstracting over implementation-specific XXE securing differences between the
stock JDK and external JAXP implementations.


## Why

Any Java library that parses XML has to secure JAXP before handing a factory to user code, and every library ends up
copy-pasting the same securing snippet. The snippet is fragile: the attributes and features needed to secure a factory
are not standardized, each JAXP implementation exposes a slightly different set, and setting an unknown one throws an
exception that callers routinely swallow. Writing this block correctly for every implementation is real work, and
duplicating it across projects means every project owns the maintenance burden on its own.

Defaults are also uneven. The stock JDK SAX and DOM parsers already prevent external entity resolution through
`FEATURE_SECURE_PROCESSING`, and JAXP 1.5 conformant implementations ship reasonable defaults for most attacks. Others,
such as standalone Xerces, Woodstox, or Saxon's TrAX, need further configuration before they reach the same baseline. A
library author has no control over which implementation is on the classpath at runtime, so the effective security
posture of their code depends on a deployment decision made elsewhere.

This library provides that baseline. Each `org.apache.commons.xml` factory call returns a new factory secured by an
implementation-specific recipe, so the returned object behaves the same way security-wise regardless of which JAXP
implementation resolved. Security becomes a property of the call, not of the classpath, and there is one place to
update when a new securing setting becomes available or a default changes.

## Usage

Add the library to your build:

```xml
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-xml</artifactId>
  <version>${commons.release.version}</version>
</dependency>
```

Every factory method in `org.apache.commons.xml` returns a new, secured factory.
Pick the one that matches the API you already use;
no other configuration is required.
On secured factories an external resource reference (DTD, entity, schema, stylesheet) is never fetched:
it resolves to empty content,
so the parse continues without it
(see Configuration below).

### Supported Runtimes

The library requires OpenJDK 8 or later (or a JDK distribution built from it), or Android API level 26 or later.

The security guarantees are defined only on the OpenJDK family
(see the [Threat Model](threat_model.html)).
No version of Android supports `FEATURE_SECURE_PROCESSING`
(so states [Android's own documentation](https://developer.android.com/reference/javax/xml/parsers/DocumentBuilderFactory#setFeature%28java.lang.String,%20boolean%29)),
so the library secures the platform's parsers as best-effort.
Android's `XmlPullParser` API is not supported:
it is not a JAXP API.

### Supported Implementations

Out of the box the library recognizes the stock JDK JAXP implementations, Apache Xerces 2.x, Woodstox, and Saxon-HE. If
a factory resolves to an implementation not covered by any bundled securing recipe, every `org.apache.commons.xml` factory method throws
`IllegalStateException` with a message naming the unsupported class. Adding support for a new JAXP implementation
requires a code change to this library.

**DOM Parsing** via `DocumentBuilderFactory`:

```java
import org.w3c.dom.Document;
import org.apache.commons.xml.SecureDocumentBuilderFactory;

Document doc = SecureDocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
```

**SAX Parsing** via `SAXParserFactory`:

```java
import org.apache.commons.xml.SecureSAXParserFactory;

SecureSAXParserFactory.newInstance().newSAXParser().parse(inputStream, myDefaultHandler);
```

**Streaming (StAX) Parsing** via `XMLInputFactory`:

```java
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.xml.SecureXMLInputFactory;

XMLStreamReader reader = SecureXMLInputFactory.newInstance().createXMLStreamReader(inputStream);
```

**XSLT Transforms** via `TransformerFactory`:

```java
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.xml.SecureTransformerFactory;

SecureTransformerFactory.newInstance()
        .newTransformer(new StreamSource(stylesheet))
        .transform(new StreamSource(inputStream), new StreamResult(outputStream));
```

**XPath Queries** via `XPathFactory`:

```java
import javax.xml.xpath.XPathConstants;
import org.w3c.dom.NodeList;
import org.apache.commons.xml.SecureXPathFactory;

NodeList hits = (NodeList) SecureXPathFactory.newInstance()
        .newXPath()
        .evaluate("//item", doc, XPathConstants.NODESET);
```

**W3C XML Schema Validation** via `SchemaFactory`:

```java
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.xml.SecureSchemaFactory;

SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        .newSchema(new StreamSource(xsdStream))
        .newValidator()
        .validate(new StreamSource(inputStream));
```

### Wrappers, not the Original Factories

A returned factory is not necessarily an instance of the underlying implementation.
It might be (and usually is) a wrapper around it,
so it cannot be cast to the implementation's own class.
Everything else about the implementation's behavior is preserved:
features, properties, and attributes delegate to it,
and only the security behavior is applied.

Preserved behavior includes the choice of internal parsers.
Each TrAX, XPath, or schema implementation has its own way of instantiating them,
and the library respects it:

- Stock JDK factories use the JDK parsers by default,
  and expose the `jdk.xml.overrideDefaultParser` feature
  (and Java system property of the same name)
  to switch to parsers instantiated through `ServiceLoader`.
- Saxon selects its parsers through its own configuration.

Whichever parser is selected, it is secured.

### Factory Methods

Each factory class mirrors every static factory method its JAXP counterpart offers,
so a secured factory is a drop-in replacement at any construction site:
the class-name/class-loader overloads and the StAX `newFactory` family (JDK 8),
`newDefaultInstance()` (Java 9, [JDK-8169778](https://bugs.openjdk.org/browse/JDK-8169778)),
and the namespace-aware `newNSInstance()` family (Java 13, [JDK-8223423](https://bugs.openjdk.org/browse/JDK-8223423)).

All of these methods work on every supported runtime, including Java 8:
- The `newNSInstance` methods enable namespace awareness on their non-NS counterpart,
  the behavior the JAXP methods are specified to have.
- The `newDefaultInstance` methods resolve the platform's own `newDefaultInstance` at run time
  and use it wherever the runtime provides one —
  Java 9 or later, and the Android API levels that ship the method —
  falling back to instantiating the JDK's built-in implementation by class name on Java 8.

The `newDefaultInstance` methods are an opt-out of JAXP pluggability:
they pin the platform's built-in implementation
instead of whatever a classpath lookup would resolve.
That suits a library with minimal XML requirements,
which can parse with the well-known platform parser
rather than delegate the choice of implementation to the application developer.

### Stylesheets and Schemas

The securing applies to documents parsed through the returned factory. Stylesheets given to
`TransformerFactory.newTransformer(Source)` and schemas given to `SchemaFactory.newSchema(Source)` are read by a parser
the implementation picks internally, and that parser may not be secured (Saxon's TrAX is one such case, see Building
below). Treat stylesheets and schemas as trusted input, or pre-parse them through a secured `org.apache.commons.xml` parser and
pass the result as a `DOMSource` or `SAXSource`.
A stylesheet also chooses where the transform writes (`xsl:result-document`):
the securing governs reads only,
so restrict output destinations yourself when running an untrusted stylesheet
(see the [Threat Model](threat_model.html)).

### Transformer Handlers and Filters

The `SAXTransformerFactory` extension methods, `newTransformerHandler(...)`, `newTemplatesHandler()` and `newXMLFilter(...)`,
if reachable by casting the factory from `SecureTransformerFactory.newInstance()`,
produce handlers, filters and `Templates` carrying the same securing as the standard entry points:
runtime `document()` resolves to empty content,
and a filter with no caller-set parent parses its input through a secured reader.
The SAX events you feed into a handler, and a parent reader you set on a filter,
are your own configuration, like any caller-supplied parser.
See the [Threat Model](threat_model.html) for the exact scope.

### Caching and Thread-Safety

There is no caching or pooling inside `org.apache.commons.xml`; callers on a hot path are responsible for their own caching. The
returned factories inherit the thread-safety properties of the underlying JAXP implementation, which in practice means
they are not thread-safe. Create a new factory per thread or synchronize externally.

## Configuration

The secured factories need no configuration.
When a document references an external resource
(a DTD, an external entity, a schema, an XInclude target, or an XSLT document),
the securing layer resolves the reference to an empty stream:
nothing is fetched,
nothing leaks into the result,
and the parse continues wherever the implementation can proceed with empty content.
This forgiving default accommodates documents that merely carry such references without needing them.

If your application should reject such documents instead of parsing them,
tighten the factory yourself.
The securing floor stays underneath whatever you configure,
so the tightening carries **no security weight**
and can be as strict as the application needs:

- Set a stricter feature on the factory,
  for example `http://apache.org/xml/features/disallow-doctype-decl`
  to reject every document carrying a DOCTYPE,
  on implementations that support the feature.
- Install a resolver that throws.
  A caller-supplied `EntityResolver`, `XMLResolver`, `LSResourceResolver` or `URIResolver`
  is consulted before the securing floor,
  so an allow-list and a deny-all are both one resolver away.

As a temporary debugging measure,
set the system property `org.apache.commons.xml.throwOnUnresolved` to `true`:
every unresolved external reference is then rejected with the resolution hook's exception,
and the message names the denied resource.
The property is read at resolution time,
so it can be toggled on a running application;
treat it as a diagnostic switch,
not as an application configuration.

