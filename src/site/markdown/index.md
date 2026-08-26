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

# Apache Commons XML

**Sandbox component.** Apache Commons XML is part of the
[Apache Commons Sandbox](https://commons.apache.org/sandbox/). It is a work in progress, has not been formally
released, and its API, coordinates, and behavior may change without notice. Do not rely on it in production.

Apache Commons XML provides secure-by-default JAXP factory creation,
abstracting over implementation-specific XXE hardening differences between the
stock JDK and external JAXP implementations.


## Why

Any Java library that parses XML has to harden JAXP before handing a factory to user code, and every library ends up
copy-pasting the same hardening snippet. The snippet is fragile: the attributes and features needed to harden a factory
are not standardized, each JAXP implementation exposes a slightly different set, and setting an unknown one throws an
exception that callers routinely swallow. Writing this block correctly for every implementation is real work, and
duplicating it across projects means every project owns the maintenance burden on its own.

Defaults are also uneven. The stock JDK SAX and DOM parsers already prevent external entity resolution through
`FEATURE_SECURE_PROCESSING`, and JAXP 1.5 conformant implementations ship reasonable defaults for most attacks. Others,
such as standalone Xerces, Woodstox, or Saxon's TrAX, need further configuration before they reach the same baseline. A
library author has no control over which implementation is on the classpath at runtime, so the effective security
posture of their code depends on a deployment decision made elsewhere.

This library provides that baseline. Each `XmlFactories` call returns a new factory hardened by an
implementation-specific recipe, so the returned object behaves the same way security-wise regardless of which JAXP
implementation resolved. Security becomes a property of the call, not of the classpath, and there is one place to
update when a new hardening setting becomes available or a default changes.

## Usage

Add the library to your build:

```xml
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-xml</artifactId>
  <version>${commons.release.version}</version>
</dependency>
```

Every method on `XmlFactories` returns a new, hardened factory.
Pick the one that matches the API you already use;
no other configuration is required.
On hardened factories an external resource reference (DTD, entity, schema, stylesheet) is never fetched:
it resolves to empty content,
so the parse continues without it
(see Configuration below).

### Supported runtimes

The library requires OpenJDK 8 or later (or a JDK distribution built from it), or Android API level 19 or later.

The security guarantees are defined only on the OpenJDK family
(see the [Threat Model](threat_model.html)).
No version of Android supports `FEATURE_SECURE_PROCESSING`
(so states [Android's own documentation](https://developer.android.com/reference/javax/xml/parsers/DocumentBuilderFactory#setFeature%28java.lang.String,%20boolean%29)),
so the library secures the platform's parsers as best-effort.
Android's `XmlPullParser` API is not supported:
it is not a JAXP API.

### Supported implementations

Out of the box the library recognizes the stock JDK JAXP implementations, Apache Xerces 2.x, Woodstox, and Saxon-HE. If
a factory resolves to an implementation not covered by any bundled hardening recipe, every `XmlFactories` method throws
`IllegalStateException` with a message naming the unsupported class. Adding support for a new JAXP implementation
requires a code change to this library.

**DOM parsing** via `DocumentBuilderFactory`:

```java
import org.w3c.dom.Document;
import org.apache.commons.xml.XmlFactories;

Document doc = XmlFactories.newDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
```

**SAX parsing** via `SAXParserFactory`:

```java
import org.apache.commons.xml.XmlFactories;

XmlFactories.newSAXParserFactory().newSAXParser().parse(inputStream, myDefaultHandler);
```

**Streaming (StAX) parsing** via `XMLInputFactory`:

```java
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.xml.XmlFactories;

XMLStreamReader reader = XmlFactories.newXMLInputFactory().createXMLStreamReader(inputStream);
```

**XSLT transforms** via `TransformerFactory`:

```java
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.xml.XmlFactories;

XmlFactories.newTransformerFactory()
        .newTransformer(new StreamSource(stylesheet))
        .transform(new StreamSource(inputStream), new StreamResult(outputStream));
```

**XPath queries** via `XPathFactory`:

```java
import javax.xml.xpath.XPathConstants;
import org.w3c.dom.NodeList;
import org.apache.commons.xml.XmlFactories;

NodeList hits = (NodeList) XmlFactories.newXPathFactory()
        .newXPath()
        .evaluate("//item", doc, XPathConstants.NODESET);
```

**W3C XML Schema validation** via `SchemaFactory`:

```java
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.xml.XmlFactories;

XmlFactories.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        .newSchema(new StreamSource(xsdStream))
        .newValidator()
        .validate(new StreamSource(inputStream));
```

### Stylesheets and schemas

The hardening applies to documents parsed through the returned factory. Stylesheets given to
`TransformerFactory.newTransformer(Source)` and schemas given to `SchemaFactory.newSchema(Source)` are read by a parser
the implementation picks internally, and that parser may not be hardened (Saxon's TrAX is one such case, see Building
below). Treat stylesheets and schemas as trusted input, or pre-parse them through a hardened `XmlFactories` parser and
pass the result as a `DOMSource` or `SAXSource`.
A stylesheet also chooses where the transform writes (`xsl:result-document`):
the hardening governs reads only,
so restrict output destinations yourself when running an untrusted stylesheet
(see the [Threat Model](threat_model.html)).

### Transformer handlers and filters

The `SAXTransformerFactory` extension methods, `newTransformerHandler(...)`, `newTemplatesHandler()` and `newXMLFilter(...)`,
if reachable by casting the factory from `XmlFactories.newTransformerFactory()`,
produce handlers, filters and `Templates` carrying the same hardening as the standard entry points:
runtime `document()` resolves to empty content,
and a filter with no caller-set parent parses its input through a hardened reader.
The SAX events you feed into a handler, and a parent reader you set on a filter,
are your own configuration, like any caller-supplied parser.
See the [Threat Model](threat_model.html) for the exact scope.

### Caching and thread-safety

There is no caching or pooling inside `XmlFactories`; callers on a hot path are responsible for their own caching. The
returned factories inherit the thread-safety properties of the underlying JAXP implementation, which in practice means
they are not thread-safe. Create a new factory per thread or synchronize externally.

## Configuration

The hardened factories need no configuration.
When a document references an external resource
(a DTD, an external entity, a schema, an XInclude target, or an XSLT document),
the hardening layer resolves the reference to an empty stream:
nothing is fetched,
nothing leaks into the result,
and the parse continues wherever the implementation can proceed with empty content.
This forgiving default accommodates documents that merely carry such references without needing them.

If your application should reject such documents instead of parsing them,
tighten the factory yourself.
The hardening floor stays underneath whatever you configure,
so the tightening carries **no security weight**
and can be as strict as the application needs:

- Set a stricter feature on the factory,
  for example `http://apache.org/xml/features/disallow-doctype-decl`
  to reject every document carrying a DOCTYPE,
  on implementations that support the feature.
- Install a resolver that throws.
  A caller-supplied `EntityResolver`, `XMLResolver`, `LSResourceResolver` or `URIResolver`
  is consulted before the hardening floor,
  so an allow-list and a deny-all are both one resolver away.

As a temporary debugging measure,
set the system property `org.apache.commons.xml.throwOnUnresolved` to `true`:
every unresolved external reference is then rejected with the resolution hook's exception,
and the message names the denied resource.
The property is read at resolution time,
so it can be toggled on a running application;
treat it as a diagnostic switch,
not as an application configuration.

