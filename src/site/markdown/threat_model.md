<!---
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
# Apache Commons XML Threat Model

## Introduction

This page amends [Apache Commons Security page](https://commons.apache.org/security.html).

For information about reporting or asking questions about security, please see the [Apache Commons Security page](https://commons.apache.org/security.html).

This page lists all security vulnerabilities fixed in released versions of this component.

Please note that binary patches are never provided. If you need to apply a source code patch, use the building instructions for the component version that you are using.

If you need help on building this component or other help on following the instructions to mitigate the known vulnerabilities listed here, please send your questions to the
public [user mailing list](mail-lists.html).

If you have encountered an unlisted security vulnerability or other unexpected behavior that has security impact, or if the descriptions here are incomplete, please report them privately to the Apache Security Team. Thank you.

## Threat Model

This is the threat model for the **0.1.x** release line.
It is versioned with the library: a report against a released version is triaged against the model as it stood at that version, not at `HEAD`.
A finding that breaks something listed under [What is in scope](#what-is-in-scope) should be reported through the channel above;
a finding that falls under [What is out of scope](#what-is-out-of-scope) will be closed citing this section.

### Scope and intended use

This library is a helper for **safely creating JAXP factories**. Each `XmlFactories.newXxxFactory()` method returns a
fresh, hardened factory whose parsers reject the common XML attacks (external entity / DTD resolution, XXE, SSRF through
external references, and entity-expansion denial of service such as Billion Laughs). The exact guarantee each factory
makes is documented in the Javadoc:

https://commons.apache.org/sandbox/commons-xml/apidocs/org/apache/commons/xml/factory/XmlFactories.html

The hardening applies to the factory and to the parsers, readers, transformers, validators and schemas it produces.

### Adversary model and trust boundary

The adversary is whoever controls the XML an application parses, together with any external system an XML
document tries to reach through an entity, DTD, schema, stylesheet, or XInclude reference. The hardening
exists to stop that untrusted document from reading local resources, reaching the network, or exhausting
memory or CPU.

The trust boundary is the factory as returned by `XmlFactories`. The XML handed to a parser, reader,
transformer, validator or schema produced by that factory is **untrusted**; the configuration of the factory
is **trusted**, and keeping it as delivered is the caller's responsibility. A caller running in the same
process can always reconfigure or replace the factory, so such a caller is not an adversary this model
defends against: that is the reason reconfiguration moves a report
[out of scope](#what-is-out-of-scope).

### What is in scope

- The hardening recipes applied by `XmlFactories` to the JAXP implementations it recognizes (stock JDK, Apache Xerces,
  Xalan, Saxon, Woodstox, and Android's Expat/KXmlParser).
- A factory returned by `XmlFactories`, used as delivered, that fails to provide a guarantee the Javadoc states it
  provides.

### Assumptions about the environment

The library does not open network connections,
spawn processes,
install signal handlers,
or read environment variables of its own:
each `XmlFactories` method only configures and returns a JAXP factory.
Which hardening recipe applies depends on the JAXP implementation present on the classpath.

**System properties that modify behavior**

The library reads no system property of its own. It enables secure processing (`FEATURE_SECURE_PROCESSING`) on every
recognized parser and leaves the resulting processing limits (entity expansion, element depth, attribute count, and
similar) at the implementation's own secure default. Those defaults differ by implementation, and on the stock JDK by
JDK version and the standard `jdk.xml.*` limit properties the JDK itself reads:

- On the stock JDK, secure processing honors the `jdk.xml.*` limit properties (for example `jdk.xml.entityExpansionLimit`,
  default `2500` on JDK 25 and `64000` on JDK 8 through 21). These are trusted deployment configuration: an operator may
  set one to tighten (or loosen) a limit globally, but loosening through one is reconfiguration, treated like loosening
  any other reserved setting (see [What is out of scope](#what-is-out-of-scope)).
- The bundled parsers apply their own hardcoded secure defaults instead (for example external Xerces and Woodstox cap
  entity expansion at `100000`) and do not read `jdk.xml.*`.

Every one of these defaults still bounds entity expansion tightly enough to reject entity-expansion denial of service
such as Billion Laughs.

**Reserved settings (must not be loosened)**

The library MAY rely on the following features, attributes and properties staying as configured. They are reserved because
they govern external resource access, DTD, entity or schema handling, the installation of a resolver, or processing
limits; loosening any of them, on the returned factory or on a parser, reader, transformer, validator or schema it
produces, breaks the hardening for that instance.

- `http://apache.org/xml/features/disallow-doctype-decl`
- `http://apache.org/xml/features/nonvalidating/load-external-dtd`
- `http://apache.org/xml/properties/internal/entity-resolver`
- `http://javax.xml.XMLConstants/feature/secure-processing`
- `http://javax.xml.XMLConstants/property/accessExternalDTD`
- `http://javax.xml.XMLConstants/property/accessExternalSchema`
- `http://javax.xml.XMLConstants/property/accessExternalStylesheet`
- `http://saxon.sf.net/feature/allow-external-functions`
- `http://saxon.sf.net/feature/allowedProtocols`
- `http://xml.org/sax/features/external-general-entities`
- `http://xml.org/sax/features/external-parameter-entities`
- `javax.xml.stream.isSupportingExternalEntities`
- `javax.xml.stream.supportDTD`
- `jdk.xml.overrideDefaultParser`
- the implementation's secure-processing limits (entity expansion, element depth, attribute count, and similar)

This list is not exhaustive:
any other feature, attribute, property, or system property that
grants access to an external resource,
relaxes DTD or entity processing,
installs a resolver the hardening layer does not wrap
(like the Xerces-specific `http://apache.org/xml/properties/internal/entity-resolver`, listed above),
or raises a processing limit
is reserved on the same terms.

Installing a resolver through the typed `set*Resolver` methods, the `DefaultHandler` passed to `SAXParser.parse`, or the resolver properties listed under **Settings you may modify** does not loosen the hardening:
those paths are wrapped by a non-removable floor.

**Settings you may modify**

The following are security-relevant but safe to change on a returned factory: the protection they appear to govern is
enforced by the reserved settings above, which a caller cannot lift.

- **Resolvers.** You may install your own resolver: the hardening floor wraps it instead of being replaced, so it stays
  in force. This covers the typed setters and the resolver properties:
    - `setEntityResolver(...)` (DOM and SAX), including the `DefaultHandler` passed to `SAXParser.parse(..., DefaultHandler)`,
    - `setResourceResolver(...)` (schema compilation and validation),
    - `setURIResolver(...)` (XSLT),
    - `setXMLResolver(...)` and the equivalent StAX resolver properties:
        - `com.ctc.wstx.dtdResolver`,
        - `com.ctc.wstx.entityResolver`,
        - `com.ctc.wstx.undeclaredEntityResolver`,
        - `javax.xml.stream.resolver`.

  Your resolver is consulted first, but the floor denies or ignores whatever it leaves unresolved.
  It therefore *must* resolve every resource you need available: a `null` return blocks the lookup,
  it does not fall through to a fetch.

- **Validation.** You may turn on DTD or XSD validation, using these methods and features/properties:
  - `setSchema(Schema)`,
  - `setValidating(true)`,
  - `http://xml.org/sax/features/validation`,
  - `http://apache.org/xml/features/validation/schema`,
  - `http://java.sun.com/xml/jaxp/properties/schemaLanguage`,
  - `http://java.sun.com/xml/jaxp/properties/schemaSource`,
  - `http://apache.org/xml/properties/schema/external-schemaLocation`,
  - `http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation`.

  An external DTD or schema named through any of these is still refused, so supply the schema yourself (in memory through
  `setSchema` / `schemaSource`, or by installing a resolver that resolves the resource and does not return `null`).

- **XInclude.** You may turn on XInclude support, using these methods and features/properties:
  - `setXIncludeAware(true)`,
  - `http://apache.org/xml/features/xinclude`.

  As in the previous case, you need to provide a secure resolver.

### What is out of scope

A returned factory is hardened as delivered; reconfiguring it is a decision to take over hardening for that instance,
and reports against a factory reconfigured in any of the ways below are out of scope.

- **Modifying a reserved setting.** Loosening any feature, attribute or property reserved under
  [Assumptions about the environment](#assumptions-about-the-environment).
- **A resolver that resolves untrusted resources.** Installing a resolver does not lift the floor (see
  **Settings you may modify** above), but your resolver is consulted ahead of it, so any resource it resolves (returns
  content for) is fetched, including one named by an untrusted identifier. Which resources it resolves is your policy to
  enforce.
- **Caller-supplied top-level URIs.** A URI passed directly to a parse call (`DocumentBuilder.parse(String)`,
  `StreamSource(systemId)`, a `SAXSource` built from a system id) is fetched as-is by the JAXP implementation without
  consulting the hardening layer. Restrict it yourself if the URI is untrusted.
- The behavior of a JAXP implementation that `XmlFactories` does not recognize (it throws rather than returning an
  unhardened factory), and any defect in the underlying JAXP implementation itself.

### Downstream responsibility

Use the factory as returned. If you reconfigure it, you take over hardening for that instance and are responsible for
re-establishing any protection you remove.

### Known non-findings

XML-security scanners and static analyzers routinely flag the parsers this library produces. The following
are **not** vulnerabilities under this model:

- A claim that a factory or instance produced by `XmlFactories` is unsafe, without showing that a reserved
  setting was loosened, a resolver was installed, or an untrusted top-level URI was passed (see
  [Assumptions about the environment](#assumptions-about-the-environment) and
  [What is out of scope](#what-is-out-of-scope)). As delivered, the instance is hardened; the bare presence
  of a `SAXParser`, `DocumentBuilder`, `XMLReader`, `Transformer`, `Validator` or `Schema` is not a finding.
- XXE, external-entity, SSRF-through-external-reference, or entity-expansion (Billion Laughs) reports against
  a factory used as delivered. Blocking these is exactly what the hardening does. A working proof against an
  unmodified instance is a `VALID` finding (see below); a scanner that pattern-matches on parser type is not.
- Reports against an instance after the caller installed a resolver (including the `DefaultHandler` passed to
  `SAXParser.parse(..., DefaultHandler)`) or loosened a reserved setting.
- Reports about a top-level URI the caller passed directly to a parse call. That URI is fetched as-is and is
  the caller's to validate.
- Reports in a JAXP implementation this library does not recognize: `XmlFactories` throws rather than
  returning an unhardened factory, so there is no instance to attack.

### Triage dispositions

A report judged against this model receives exactly one of:

| Disposition | Meaning |
| --- | --- |
| `VALID` | A factory or instance used as delivered fails to provide a guarantee its Javadoc states (for example, a hardened parser still resolves an external entity, or a documented processing limit is not applied). |
| `OUT-OF-SCOPE: reconfigured` | A reserved setting was loosened, or a resolver was installed, on the factory or a produced instance before the reported behavior (see [What is out of scope](#what-is-out-of-scope)). |
| `OUT-OF-SCOPE: caller input` | The behavior follows from a top-level URI, or other input, the caller passed directly to a parse call. |
| `OUT-OF-SCOPE: foreign implementation` | The behavior is in a JAXP implementation `XmlFactories` does not recognize, or in the underlying JAXP implementation itself. |
| `MODEL-GAP` | The report fits none of the above. The model is then incomplete: revise it rather than making an ad-hoc call. |

### Conditions that would change this model

Revise this model when any of the following change: a new `XmlFactories` factory method or other public
surface; support for a JAXP implementation beyond those listed under [What is in scope](#what-is-in-scope);
a new reserved setting; or a report that cannot be routed to one of the dispositions above.

## Security Vulnerabilities

None.

## Safe Deserialization
For information about safe deserialization, please see [Safe Deserialization](https://commons.apache.org/io/description.html#Safe_Deserialization).
