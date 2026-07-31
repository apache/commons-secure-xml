/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz
 * SPDX-License-Identifier: Apache-2.0
 */
package org.apache.commons.xml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Shared fixtures for attack tests.
 *
 * <p>The hardened-side helpers come in three flavors, distinguished by their suffix:</p>
 *
 * <ul>
 *   <li>{@code assert*Blocks(...)} runs the payload through a hardened factory from {@link XmlFactories} and asserts the parse throws. Used when the hardening
 *       layer is expected to reject the attack outright.</li>
 *   <li>{@code assert*DoesNotLeak(...)} runs the payload through a hardened factory and asserts the parse completes without throwing and without producing the
 *       {@link #LEAKED_MARKER} string. Used when the hardening contract guarantees the parse succeeds but never resolves the external resource (for example,
 *       {@code XERCES_LOAD_EXTERNAL_DTD=false} silently skipping the external subset, with the body's undeclared entity reference dropped per XML 1.0
 *       §4.1).</li>
 *   <li>{@code assert*BlocksOrDoesNotLeak(...)} accepts either of the previous two outcomes. Used where the same hardening contract surfaces differently across
 *       providers (for example, stock-JDK XSLTC throws via {@code ACCESS_EXTERNAL_DTD} while Apache Xalan silently skips because its source-rewrite routes parsing
 *       through a {@code XERCES_LOAD_EXTERNAL_DTD=false} reader).</li>
 * </ul>
 *
 * <p>DOM tests that depend on user-defined entity machinery should gate themselves with {@link org.junit.jupiter.api.Assumptions#assumeTrue} on
 * {@link #DOM_RESOLVES_INTERNAL_ENTITIES} so they skip on platforms (such as Android with KXmlParser) whose DOM parser does not surface the entity events that
 * the strict {@link #assertDomBlocks} assertion expects.</p>
 *
 * <p>The permissive-side positive controls mirror the hardened-side verbs with an {@code assertPermissive*} prefix: {@code assertPermissive*Parses} for direct
 * parsing, {@code assertPermissive*Compiles} for {@link SchemaFactory} / {@link TransformerFactory} compilation, {@code assertPermissiveTransformerTransforms}
 * for {@code Transformer.transform}, {@code assertPermissiveValidatorValidates} for {@code Validator.validate}. Both sides perform the same operation; the
 * prefix marks which factory hardening level the assertion is set against.</p>
 *
 * <p>Schema and Templates assertions take a {@link Source} so the same helper covers both inline-string payloads and resource-backed wrappers; build the
 * source via {@link #streamSource(String)} for a string payload or {@link #resourceSource(String)} for a file under {@code src/test/resources/leaked/}. The
 * resource form preserves the system id so relative {@code xs:include} / {@code xs:import} / {@code xs:redefine} / {@code xsl:include} / {@code xsl:import}
 * URIs resolve normally.</p>
 *
 * <p>The two generic primitives {@link #assertParseFails} and {@link #assertParseSucceeds} are exposed for tests that need to compose a non-standard factory
 * call.</p>
 */
final class AttackTestSupport {

    /**
     * Strict reporter installed on every hardened factory, parser, validator and transformer in the helpers below.
     *
     * <p>The hardening layer signals every blocked external fetch and every SAX-fatal it could not silently skip via the standard JAXP error channels:
     * {@link ErrorListener#error(TransformerException)} / {@link ErrorListener#fatalError(TransformerException) fatalError} on the TrAX side and
     * {@link ErrorHandler#error(SAXParseException) error} / {@link ErrorHandler#fatalError(SAXParseException) fatalError} on the SAX side. Both Apache Xalan's
     * {@code DefaultErrorHandler(false)} and Saxon's {@code StandardErrorListener} are pathologically lenient defaults that swallow these events; SAX's
     * {@link DefaultHandler} treats {@code error} as a no-op. The test fixture replaces those defaults with a strict reporter that re-throws on every reported
     * error or fatalError so the helpers can observe the block via the same mechanism the spec uses to surface it. Warnings stay silent: they are not security
     * signals.</p>
     */
    static final class StrictReporter implements ErrorListener, ErrorHandler {

        @Override
        public void error(final SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(final TransformerException exception) throws TransformerException {
            throw exception;
        }

        @Override
        public void fatalError(final SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(final TransformerException exception) throws TransformerException {
            throw exception;
        }

        @Override
        public void warning(final SAXParseException exception) {
            // not a security signal
        }

        @Override
        public void warning(final TransformerException exception) {
            // not a security signal
        }
    }
    /**
     * Trivial W3C XML Schema that validates {@link #xmlBody(String)} output.
     */
    static final String BENIGN_SCHEMA =
            "<?xml version=\"1.0\"?>\n"
            + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
            + "  <xs:element name=\"root\">\n"
            + "    <xs:complexType>\n"
            + "      <xs:sequence>\n"
            + "        <xs:element name=\"child\" type=\"xs:string\"/>\n"
            + "      </xs:sequence>\n"
            + "    </xs:complexType>\n"
            + "  </xs:element>\n"
            + "</xs:schema>\n";
    /**
     * Benign payload used to probe whether the DOM parser inlines a user-defined internal general entity into the tree.
     */
    private static final String DOM_INTERNAL_ENTITY_PROBE =
            "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE root [<!ENTITY foo \"bar\">]>\n"
            + "<root>&foo;</root>";
    /**
     * Set to {@code true} when the platform's DOM parser supports user-defined internal entities.
     *
     * <p>Android's {@code KXmlParser} currently fails this test.</p>
     */
    static final boolean DOM_RESOLVES_INTERNAL_ENTITIES = probeDomResolvesInternalEntities();
    /** {@code true} when running on Android (Dalvik / ART), {@code false} on any standard JVM. Probed once via {@code Class.forName} on {@code android.os.Build}. */
    static final boolean IS_ANDROID = probeAndroid();
    /**
     * URL form of the JDK's entity-expansion limit property.
     */
    private static final String JDK_ENTITY_EXPANSION_LIMIT = "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit";
    /**
     * Text planted in every fixture under {@code src/test/resources/leaked/}.
     *
     * <p>Tests that capture a parser's output assert this string is absent: it can only appear if the hardened parser fetched the external resource, so its
     * presence is the leak signal.</p>
     */
    static final String LEAKED_MARKER = "All your base are belong to us";
    static final StrictReporter STRICT_REPORTER = new StrictReporter();

    /**
     * Asserts a hardened DOM parse of the payload throws.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link XmlFactories#newDocumentBuilderFactory()}; only a thrown exception passes.</p>
     */
    static void assertDomBlocks(final String payload) {
        assertParseFails(() -> strictDocumentBuilder(XmlFactories.newDocumentBuilderFactory()).parse(inputSource(payload)), "DOM", SAXException.class);
    }

    /**
     * Asserts a hardened DOM parse completes without throwing and without leaked content.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link XmlFactories#newDocumentBuilderFactory()}; use this when the hardening guarantee is "the parse
     * succeeds but never resolves the external resource", for example, when {@code XERCES_LOAD_EXTERNAL_DTD=false} silently skips the external subset.</p>
     */
    static void assertDomDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> domParseAndCaptureText(payload), "DOM");
    }

    /**
     * Asserts a hardened DOM parse succeeds.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link XmlFactories#newDocumentBuilderFactory()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertDomParses(final String payload) {
        assertParseSucceeds(() -> strictDocumentBuilder(XmlFactories.newDocumentBuilderFactory()).parse(inputSource(payload)), "DOM");
    }

    /**
     * Skeleton for every {@code assert*BlocksOrDoesNotLeak} helper.
     *
     * <p>Treats a thrown exception of one of the {@code expected} types as "hardening blocked at parse" (acceptable); otherwise asserts the captured output
     * omits {@link #LEAKED_MARKER}. A throw whose type does not match {@code expected} fails the test, so unrelated failures (for example, a {@link HardeningException}
     * because no recipe matched the JAXP implementation) cannot be silently accepted as a clean block.</p>
     *
     * @param action      The parse to execute, returning the captured output text checked for {@link #LEAKED_MARKER}.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception types any of which the hardening layer may surface as a clean rejection.
     */
    @SafeVarargs
    private static void assertNoLeakOrThrows(final ThrowingSupplier<String> action, final String description, final Class<? extends Throwable>... expected) {
        final String output;
        try {
            output = action.get();
        } catch (final Throwable thrown) {
            if (Arrays.stream(expected).anyMatch(c -> c.isInstance(thrown))) {
                return; // hardening blocked at parse; acceptable outcome.
            }
            throw new AssertionError(blockedDescription(description) + " (got " + thrown.getClass().getName() + ")", thrown);
        }
        assertFalse(output.contains(LEAKED_MARKER),
                "Hardening did not block " + description + "; output contained marker '" + LEAKED_MARKER + "'.\nFull output:\n" + output);
    }

    /**
     * Skeleton for every strict {@code assert*DoesNotLeak} helper.
     *
     * <p>Runs the action, lets any thrown exception fail the assertion, and asserts that the captured output omits {@link #LEAKED_MARKER}. Use this when the
     * hardening contract guarantees "parses successfully without resolving the external resource"; use {@link #assertNoLeakOrThrows} when the contract is
     * "either blocks at parse or completes without leaked content".</p>
     *
     * @param action      The parse to execute, returning the captured output text checked for {@link #LEAKED_MARKER}.
     * @param description short label naming the JAXP surface under test.
     */
    private static void assertNoLeakStrict(final ThrowingSupplier<String> action, final String description) {
        final String output = assertDoesNotThrow(action, "Hardened " + description + " parse must not throw");
        assertFalse(output.contains(LEAKED_MARKER),
                "Hardening did not block " + description + "; output contained marker '" + LEAKED_MARKER + "'.\nFull output:\n" + output);
    }

    /**
     * Asserts the supplied parsing action throws an exception of the {@code expected} type.
     *
     * @param action      The parse to execute.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception type the hardening layer is expected to surface.
     */
    static void assertParseFails(final Executable action, final String description, final Class<? extends Throwable> expected) {
        assertThrows(expected, action, blockedDescription(description));
    }

    /**
     * Asserts the supplied parsing action throws an exception that matches one of the {@code expected} types.
     *
     * @param action      The parse to execute.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception types any of which the hardening layer may surface.
     */
    @SafeVarargs
    static void assertParseFails(final Executable action, final String description, final Class<? extends Throwable>... expected) {
        final Throwable thrown = assertThrows(Exception.class, action, blockedDescription(description));
        if (Arrays.stream(expected).noneMatch(c -> c.isInstance(thrown))) {
            throw new AssertionError(blockedDescription(description) + " (got " + thrown.getClass().getName() + ")", thrown);
        }
    }

    /**
     * Asserts the supplied action does not throw.
     *
     * <p>Generic primitive underlying every {@code assert*Parses(...)} / {@code assert*Compiles(...)} / {@code assert*Transforms(...)} /
     * {@code assert*Validates(...)} helper (and their permissive {@code assertPermissive*} counterparts); exposed for tests that compose a non-standard
     * call.</p>
     *
     * @param action      The parse to execute.
     * @param description short label included in the failure message.
     */
    static void assertParseSucceeds(final Executable action, final String description) {
        assertDoesNotThrow(action, "Unconfigured factory should parse " + description + "; the wrapper or its external reference is broken.");
    }

    /**
     * Asserts a permissive DOM parse succeeds.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link DocumentBuilderFactory#newInstance()} with FSP off; positive control proving the payload is
     * well-formed.</p>
     */
    static void assertPermissiveDomParses(final String payload) {
        assertParseSucceeds(() -> {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            if (!IS_ANDROID) {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            }
            suppressException(() -> factory.setAttribute(JDK_ENTITY_EXPANSION_LIMIT, "0"));
            strictDocumentBuilder(factory).parse(inputSource(payload));
        }, "DOM");
    }

    /**
     * Asserts a permissive SAX parse succeeds.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link SAXParserFactory#newInstance()} with FSP off; positive control proving the payload is
     * well-formed.</p>
     */
    static void assertPermissiveSaxParses(final String payload) {
        assertParseSucceeds(() -> {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            if (!IS_ANDROID) {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            }
            final XMLReader reader = strictXMLReader(factory);
            suppressException(() -> reader.setProperty(JDK_ENTITY_EXPANSION_LIMIT, "0"));
            consumeXmlReader(reader, payload);
        }, "SAX");
    }

    /**
     * Asserts a permissive Schema compilation succeeds.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link SchemaFactory#newInstance(String)} with FSP off; positive control proving the wrapper is
     * well-formed.</p>
     */
    static void assertPermissiveSchemaCompiles(final Source xsd) {
        assertParseSucceeds(() -> {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            if (!IS_ANDROID) {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            }
            suppressException(() -> factory.setProperty(JDK_ENTITY_EXPANSION_LIMIT, "0"));
            strictSchema(factory, xsd);
        }, "Schema compile");
    }

    /**
     * Asserts a permissive StAX parse succeeds.
     *
     * <p>{@link XMLStreamReader} from {@link XMLInputFactory#newInstance()} with FSP off; positive control proving the payload is well-formed.</p>
     */
    static void assertPermissiveStaxParses(final String payload) {
        assertParseSucceeds(() -> {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            suppressException(() -> factory.setProperty(XMLConstants.FEATURE_SECURE_PROCESSING, false));
            // URL form of the JDK property; JDK 8's XMLSecurityManager.getIndex only matches this form, JDK 11+ accepts both.
            suppressException(() -> factory.setProperty(JDK_ENTITY_EXPANSION_LIMIT, "0"));
            consumeStreamReader(factory, payload);
        }, "StAX");
    }

    /**
     * Asserts a permissive Templates compilation succeeds.
     *
     * <p>{@link TransformerFactory#newTransformer(Source)} via {@link TransformerFactory#newInstance()} with FSP off; positive control proving the stylesheet
     * is well-formed.</p>
     */
    static void assertPermissiveTemplatesCompiles(final String xslt) {
        assertParseSucceeds(() -> {
            final TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            strictTemplates(factory, permissiveSaxSource(xslt));
        }, "Templates compile");
    }

    /**
     * Asserts a permissive identity Transformer succeeds.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} via {@link TransformerFactory#newInstance()} with FSP off; positive control proving
     * the payload is well-formed.</p>
     */
    static void assertPermissiveTransformerTransforms(final String payload) {
        assertParseSucceeds(() -> {
            final TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            strictTransformer(factory).transform(permissiveSaxSource(payload), new StreamResult(new StringWriter()));
        }, "Transformer");
    }

    /**
     * Asserts a permissive Validator validation succeeds.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link SchemaFactory#newInstance(String)} with FSP off;
     * positive control proving the instance is well-formed.</p>
     */
    static void assertPermissiveValidatorValidates(final String xml) {
        assertParseSucceeds(() -> {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            suppressException(() -> factory.setProperty(JDK_ENTITY_EXPANSION_LIMIT, "0"));
            strictValidator(strictSchema(factory, streamSource(BENIGN_SCHEMA))).validate(streamSource(xml));
        }, "Validator");
    }

    /**
     * Asserts a hardened SAX parse of the payload throws.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link XmlFactories#newSAXParserFactory()}; only a thrown exception passes.</p>
     */
    static void assertSaxBlocks(final String payload) {
        assertParseFails(() -> consumeXmlReader(strictXMLReader(XmlFactories.newSAXParserFactory()), payload), "SAX", SAXException.class);
    }

    /**
     * Asserts a hardened SAX parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link XmlFactories#newSAXParserFactory()}; use this when the hardening guarantee is "the parse
     * succeeds but never resolves the external resource", for example, when {@code XERCES_LOAD_EXTERNAL_DTD=false} silently skips the external subset.</p>
     */
    static void assertSaxDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> captureCharacters(strictXMLReader(XmlFactories.newSAXParserFactory()), payload), "SAX");
    }

    /**
     * Asserts a hardened SAX parse succeeds.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link XmlFactories#newSAXParserFactory()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertSaxParses(final String payload) {
        assertParseSucceeds(() -> consumeXmlReader(strictXMLReader(XmlFactories.newSAXParserFactory()), payload), "SAX");
    }

    /**
     * Asserts a hardened Schema compilation throws.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link XmlFactories#newSchemaFactory()}; only a thrown exception passes.</p>
     */
    static void assertSchemaBlocks(final Source xsd) {
        assertParseFails(() -> strictSchema(XmlFactories.newSchemaFactory(), xsd), "Schema compile", SAXException.class, SecurityException.class);
    }

    /**
     * Asserts a hardened Schema compilation succeeds.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link XmlFactories#newSchemaFactory()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertSchemaCompiles(final Source xsd) {
        assertParseSucceeds(() -> strictSchema(XmlFactories.newSchemaFactory(), xsd), "Schema compile");
    }

    /**
     * Asserts a hardened Schema compilation completes without throwing.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link XmlFactories#newSchemaFactory()}; use this when the hardening contract guarantees the compile
     * succeeds but never resolves the external resource (for example, {@code XERCES_LOAD_EXTERNAL_DTD=false} silently skipping the external subset, with the body's
     * undeclared entity reference dropped per XML 1.0 §4.1).</p>
     */
    static void assertSchemaDoesNotLeak(final Source xsd) {
        assertParseSucceeds(() -> strictSchema(XmlFactories.newSchemaFactory(), xsd), "Schema compile");
    }

    /**
     * Asserts a hardened StAX parse of the payload throws.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link XmlFactories#newXMLInputFactory()}; both flavors are exercised and either must
     * throw.</p>
     */
    static void assertStaxBlocks(final String payload) {
        assertParseFails(() -> consumeStreamReader(XmlFactories.newXMLInputFactory(), payload), "StAX stream", XMLStreamException.class);
        assertParseFails(() -> consumeEventReader(XmlFactories.newXMLInputFactory(), payload), "StAX event", XMLStreamException.class);
    }

    /**
     * Asserts a hardened StAX parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link XmlFactories#newXMLInputFactory()}; both flavors are exercised. Use this when the
     * hardening guarantee is "the parse succeeds but never resolves the external resource", for example, when the JDK's {@code ignore-external-dtd} property silently
     * skips the external subset.</p>
     */
    static void assertStaxDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> captureStaxStreamText(XmlFactories.newXMLInputFactory(), payload), "StAX stream");
        assertNoLeakStrict(() -> captureStaxEventText(XmlFactories.newXMLInputFactory(), payload), "StAX event");
    }

    /**
     * Asserts a hardened StAX parse succeeds.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link XmlFactories#newXMLInputFactory()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertStaxParses(final String payload) {
        assertParseSucceeds(() -> consumeStreamReader(XmlFactories.newXMLInputFactory(), payload), "StAX stream");
        assertParseSucceeds(() -> consumeEventReader(XmlFactories.newXMLInputFactory(), payload), "StAX event");
    }

    /**
     * Asserts a hardened Templates compile-and-transform throws.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link XmlFactories#newTransformerFactory()} followed by transform; either step throwing
     * passes.</p>
     */
    static void assertTemplatesBlocks(final Source xslt) {
        assertParseFails(() -> {
            final Templates templates = strictTemplates(XmlFactories.newTransformerFactory(), xslt);
            // Xalan returns `null` if the template fails
            if (templates == null) {
                throw new TransformerException("Transformer factory returned null");
            }
            strictTransformer(templates).transform(streamSource("<root/>"), new StreamResult(new StringWriter()));
        }, "Templates", TransformerException.class);
    }

    /**
     * Asserts a hardened Templates compile-and-transform succeeds.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link XmlFactories#newTransformerFactory()} followed by transform; positive control for
     * DOCTYPE-only payloads.</p>
     */
    static void assertTemplatesCompiles(final Source xslt) {
        assertParseSucceeds(() -> templatesCompileAndTransform(xslt), "Templates compile");
    }

    /**
     * Asserts a hardened Templates compile-and-transform completes without throwing and without leaked content.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link XmlFactories#newTransformerFactory()} followed by transform; use this when the hardening
     * contract guarantees the compile and transform succeed but never resolve the external resource.</p>
     */
    static void assertTemplatesDoesNotLeak(final Source xslt) {
        assertNoLeakStrict(() -> templatesCompileAndTransform(xslt), "Templates");
    }

    /**
     * Asserts a hardened identity Transformer of the payload throws.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} on the identity transformer from {@link XmlFactories#newTransformerFactory()}; only
     * a thrown exception passes.</p>
     */
    static void assertTransformerBlocks(final String payload) {
        assertParseFails(
                () -> strictTransformer(XmlFactories.newTransformerFactory()).transform(streamSource(payload), new StreamResult(new StringWriter())),
                "Transformer", TransformerException.class);
    }

    /**
     * Asserts a hardened identity Transformer completes without throwing and without leaked content.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} via {@link XmlFactories#newTransformerFactory()}; use this when the hardening
     * contract guarantees the transform succeeds but never resolves the external resource.</p>
     */
    static void assertTransformerDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> identityTransformAndCapture(payload), "Transformer");
    }

    /**
     * Asserts a hardened identity Transformer succeeds.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} on the identity transformer from {@link XmlFactories#newTransformerFactory()};
     * positive control for DOCTYPE-only payloads.</p>
     */
    static void assertTransformerTransforms(final String payload) {
        assertParseSucceeds(() -> identityTransformAndCapture(payload), "Transformer");
    }

    /**
     * Asserts a hardened Validator validation throws.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link XmlFactories#newSchemaFactory()}; only a thrown
     * exception passes (the schema is benign; the attack lives in the instance document).</p>
     */
    static void assertValidatorBlocks(final String xml) {
        assertParseFails(
                () -> strictValidator(strictSchema(XmlFactories.newSchemaFactory(), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator", SAXException.class, SecurityException.class);
    }

    /**
     * Asserts a hardened Validator validation completes without throwing.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link XmlFactories#newSchemaFactory()}; use this when the
     * hardening contract guarantees the validate succeeds but never resolves the external resource.</p>
     */
    static void assertValidatorDoesNotLeak(final String xml) {
        assertParseSucceeds(
                () -> strictValidator(strictSchema(XmlFactories.newSchemaFactory(), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator");
    }

    /**
     * Asserts a hardened Validator validation succeeds.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link XmlFactories#newSchemaFactory()}; positive control
     * for DOCTYPE-only payloads.</p>
     */
    static void assertValidatorValidates(final String xml) {
        assertParseSucceeds(
                () -> strictValidator(strictSchema(XmlFactories.newSchemaFactory(), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator");
    }

    /**
     * Asserts a hardened-in-place XMLReader parse of the payload throws.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader hardened via {@link XmlFactories#harden(XMLReader)}; only a thrown exception passes.</p>
     */
    static void assertXmlReaderBlocks(final String payload) {
        assertParseFails(() -> consumeXmlReader(rawHardenedReader(), payload), "XMLReader", SAXException.class);
    }

    /**
     * Asserts a hardened-in-place XMLReader parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader hardened via {@link XmlFactories#harden(XMLReader)}; use this when the hardening contract
     * guarantees the parse succeeds but never resolves the external resource.</p>
     */
    static void assertXmlReaderDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> captureCharacters(rawHardenedReader(), payload), "XMLReader");
    }

    /**
     * Asserts a hardened-in-place XMLReader parse succeeds.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader hardened via {@link XmlFactories#harden(XMLReader)}; positive control for DOCTYPE-only
     * payloads.</p>
     */
    static void assertXmlReaderParses(final String payload) {
        assertParseSucceeds(() -> consumeXmlReader(rawHardenedReader(), payload), "XMLReader");
    }

    /**
     * Runs the action and, if it throws, aborts (skips rather than fails) the calling test. Used to guard platform-optional configuration such as
     * {@code setXIncludeAware}, which the Android JAXP implementations do not support.
     */
    static void assumeDoesNotThrow(final Executable action) {
        try {
            action.execute();
        } catch (final Throwable t) {
            Assumptions.assumeTrue(false, "platform does not support this configuration: " + t);
        }
    }

    /**
     * Builds the failure message used by every {@code assert*Blocks(...)} helper.
     *
     * @param description short label naming the JAXP surface under test.
     * @return The assertion-failure message string.
     */
    private static String blockedDescription(final String description) {
        return "Hardening did not block " + description + "; parse completed successfully.";
    }

    /** Parses the payload through the supplied reader and returns the accumulated character data, used by the SAX-based {@code DoesNotLeak} helpers. */
    private static String captureCharacters(final XMLReader reader, final String payload) throws Exception {
        final StringBuilder text = new StringBuilder();
        reader.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                text.append(ch, start, length);
            }
        });
        strictXMLReader(reader).parse(inputSource(payload));
        return text.toString();
    }

    /** Parses the payload through a {@link XMLEventReader} and returns the accumulated character data, used by the StAX-based {@code DoesNotLeak} helper. */
    private static String captureStaxEventText(final XMLInputFactory factory, final String payload) throws Exception {
        final StringBuilder text = new StringBuilder();
        final XMLEventReader events = factory.createXMLEventReader(new StringReader(payload));
        try {
            while (events.hasNext()) {
                final XMLEvent event = events.nextEvent();
                if (event.isCharacters()) {
                    text.append(event.asCharacters().getData());
                }
            }
        } finally {
            events.close();
        }
        return text.toString();
    }

    /** Parses the payload through a {@link XMLStreamReader} and returns the accumulated character data, used by the StAX-based {@code DoesNotLeak} helper. */
    private static String captureStaxStreamText(final XMLInputFactory factory, final String payload) throws Exception {
        final StringBuilder text = new StringBuilder();
        final XMLStreamReader stream = factory.createXMLStreamReader(new StringReader(payload));
        try {
            while (stream.hasNext()) {
                final int event = stream.next();
                if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    text.append(stream.getText());
                }
            }
        } finally {
            stream.close();
        }
        return text.toString();
    }

    /** Drains every {@link XMLEventReader} event from the factory's reader for the payload. */
    private static void consumeEventReader(final XMLInputFactory factory, final String payload) throws Exception {
        final XMLEventReader events = factory.createXMLEventReader(new StringReader(payload));
        try {
            while (events.hasNext()) {
                events.nextEvent();
            }
        } finally {
            events.close();
        }
    }

    /** Drains every {@link XMLStreamReader} event from the factory's reader for the payload. */
    private static void consumeStreamReader(final XMLInputFactory factory, final String payload) throws Exception {
        final XMLStreamReader stream = factory.createXMLStreamReader(new StringReader(payload));
        try {
            while (stream.hasNext()) {
                stream.next();
            }
        } finally {
            stream.close();
        }
    }

    /** Parses the payload through the supplied reader, discarding events; used by the SAX-based {@code Parses} / {@code Blocks} helpers. */
    private static void consumeXmlReader(final XMLReader reader, final String payload) throws Exception {
        reader.setContentHandler(new DefaultHandler());
        strictXMLReader(reader).parse(inputSource(payload));
    }

    private static String domParseAndCaptureText(final String payload) throws Exception {
        final Document doc = strictDocumentBuilder(XmlFactories.newDocumentBuilderFactory()).parse(inputSource(payload));
        if (doc.getDocumentElement() == null) {
            return "";
        }
        // Harmony's DOM returns null from getTextContent() on an element whose only children are unresolved EntityReference nodes.
        final String text = doc.getDocumentElement().getTextContent();
        return text == null ? "" : text;
    }

    private static String identityTransformAndCapture(final String payload) throws TransformerException {
        final StringWriter sink = new StringWriter();
        strictTransformer(XmlFactories.newTransformerFactory()).transform(streamSource(payload), new StreamResult(sink));
        return sink.toString();
    }

    /** Builds an {@link InputSource} backed by a {@link StringReader} over the payload. */
    static InputSource inputSource(final String xml) {
        return new InputSource(new StringReader(xml));
    }

    /**
     * Builds a {@link SAXSource} wrapping the payload, without an explicit parser; used by the unconfigured-side TrAX controls.
     */
    private static SAXSource permissiveSaxSource(final String xml) {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = strictXMLReader(factory);
        suppressException(() -> reader.setProperty(JDK_ENTITY_EXPANSION_LIMIT, "0"));
        // On Android the reader is Expat, which accepts namespace-prefixes at setFeature time but fails mid-parse; a permissive TrAX identity transform probes
        // that feature, so wrap it to reject the feature eagerly (matching the production HardeningExpatXMLReader) while keeping the control permissive (no floor).
        return new SAXSource(IS_ANDROID ? new PermissiveExpatReader(reader) : reader, new InputSource(new StringReader(xml)));
    }

    /**
     * Test-only permissive counterpart of {@code SAXParserHardener.HardeningExpatXMLReader}: a pass-through Expat wrapper that rejects the
     * {@code namespace-prefixes} feature eagerly (so a probing TrAX identity transformer falls back instead of failing the whole parse) but installs no deny-all
     * resolver floor, so the unconfigured/positive controls stay permissive.
     */
    private static final class PermissiveExpatReader extends XMLFilterImpl {

        private static final String NAMESPACE_PREFIXES_FEATURE = "http://xml.org/sax/features/namespace-prefixes";

        PermissiveExpatReader(final XMLReader parent) {
            super(parent);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (value && NAMESPACE_PREFIXES_FEATURE.equals(name)) {
                throw new SAXNotSupportedException("ExpatReader does not support enabling the '" + NAMESPACE_PREFIXES_FEATURE + "' feature");
            }
            super.setFeature(name, value);
        }
    }

    private static boolean probeAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean probeDomResolvesInternalEntities() {
        try {
            final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputSource(DOM_INTERNAL_ENTITY_PROBE));
            final Element root = doc.getDocumentElement();
            return root != null && "bar".equals(root.getTextContent());
        } catch (final Exception e) {
            return false;
        }
    }

    /** Builds a raw {@link XMLReader} from a deliberately permissive {@link SAXParserFactory} and hardens it via {@link XmlFactories#harden(XMLReader)}. */
    private static XMLReader rawHardenedReader() throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        if (!IS_ANDROID) {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        }
        return XmlFactories.harden(factory.newSAXParser().getXMLReader());
    }

    /** Opens the named test resource as a {@link StreamSource} preserving its system id, so relative includes/imports/redefines resolve normally. */
    static StreamSource resourceSource(final String name) {
        final URL url = resourceUrl(name);
        try {
            return new StreamSource(url.openStream(), url.toString());
        } catch (final IOException e) {
            throw new AssertionError("Failed to open " + name, e);
        }
    }

    /** Resolves a fixture under {@code src/test/resources/leaked/} to a {@link URL}, failing the test if the resource is missing. */
    static URL resourceUrl(final String name) {
        final URL url = AttackTestSupport.class.getResource("/leaked/" + name);
        assertNotNull(url, "test resource not found: " + name);
        return url;
    }

    /** Builds a {@link StreamSource} backed by a {@link StringReader} over the payload. */
    static StreamSource streamSource(final String xml) {
        final StreamSource streamSource = new StreamSource(new StringReader(xml));
        streamSource.setSystemId("test:fixture");
        return streamSource;
    }

    /**
     * Builds a {@link DocumentBuilder} from {@code factory} with {@link #STRICT_REPORTER} installed as its error handler.
     */
    static DocumentBuilder strictDocumentBuilder(final DocumentBuilderFactory factory) throws ParserConfigurationException {
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(STRICT_REPORTER);
        return builder;
    }

    /**
     * Compiles {@code xsds} into a {@link Schema} using {@code factory}, with {@link #STRICT_REPORTER} installed on the factory before compile.
     */
    private static Schema strictSchema(final SchemaFactory factory, final Source... xsds) throws SAXException {
        factory.setErrorHandler(STRICT_REPORTER);
        return factory.newSchema(xsds);
    }

    /**
     * Compiles {@code xslt} into a {@link Templates} using {@code factory}, with {@link #STRICT_REPORTER} installed on the factory before compile.
     */
    private static Templates strictTemplates(final TransformerFactory factory, final Source xslt) throws TransformerConfigurationException {
        factory.setErrorListener(STRICT_REPORTER);
        return factory.newTemplates(xslt);
    }

    /**
     * Builds an identity {@link Transformer} from {@code factory} with {@link #STRICT_REPORTER} installed on both the factory and the resulting transformer.
     */
    private static Transformer strictTransformer(final TransformerFactory factory) throws TransformerConfigurationException {
        factory.setErrorListener(STRICT_REPORTER);
        final Transformer transformer = factory.newTransformer();
        transformer.setErrorListener(STRICT_REPORTER);
        return transformer;
    }

    /**
     * Builds a {@link Transformer} from {@code templates} with {@link #STRICT_REPORTER} installed as its error listener.
     */
    private static Transformer strictTransformer(final Templates templates) throws TransformerConfigurationException {
        final Transformer transformer = templates.newTransformer();
        transformer.setErrorListener(STRICT_REPORTER);
        return transformer;
    }

    /**
     * Builds a {@link Validator} from {@code schema} with {@link #STRICT_REPORTER} installed as its error handler.
     */
    private static Validator strictValidator(final Schema schema) {
        final Validator validator = schema.newValidator();
        validator.setErrorHandler(STRICT_REPORTER);
        return validator;
    }

    /**
     * Builds an {@link XMLReader} from {@code factory} with {@link #STRICT_REPORTER} installed as its error handler.
     */
    private static XMLReader strictXMLReader(final SAXParserFactory factory) {
        try {
            return strictXMLReader(factory.newSAXParser().getXMLReader());
        } catch (ParserConfigurationException | SAXException e) {
            throw new AssertionError("Failed to create permissive XMLReader", e);
        }
    }

    /**
     * Installs {@link #STRICT_REPORTER} as the error handler on {@code reader} and returns it; for raw-reader paths.
     */
    static XMLReader strictXMLReader(final XMLReader reader) {
        reader.setErrorHandler(STRICT_REPORTER);
        return reader;
    }

    /** Runs the action and silently swallows any thrown exception; used to apply best-effort permissive-side flags that may not be supported. */
    private static void suppressException(final Executable action) {
        try {
            action.execute();
        } catch (final Throwable e) {
            // Ignore
        }
    }

    private static String templatesCompileAndTransform(final Source xslt) throws TransformerException {
        final StringWriter sink = new StringWriter();
        final Templates templates = strictTemplates(XmlFactories.newTransformerFactory(), xslt);
        // Xalan returns `null` if the template fails
        if (templates != null) {
            strictTransformer(templates).transform(streamSource("<root/>"), new StreamResult(sink));
        }
        return sink.toString();
    }

    /** XML body wrapping the supplied text in a benign {@code <root>/<child>} element pair, validated by {@link #BENIGN_SCHEMA}. */
    static String xmlBody(final String text) {
        return "<root><child>" + text + "</child></root>";
    }

    /** XSD body embedding the supplied text in an annotation, used as the carrier for DOCTYPE-based attacks against schema compilation. */
    static String xsdBody(final String text) {
        return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                + "  <xs:annotation><xs:documentation>" + text + "</xs:documentation></xs:annotation>\n"
                + "  <xs:element name=\"root\" type=\"xs:string\"/>\n"
                + "</xs:schema>";
    }

    /** XSLT body embedding the supplied text inside a single {@code xsl:template match="/"}, used as the carrier for DOCTYPE-based attacks against TrAX. */
    static String xsltBody(final String text) {
        return "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\">" + text + "</xsl:template>\n"
                + "</xsl:stylesheet>";
    }

    private AttackTestSupport() {
    }

}
