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
# Apache Commons Secure XML Threat Model

## Introduction

This page amends [Apache Commons Security page](https://commons.apache.org/security.html).

For information about reporting or asking questions about security, please see the [Apache Commons Security page](https://commons.apache.org/security.html).

This page lists all security vulnerabilities fixed in released versions of this component.

Please note that binary patches are never provided. If you need to apply a source code patch, use the building instructions for the component version that you are using.

If you need help on building this component or other help on following the instructions to mitigate the known vulnerabilities listed here, please send your questions to the
public [user mailing list](mail-lists.html).

If you have encountered an unlisted security vulnerability or other unexpected behavior that has security impact, or if the descriptions here are incomplete, please report them privately to the Apache Security Team. Thank you.

## Threat Model

This is the threat model for the **1.0.x** release line.
It is versioned with the library: a report against a released version is triaged against the model as it stood at that version, not at `HEAD`.
A finding that breaks something listed under [What is in scope](#what-is-in-scope) should be reported through the channel above;
a finding that falls under [What is out of scope](#what-is-out-of-scope) will be closed citing this section.

### Scope and Intended Use

This library is a helper for **safely creating JAXP factories**. Each `XxxFactory.newYyy()` method returns a
new, secured factory whose parsers reject the common XML attacks (external entity / DTD resolution, XXE, SSRF through
external references, and entity-expansion denial of service such as Billion Laughs). The exact guarantee each factory
makes is documented in the Javadoc:

https://commons.apache.org/index/commons-secure-xml/apidocs/org/apache/commons/xml/secure/package-summary.html

The securing applies to the factory and to the parsers, readers, transformers, validators, schemas and XPath objects it produces.
It governs what those objects read;
what a transform writes is the stylesheet author's capability
(see **Transform output destinations** under [What is out of scope](#what-is-out-of-scope)).

### Adversary Model and Trust Boundary

The adversary is whoever controls the XML an application parses, together with any external system an XML document tries to reach through an entity, DTD, schema, stylesheet, or XInclude reference.
The securing exists to stop that untrusted document from reading local resources, reaching the network, or exhausting memory or CPU.

The trust boundary is the factory as returned by `org.apache.commons.xml.secure`. The XML handed to a parser, reader,
transformer, validator or schema produced by that factory is **untrusted**; the configuration of the factory
is **trusted**, and keeping it as delivered is the caller's responsibility. A caller running in the same
process can always reconfigure or replace the factory, so such a caller is not an adversary this model
defends against: that is the reason reconfiguration moves a report
[out of scope](#what-is-out-of-scope).

The same holds for parser objects the caller constructs outside the library and passes in:
an `XMLReader` wrapped in a `SAXSource`,
a StAX reader inside a `StAXSource`,
or a document parsed elsewhere and handed over as a `DOMSource`
are **trusted** configuration, not untrusted input.
The library secures what it creates;
it does not re-harden what you built,
because your reader's settings are indistinguishable from configuration you chose deliberately.

### What is in Scope

- The securing recipes applied by `org.apache.commons.xml.secure`.
  Every implementation of JAXP 1.4 or later is in scope,
  as long as it respects the contract of the features, attributes, and properties the recipes use.
  An implementation that cannot accept a required setting makes the factory method throw
  instead of returning an unsecured factory.

  The recipes for Android's Expat/KXmlParser are applied as best-effort and carry no guarantee
  (see **Supported runtimes** under [Assumptions about the environment](#assumptions-about-the-environment)).
- A factory returned by `org.apache.commons.xml.secure`, used as delivered, that fails to provide a guarantee the Javadoc states it
  provides. The guarantee covers the documented entry points of each returned factory type,
  including the `SAXTransformerFactory` extension methods when the returned `TransformerFactory` exposes them.

### Assumptions about the Environment

The library does not open network connections,
spawn processes,
install signal handlers,
or read environment variables of its own:
each `org.apache.commons.xml.secure` factory method only configures and returns a JAXP factory.
Which securing recipe applies depends on the JAXP implementation present on the classpath.

**Supported runtimes**

The guarantees are defined on a single runtime family:
OpenJDK 8 or later (and JDK distributions built from it).
On these runtimes the recognized parsers apply the processing limits the guarantees rely on.

Android, on every API level, carries no guarantee:
no version of Android supports `FEATURE_SECURE_PROCESSING`
(so states [Android's own documentation](https://developer.android.com/reference/javax/xml/parsers/DocumentBuilderFactory#setFeature%28java.lang.String,%20boolean%29)),
the setting the guaranteed processing limits build on.
The library still secures Android's parsers as best-effort,
tested as complete starting with API level 33
(see [Supported runtimes](index.html) on the main page),
but a report demonstrated only on Android is [out of scope](#what-is-out-of-scope) on any API level.

**Honored JAXP contracts**

The in-scope requirement that an implementation respect the contract of the settings a recipe uses
(see [What is in scope](#what-is-in-scope))
extends to the JAXP API contracts themselves.
In particular:

- A method handed a `SAXSource` carrying an `XMLReader` parses with that reader,
  as the [`SAXSource` contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/javax/xml/transform/sax/SAXSource.html#%3Cinit%3E(org.xml.sax.XMLReader,org.xml.sax.InputSource)) requires.
- A method handed a `StAXSource` reads from the stream or event reader it carries,
  as the [`StAXSource` contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/javax/xml/transform/stax/StAXSource.html) implies:
  the reader must arrive positioned on `START_DOCUMENT` or `START_ELEMENT`,
  and the source is consumed during processing.

The securing injects hardened readers this way,
so an implementation that ignores the supplied reader and parses with an internal parser of its own
parses outside the securing.
Guarding against such an implementation would be a valid *hardening* of this library,
but the substitution itself is a contract violation in that implementation,
not a vulnerability here:
a report built on one is triaged `OUT-OF-SCOPE: foreign implementation`.

**XInclude resolution**

XInclude is the converse case: JAXP specifies no contract at all.
`setXIncludeAware` turns the processor on,
but no part of the API says which resolver — if any — a processor consults for an `xi:include` href.

The XInclude guarantee is therefore restricted to implementations that follow the Xerces convention
of routing an `xi:include` fetch through the `EntityResolver`.
An implementation whose XInclude processor resolves an href without consulting the entity resolver
fetches outside the securing,
and a report demonstrated only there is [out of scope](#what-is-out-of-scope).

**System properties that modify behavior**

The library reads a single system property of its own,
`org.apache.commons.xml.secure.throwOnUnresolved`:
when set to `true`,
every secured factory rejects an unresolved external reference with an exception
instead of resolving it to empty content.
Either way the resource is not fetched,
so the property selects an error-reporting style,
not a security posture.

The library enables secure processing (`FEATURE_SECURE_PROCESSING`) on every recognized parser that supports it
(no Android parser does, see **Supported runtimes** above)
and leaves the resulting processing limits (entity expansion, element depth, attribute count, and similar)
at the implementation's own secure default. Those defaults differ by implementation, and on the stock JDK by
JDK version and the standard `jdk.xml.*` limit properties the JDK itself reads:

- On the stock JDK, secure processing honors the `jdk.xml.*` limit properties (for example `jdk.xml.entityExpansionLimit`,
  default `2500` on JDK 25 and `64000` on JDK 8 through 21). These are trusted deployment configuration: an operator may
  set one to tighten (or loosen) a limit globally, but loosening through one is reconfiguration, treated like loosening
  any other reserved setting (see [What is out of scope](#what-is-out-of-scope)).
- The bundled parsers apply their own hardcoded secure defaults instead (for example external Xerces and Woodstox cap
  entity expansion at `100000`) and do not read `jdk.xml.*`.

On the supported runtimes (see **Supported runtimes** above),
every one of these defaults still bounds entity expansion tightly enough to reject entity-expansion denial of service
such as Billion Laughs.

**Reserved Settings (must not be loosened)**

The library MAY rely on the following features, attributes and properties staying as configured. They are reserved because
they govern external resource access, DTD, entity or schema handling, the installation of a resolver, or processing
limits; loosening any of them, on the returned factory or on a parser, reader, transformer, validator or schema it
produces, breaks the securing for that instance.

- `http://apache.org/xml/features/disallow-doctype-decl`
- `http://apache.org/xml/features/nonvalidating/load-external-dtd`
- `http://apache.org/xml/properties/internal/entity-resolver`
- `http://javax.xml.XMLConstants/feature/secure-processing`
- `http://saxon.sf.net/feature/allow-external-functions`
- `http://saxon.sf.net/feature/allowedProtocols`
- `http://xml.org/sax/features/external-general-entities`
- `http://xml.org/sax/features/external-parameter-entities`
- `javax.xml.stream.isSupportingExternalEntities`
- `javax.xml.stream.supportDTD`
- the implementation's secure-processing limits (entity expansion, element depth, attribute count, and similar)

This list is not exhaustive:
any other feature, attribute, property, or system property that
grants access to an external resource,
relaxes DTD or entity processing,
installs a resolver the securing layer does not wrap
(like the Xerces-specific `http://apache.org/xml/properties/internal/entity-resolver`, listed above),
or raises a processing limit
is reserved on the same terms.

Installing a resolver through the typed `set*Resolver` methods, the `DefaultHandler` passed to `SAXParser.parse`, or the resolver properties listed under **Settings you may modify** does not loosen the securing:
those paths are wrapped by a non-removable floor.
Neither does setting the JAXP 1.5 external-access properties, also listed there:
a resource supplied by a resolver bypasses their checks,
so on a secured instance they never come into play.

**Settings You May Modify**

The following are security-relevant but safe to change on a returned factory: the protection they appear to govern is
enforced by the reserved settings above, which a caller cannot lift.

- **Resolvers.** You may install your own resolver: the securing floor wraps it instead of being replaced, so it stays
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

  An opted-in resource stays on the floor:
  a `Source` returned by a `URIResolver` is re-parsed through a secured reader
  (a `DOMSource`, or a `SAXSource` carrying your own reader, is used as returned).

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

- **External-access properties.** You may set the JAXP 1.5 external-access properties, to any value:
  - `http://javax.xml.XMLConstants/property/accessExternalDTD`,
  - `http://javax.xml.XMLConstants/property/accessExternalSchema`,
  - `http://javax.xml.XMLConstants/property/accessExternalStylesheet`.

  The securing is independent of them:
  a resource supplied by a resolver bypasses these checks,
  and the securing floor resolves or ignores every external reference,
  so on a secured instance the properties never come into play —
  no value loosens the securing, and no value is needed to keep it
  (see [why the securing does not build on them](apidocs/index.html#external-access-properties) in the Javadoc overview).
  The same independence holds for their
  `javax.xml.accessExternalDTD`, `javax.xml.accessExternalSchema` and `javax.xml.accessExternalStylesheet`
  system-property counterparts.
  Known JDK defects apply the checks even to a resolver-supplied document;
  they fail closed:
  a legitimately resolved resource may be denied, never fetched.

- **Internal parser selection.**
  On the stock JDK TrAX, XPath, and schema implementations
  you may set [`jdk.xml.overrideDefaultParser`](https://docs.oracle.com/en/java/javase/25/docs/api/java.xml/module-summary.html#jdk.xml.overrideDefaultParser)
  to switch their internal parses from the JDK parsers to a `ServiceLoader`-resolved parser.
  Whichever parser is selected, it is secured,
  so the setting carries no security weight.

### What is Out of Scope

A returned factory is secured as delivered; reconfiguring it is a decision to take over securing for that instance,
and reports against a factory reconfigured in any of the ways below are out of scope.

- **Modifying a reserved setting.** Loosening any feature, attribute or property reserved under
  [Assumptions about the environment](#assumptions-about-the-environment).
- **A resolver that resolves untrusted resources.** Installing a resolver does not lift the floor (see
  **Settings you may modify** above), but your resolver is consulted ahead of it, so any resource it resolves (returns
  content for) is fetched, including one named by an untrusted identifier. Which resources it resolves is your policy to
  enforce.
- **Caller-supplied top-level URIs.** A URI passed directly to a parse call (`DocumentBuilder.parse(String)`,
  `StreamSource(systemId)`, a `SAXSource` built from a system id) is fetched as-is by the JAXP implementation without
  consulting the securing layer. Restrict it yourself if the URI is untrusted.
- **Caller-supplied parser instances.**
  A parser built outside `org.apache.commons.xml.secure` and handed to a produced instance is used as configured:
  a `SAXSource` carrying its own `XMLReader`,
  a `StAXSource` carrying a stream or event reader,
  or a `DOMSource` holding a document parsed elsewhere.
  Its settings are yours, including permissive ones.
  To parse with your own reader under the securing guarantees,
  obtain it from `SecureSAXParserFactory.newInstance()`
  before wrapping it in a `SAXSource`.
- The behavior of a JAXP implementation that does not respect the contract of the settings a securing recipe requires
  (the factory method throws rather than returning an unsecured factory),
  and any defect in the underlying JAXP implementation itself.
- **XInclude outside the Xerces convention.**
  JAXP does not specify which resolver an XInclude processor consults,
  so the guarantee is restricted to implementations that route an `xi:include` fetch through the entity resolver
  (see **XInclude resolution** under [Assumptions about the environment](#assumptions-about-the-environment)).
- **Android, on any API level.**
  No version of Android supports `FEATURE_SECURE_PROCESSING`,
  so the securing there is best-effort and no guarantee is defined
  (see **Supported runtimes** under [Assumptions about the environment](#assumptions-about-the-environment)).
- **Transform output destinations.**
  The securing governs what a parse or transform reads;
  it does not confine what a transform writes.
  A stylesheet's output-producing instructions,
  `xsl:result-document` in particular,
  write wherever the stylesheet directs, within the runtime's permissions:
  running a stylesheet grants its author that capability,
  so restricting destinations when the stylesheet is untrusted is the operator's responsibility
  (an output resolver of the implementation, filesystem permissions, or process sandboxing).
  A path-traversal or file-write report through a stylesheet's output instructions is out of scope.

### Downstream Responsibility

Use the factory as returned. If you reconfigure it, you take over securing for that instance and are responsible for
re-establishing any protection you remove.

### Known Non-Findings

XML-security scanners and static analyzers routinely flag the parsers this library produces. The following
are **not** vulnerabilities under this model:

- A claim that a factory or instance produced by `org.apache.commons.xml.secure` is unsafe, without showing that a reserved
  setting was loosened, a resolver was installed, or an untrusted top-level URI was passed (see
  [Assumptions about the environment](#assumptions-about-the-environment) and
  [What is out of scope](#what-is-out-of-scope)). As delivered, the instance is secured; the bare presence
  of a `SAXParser`, `DocumentBuilder`, `XMLReader`, `Transformer`, `Validator` or `Schema` is not a finding.
- XXE, external-entity, SSRF-through-external-reference, or entity-expansion (Billion Laughs) reports against
  a factory used as delivered. Blocking these is exactly what the securing does. A working proof against an
  unmodified instance is a `VALID` finding (see below); a scanner that pattern-matches on parser type is not.
- A report demonstrated only on Android,
  where the securing is best-effort and no guarantee is defined
  (see **Supported runtimes** under [Assumptions about the environment](#assumptions-about-the-environment)).
- Reports against an instance after the caller installed a resolver (including the `DefaultHandler` passed to
  `SAXParser.parse(..., DefaultHandler)`) or loosened a reserved setting.
- Reports demonstrated on a parser the reporter configured themselves:
  for example enabling `external-general-entities` on a self-built `XMLReader`,
  wrapping it in a `SAXSource`,
  and showing that a produced `Transformer`, `Validator` or `SchemaFactory` resolves the entity.
  The permissive settings belong to the reporter's own reader,
  not to an instance this library produced
  (see **Caller-supplied parser instances** under [What is out of scope](#what-is-out-of-scope)).
- Reports about a top-level URI the caller passed directly to a parse call. That URI is fetched as-is and is
  the caller's to validate.
- A path-traversal or file-write report through `xsl:result-document` or another output-producing
  instruction of a stylesheet
  (see **Transform output destinations** under [What is out of scope](#what-is-out-of-scope)).
- Reports in a JAXP implementation that does not respect the contract of the settings a securing recipe
  requires: `org.apache.commons.xml.secure` factory method throws rather than returning an unsecured factory, so there is no instance to attack.

### Triage Dispositions

A report judged against this model receives exactly one of:

| Disposition | Meaning |
| --- | --- |
| `VALID` | A factory or instance used as delivered fails to provide a guarantee its Javadoc states (for example, a secured parser still resolves an external entity, or a documented processing limit is not applied). |
| `OUT-OF-SCOPE: reconfigured` | A reserved setting was loosened, or a resolver was installed, on the factory or a produced instance before the reported behavior (see [What is out of scope](#what-is-out-of-scope)). |
| `OUT-OF-SCOPE: caller input` | The behavior follows from a top-level URI, a parser instance the caller constructed outside the library, or other input the caller passed directly to a parse call. |
| `OUT-OF-SCOPE: foreign implementation` | The behavior is in a JAXP implementation that does not respect the contract of the settings a securing recipe requires, or is a defect in the underlying JAXP implementation itself. |
| `OUT-OF-SCOPE: unsupported runtime` | The behavior is demonstrated only on a runtime the guarantees are not defined on, such as Android on any API level (see **Supported runtimes** under [Assumptions about the environment](#assumptions-about-the-environment)). |
| `MODEL-GAP` | The report fits none of the above. The model is then incomplete: revise it rather than making an ad-hoc call. |

### Conditions That Would Change This Model

Revise this model when any of the following change:
a new `org.apache.commons.xml.secure` factory or other public surface;
support for a JAXP implementation beyond those listed under [What is in scope](#what-is-in-scope);
a change to the supported runtimes (see **Supported runtimes** under [Assumptions about the environment](#assumptions-about-the-environment));
a new reserved setting;
or a report that cannot be routed to one of the dispositions above.

## Security Vulnerabilities

None.

## Safe Deserialization
For information about safe deserialization, please see [Safe Deserialization](https://commons.apache.org/io/description.html#Safe_Deserialization).
