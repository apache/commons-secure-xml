/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz
 * SPDX-License-Identifier: Apache-2.0
 */
package org.apache.commons.xml.secure;

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
 * <p>The secure-side helpers come in three flavors, distinguished by their suffix:</p>
 *
 * <ul>
 *   <li>{@code assert*Blocks(...)} runs the payload through a secure factory from {@link org.apache.commons.xml.secure} and asserts the parse throws. Used when the secure
 *       layer is expected to reject the attack outright.</li>
 *   <li>{@code assert*DoesNotLeak(...)} runs the payload through a secure factory and asserts the parse completes without throwing and without producing the
 *       {@link #LEAKED_MARKER} string. Used when the secure contract guarantees the parse succeeds but never resolves the external resource (for example,
 *       the ignore-all resolver floor resolving the external subset to empty content).</li>
 *   <li>{@code assert*BlocksOrDoesNotLeak(...)} accepts either of the previous two outcomes. Used where the same secure contract surfaces differently across
 *       providers (for example, an entity declared in the emptied external subset is a fatal error on one implementation and a silently skipped reference on
 *       another).</li>
 * </ul>
 *
 * <p>DOM tests that depend on user-defined entity machinery should gate themselves with {@link org.junit.jupiter.api.Assumptions#assumeTrue} on
 * {@link #DOM_RESOLVES_INTERNAL_ENTITIES} so they skip on platforms (such as Android with KXmlParser) whose DOM parser does not surface the entity events that
 * the strict {@link #assertDomBlocks} assertion expects.</p>
 *
 * <p>The permissive-side positive controls mirror the secure-side verbs with an {@code assertPermissive*} prefix: {@code assertPermissive*Parses} for direct
 * parsing, {@code assertPermissive*Compiles} for {@link SchemaFactory} / {@link TransformerFactory} compilation, {@code assertPermissiveTransformerTransforms}
 * for {@code Transformer.transform}, {@code assertPermissiveValidatorValidates} for {@code Validator.validate}. Both sides perform the same operation; the
 * prefix marks which factory secure level the assertion is set against.</p>
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
     * Test-only permissive counterpart of {@code SecureSAXParserFactory.SecureExpatXMLReader}: a pass-through Expat wrapper that rejects the
     * {@code namespace-prefixes} feature eagerly (so a probing TrAX identity transformer falls back instead of failing the whole parse) but installs no ignore-all
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

    /**
     * Strict reporter installed on every secure factory, parser, validator and transformer in the helpers below.
     * <p>
     * The secure layer signals every blocked external fetch and every SAX-fatal it could not silently skip via the standard JAXP error channels:
     * {@link ErrorListener#error(TransformerException)} / {@link ErrorListener#fatalError(TransformerException) fatalError} on the TrAX side and
     * {@link ErrorHandler#error(SAXParseException) error} / {@link ErrorHandler#fatalError(SAXParseException) fatalError} on the SAX side. Both Apache Xalan's
     * {@code DefaultErrorHandler(false)} and Saxon's {@code StandardErrorListener} are pathologically lenient defaults that swallow these events; SAX's
     * {@link DefaultHandler} treats {@code error} as a no-op. The test fixture replaces those defaults with a strict reporter that re-throws on every reported
     * error or fatalError so the helpers can observe the block via the same mechanism the specification uses to surface it. Warnings stay silent: they are not
     * security signals.
     * </p>
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
    /** {@code true} when the platform's default DOM factory (and its builders) support parser-attached schemas; Android inherits the throwing JAXP base methods. */
    static final boolean DOM_SUPPORTS_SCHEMA = supportsConfiguration(() -> DocumentBuilderFactory.newInstance().setSchema(null));
    /** {@code true} when the platform's default DOM factory accepts {@link XMLConstants#FEATURE_SECURE_PROCESSING}; Android's factory rejects it. */
    static final boolean DOM_SUPPORTS_SECURE_PROCESSING =
            supportsConfiguration(() -> DocumentBuilderFactory.newInstance().setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true));
    /** {@code true} when the platform's default DOM factory (and its builders) support the XInclude switches; Android inherits the throwing JAXP base methods. */
    static final boolean DOM_SUPPORTS_XINCLUDE = supportsConfiguration(() -> DocumentBuilderFactory.newInstance().setXIncludeAware(false));
    /** {@code true} when running on Android (Dalvik / ART), {@code false} on any standard JVM. Probed once via {@code Class.forName} on {@code android.os.Build}. */
    static final boolean IS_ANDROID = probeAndroid();
    /**
     * URL form of the three JDK entity limits, every one of which a Billion Laughs payload could trip.
     */
    private static final String[] JDK_ENTITY_LIMITS = {
            "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
            "http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit",
            "http://www.oracle.com/xml/jaxp/properties/entityReplacementLimit"};
    /**
     * Text planted in every fixture under {@code src/test/resources/leaked/}.
     *
     * <p>Tests that capture a parser's output assert this string is absent: it can only appear if the secure parser fetched the external resource, so its
     * presence is the leak signal.</p>
     */
    static final String LEAKED_MARKER = "All your base are belong to us";
    /** {@code true} when the platform's default SAX parser supports {@code reset()}; Android inherits the throwing JAXP base method. */
    static final boolean SAX_SUPPORTS_RESET = supportsConfiguration(() -> SAXParserFactory.newInstance().newSAXParser().reset());
    /** {@code true} when the platform's default SAX factory (and its parsers) support parser-attached schemas; Android inherits the throwing JAXP base methods. */
    static final boolean SAX_SUPPORTS_SCHEMA = supportsConfiguration(() -> SAXParserFactory.newInstance().setSchema(null));
    /** {@code true} when the platform's default SAX factory accepts {@link XMLConstants#FEATURE_SECURE_PROCESSING}; Android's Expat rejects it. */
    static final boolean SAX_SUPPORTS_SECURE_PROCESSING =
            supportsConfiguration(() -> SAXParserFactory.newInstance().setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true));
    /** {@code true} when the platform's default SAX factory (and its parsers) support the XInclude switches; Android inherits the throwing JAXP base methods. */
    static final boolean SAX_SUPPORTS_XINCLUDE = supportsConfiguration(() -> SAXParserFactory.newInstance().setXIncludeAware(false));
    static final StrictReporter STRICT_REPORTER = new StrictReporter();
    /**
     * Woodstox's entity-count limit property; it ignores the JDK properties above and enforces its own default of {@code 100000}.
     */
    private static final String WSTX_MAX_ENTITY_COUNT = "com.ctc.wstx.maxEntityCount";

    /**
     * Asserts a secure DOM parse of the payload throws.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link SecureDocumentBuilderFactory#newInstance()}; only a thrown exception passes.</p>
     */
    static void assertDomBlocks(final String payload) {
        assertParseFails(() -> strictDocumentBuilder(SecureDocumentBuilderFactory.newInstance()).parse(inputSource(payload)), "DOM", SAXException.class);
    }

    /**
     * Asserts a secure DOM parse either blocks at parse or completes without leaked content.
     *
     * <p>Used for an external-resource payload whose outcome differs across implementations: one that resolves the reference to empty (the ignore-all floor) does
     * not leak, while one that rejects the unresolvable systemId throws instead. Both are acceptable.</p>
     */
    static void assertDomBlocksOrDoesNotLeak(final String payload) {
        assertNoLeakOrThrows(() -> domParseAndCaptureText(SecureDocumentBuilderFactory.newInstance(), payload), "DOM", SAXException.class);
    }

    /**
     * Asserts a secure DOM parse completes without throwing and without leaked content.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link SecureDocumentBuilderFactory#newInstance()}; use this when the secure guarantee is "the parse
     * succeeds but never resolves the external resource", for example, when the ignore-all resolver floor resolves the external subset to empty content.</p>
     */
    static void assertDomDoesNotLeak(final String payload) {
        assertDomDoesNotLeak(SecureDocumentBuilderFactory.newInstance(), payload);
    }

    /**
     * Same contract as {@link #assertDomDoesNotLeak(String)}, on a caller-configured secure factory.
     */
    static void assertDomDoesNotLeak(final DocumentBuilderFactory factory, final String payload) {
        assertNoLeakStrict(() -> domParseAndCaptureText(factory, payload), "DOM");
    }

    /**
     * Asserts a secure DOM parse succeeds.
     *
     * <p>{@link DocumentBuilder#parse(InputSource)} via {@link SecureDocumentBuilderFactory#newInstance()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertDomParses(final String payload) {
        assertParseSucceeds(() -> strictDocumentBuilder(SecureDocumentBuilderFactory.newInstance()).parse(inputSource(payload)), "DOM");
    }

    /**
     * Skeleton for every {@code assert*BlocksOrDoesNotLeak} helper.
     *
     * <p>Treats a thrown exception of one of the {@code expected} types as "hardening blocked at parse" (acceptable); otherwise asserts the captured output
     * omits {@link #LEAKED_MARKER}. A throw whose type does not match {@code expected} fails the test, so unrelated failures (for example, a {@link SecureException}
     * because no recipe matched the JAXP implementation) cannot be silently accepted as a clean block.</p>
     *
     * @param action      The parse to execute, returning the captured output text checked for {@link #LEAKED_MARKER}.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception types any of which the secure layer may surface as a clean rejection.
     */
    @SafeVarargs
    private static void assertNoLeakOrThrows(final ThrowingSupplier<String> action, final String description, final Class<? extends Throwable>... expected) {
        final String output;
        try {
            output = action.get();
        } catch (final Throwable thrown) {
            if (Arrays.stream(expected).anyMatch(c -> c.isInstance(thrown))) {
                return; // secure blocked at parse; acceptable outcome.
            }
            throw new AssertionError(blockedDescription(description) + " (got " + thrown.getClass().getName() + ")", thrown);
        }
        assertFalse(output.contains(LEAKED_MARKER),
                "Securing did not block " + description + "; output contained marker '" + LEAKED_MARKER + "'.\nFull output:\n" + output);
    }

    /**
     * Skeleton for every strict {@code assert*DoesNotLeak} helper.
     *
     * <p>Runs the action, lets any thrown exception fail the assertion, and asserts that the captured output omits {@link #LEAKED_MARKER}. Use this when the
     * secure contract guarantees "parses successfully without resolving the external resource"; use {@link #assertNoLeakOrThrows} when the contract is
     * "either blocks at parse or completes without leaked content".</p>
     *
     * @param action      The parse to execute, returning the captured output text checked for {@link #LEAKED_MARKER}.
     * @param description short label naming the JAXP surface under test.
     */
    private static void assertNoLeakStrict(final ThrowingSupplier<String> action, final String description) {
        final String output = assertDoesNotThrow(action, "Secured " + description + " parse must not throw");
        assertFalse(output.contains(LEAKED_MARKER),
                "Securing did not block " + description + "; output contained marker '" + LEAKED_MARKER + "'.\nFull output:\n" + output);
    }

    /**
     * Asserts the supplied parsing action throws an exception of the {@code expected} type.
     *
     * @param action      The parse to execute.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception type the secure layer is expected to surface.
     */
    static void assertParseFails(final Executable action, final String description, final Class<? extends Throwable> expected) {
        assertThrows(expected, action, blockedDescription(description));
    }

    /**
     * Asserts the supplied parsing action throws an exception that matches one of the {@code expected} types.
     *
     * @param action      The parse to execute.
     * @param description short label naming the JAXP surface under test.
     * @param expected    The exception types any of which the secure layer may surface.
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
            liftEntityLimits(factory);
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
            liftEntityLimits(reader);
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
            liftEntityLimits(factory);
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
            liftEntityLimits(factory);
            consumeStreamReader(factory, payload);
        }, "StAX");
    }

    /**
     * Asserts a permissive Templates compilation succeeds.
     *
     * <p>{@link TransformerFactory#newTransformer(Source)} via {@link TransformerFactory#newInstance()} with FSP off; positive control proving the stylesheet
     * is well-formed.</p>
     *
     * <p>The control instantiates a {@link Transformer} rather than stopping at {@link TransformerFactory#newTemplates(Source)}, because a failed compile does
     * not necessarily throw: Apache Xalan returns {@code null}, and XSLTC swallows the error and returns a {@code Templates} carrying no translet. Only building
     * the transformer surfaces either, so the control cannot pass on a stylesheet that never compiled.</p>
     */
    static void assertPermissiveTemplatesCompiles(final String xslt) {
        assertParseSucceeds(() -> {
            final TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            final Templates templates = strictTemplates(factory, permissiveSaxSource(xslt));
            if (templates == null) {
                throw new TransformerConfigurationException("Transformer factory returned null");
            }
            strictTransformer(templates);
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
            liftEntityLimits(factory);
            strictValidator(strictSchema(factory, streamSource(BENIGN_SCHEMA))).validate(streamSource(xml));
        }, "Validator");
    }

    /**
     * Asserts a secure SAX parse of the payload throws.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link SecureSAXParserFactory#newInstance()}; only a thrown exception passes.</p>
     */
    static void assertSaxBlocks(final String payload) {
        assertParseFails(() -> consumeXmlReader(strictXMLReader(SecureSAXParserFactory.newInstance()), payload), "SAX", SAXException.class);
    }

    /**
     * Asserts a secure SAX parse either blocks at parse or completes without leaked content. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertSaxBlocksOrDoesNotLeak(final String payload) {
        assertNoLeakOrThrows(() -> captureCharacters(strictXMLReader(SecureSAXParserFactory.newInstance()), payload), "SAX", SAXException.class);
    }

    /**
     * Asserts a secure SAX parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link SecureSAXParserFactory#newInstance()}; use this when the secure guarantee is "the parse
     * succeeds but never resolves the external resource", for example, when the ignore-all resolver floor resolves the external subset to empty content.</p>
     */
    static void assertSaxDoesNotLeak(final String payload) {
        assertSaxDoesNotLeak(strictXMLReader(SecureSAXParserFactory.newInstance()), payload);
    }

    /**
     * Same contract as {@link #assertSaxDoesNotLeak(String)}, on a caller-configured secure reader.
     */
    static void assertSaxDoesNotLeak(final XMLReader reader, final String payload) {
        assertNoLeakStrict(() -> captureCharacters(reader, payload), "SAX");
    }

    /**
     * Asserts a secure SAX parse succeeds.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a parser from {@link SecureSAXParserFactory#newInstance()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertSaxParses(final String payload) {
        assertParseSucceeds(() -> consumeXmlReader(strictXMLReader(SecureSAXParserFactory.newInstance()), payload), "SAX");
    }

    /**
     * Asserts a secure Schema compilation throws.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link SecureSchemaFactory#newInstance(String)}; only a thrown exception passes.</p>
     */
    static void assertSchemaBlocks(final Source xsd) {
        assertSchemaBlocks(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), xsd);
    }

    /**
     * Same contract as {@link #assertSchemaBlocks(Source)}, on a caller-configured secure factory.
     */
    static void assertSchemaBlocks(final SchemaFactory factory, final Source xsd) {
        assertParseFails(() -> strictSchema(factory, xsd), "Schema compile", SAXException.class, SecurityException.class);
    }

    /**
     * Asserts a secure Schema compile either blocks or completes: an unresolved import resolves to an empty schema (which may itself fail to compile) or is
     * rejected outright. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertSchemaBlocksOrDoesNotLeak(final Source xsd) {
        assertNoLeakOrThrows(() -> {
            strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), xsd);
            return "";
        }, "Schema compile", SAXException.class, SecurityException.class);
    }

    /**
     * Asserts a secure Schema compilation succeeds.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link SecureSchemaFactory#newInstance(String)}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertSchemaCompiles(final Source xsd) {
        assertParseSucceeds(() -> strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), xsd), "Schema compile");
    }

    /**
     * Asserts a secure Schema compilation completes without throwing.
     *
     * <p>{@link SchemaFactory#newSchema(Source)} via {@link SecureSchemaFactory#newInstance(String)}; use this when the secure contract guarantees the compile
     * succeeds but never resolves the external resource (for example, {@code XERCES_LOAD_EXTERNAL_DTD=false} silently skipping the external subset, with the body's
     * undeclared entity reference dropped per XML 1.0 §4.1).</p>
     */
    static void assertSchemaDoesNotLeak(final Source xsd) {
        assertSchemaDoesNotLeak(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), xsd);
    }

    /**
     * Same contract as {@link #assertSchemaDoesNotLeak(Source)}, on a caller-configured secure factory.
     */
    static void assertSchemaDoesNotLeak(final SchemaFactory factory, final Source xsd) {
        assertParseSucceeds(() -> strictSchema(factory, xsd), "Schema compile");
    }

    /**
     * Asserts a secure StAX parse of the payload throws.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link SecureXMLInputFactory#newInstance()}; both flavors are exercised and either must
     * throw.</p>
     */
    static void assertStaxBlocks(final String payload) {
        assertParseFails(() -> consumeStreamReader(SecureXMLInputFactory.newInstance(), payload), "StAX stream", XMLStreamException.class);
        assertParseFails(() -> consumeEventReader(SecureXMLInputFactory.newInstance(), payload), "StAX event", XMLStreamException.class);
    }

    /**
     * Asserts a secure StAX parse (stream and event) either blocks at parse or completes without leaked content. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertStaxBlocksOrDoesNotLeak(final String payload) {
        assertNoLeakOrThrows(() -> captureStaxStreamText(SecureXMLInputFactory.newInstance(), payload), "StAX stream", XMLStreamException.class);
        assertNoLeakOrThrows(() -> captureStaxEventText(SecureXMLInputFactory.newInstance(), payload), "StAX event", XMLStreamException.class);
    }

    /**
     * Asserts a secure StAX parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link SecureXMLInputFactory#newInstance()}; both flavors are exercised. Use this when the
     * secure guarantee is "the parse succeeds but never resolves the external resource", for example, when the JDK's {@code ignore-external-dtd} property silently
     * skips the external subset.</p>
     */
    static void assertStaxDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> captureStaxStreamText(SecureXMLInputFactory.newInstance(), payload), "StAX stream");
        assertNoLeakStrict(() -> captureStaxEventText(SecureXMLInputFactory.newInstance(), payload), "StAX event");
    }

    /**
     * Asserts a secure StAX parse succeeds.
     *
     * <p>{@link XMLStreamReader} and {@link XMLEventReader} from {@link SecureXMLInputFactory#newInstance()}; positive control for DOCTYPE-only payloads.</p>
     */
    static void assertStaxParses(final String payload) {
        assertParseSucceeds(() -> consumeStreamReader(SecureXMLInputFactory.newInstance(), payload), "StAX stream");
        assertParseSucceeds(() -> consumeEventReader(SecureXMLInputFactory.newInstance(), payload), "StAX event");
    }

    /**
     * Asserts a secure Templates compile-and-transform throws.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link SecureTransformerFactory#newInstance()} followed by transform; either step throwing
     * passes.</p>
     */
    static void assertTemplatesBlocks(final Source xslt) {
        assertParseFails(() -> {
            final Templates templates = strictTemplates(SecureTransformerFactory.newInstance(), xslt);
            // Xalan returns `null` if the template fails
            if (templates == null) {
                throw new TransformerException("Transformer factory returned null");
            }
            strictTransformer(templates).transform(streamSource("<root/>"), new StreamResult(new StringWriter()));
        }, "Templates", TransformerException.class);
    }

    /**
     * Asserts a secure Templates compile-and-transform either blocks or completes without leaked content. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertTemplatesBlocksOrDoesNotLeak(final Source xslt) {
        assertNoLeakOrThrows(() -> templatesCompileAndTransform(SecureTransformerFactory.newInstance(), xslt), "Templates", TransformerException.class);
    }

    /**
     * Asserts a secure Templates compile-and-transform succeeds.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link SecureTransformerFactory#newInstance()} followed by transform; positive control for
     * DOCTYPE-only payloads.</p>
     */
    static void assertTemplatesCompiles(final Source xslt) {
        assertParseSucceeds(() -> templatesCompileAndTransform(SecureTransformerFactory.newInstance(), xslt), "Templates compile");
    }

    /**
     * Asserts a secure Templates compile-and-transform completes without throwing and without leaked content.
     *
     * <p>{@link TransformerFactory#newTemplates(Source)} via {@link SecureTransformerFactory#newInstance()} followed by transform; use this when the secure
     * contract guarantees the compile and transform succeed but never resolve the external resource.</p>
     */
    static void assertTemplatesDoesNotLeak(final Source xslt) {
        assertTemplatesDoesNotLeak(SecureTransformerFactory.newInstance(), xslt);
    }

    /**
     * Same contract as {@link #assertTemplatesDoesNotLeak(Source)}, on a caller-configured secure factory.
     */
    static void assertTemplatesDoesNotLeak(final TransformerFactory factory, final Source xslt) {
        assertNoLeakStrict(() -> templatesCompileAndTransform(factory, xslt), "Templates");
    }

    /**
     * Asserts a secure identity Transformer of the payload throws.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} on the identity transformer from {@link SecureTransformerFactory#newInstance()}; only
     * a thrown exception passes.</p>
     */
    static void assertTransformerBlocks(final String payload) {
        assertParseFails(
                () -> strictTransformer(SecureTransformerFactory.newInstance()).transform(streamSource(payload), new StreamResult(new StringWriter())),
                "Transformer", TransformerException.class);
    }

    /**
     * Asserts a secure identity Transformer either blocks or completes without leaked content. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertTransformerBlocksOrDoesNotLeak(final String payload) {
        assertNoLeakOrThrows(() -> identityTransformAndCapture(SecureTransformerFactory.newInstance(), payload), "Transformer", TransformerException.class);
    }

    /**
     * Asserts a secure identity Transformer completes without throwing and without leaked content.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} via {@link SecureTransformerFactory#newInstance()}; use this when the secure
     * contract guarantees the transform succeeds but never resolves the external resource.</p>
     */
    static void assertTransformerDoesNotLeak(final String payload) {
        assertTransformerDoesNotLeak(SecureTransformerFactory.newInstance(), payload);
    }

    /**
     * Same contract as {@link #assertTransformerDoesNotLeak(String)}, on a caller-configured secure factory.
     */
    static void assertTransformerDoesNotLeak(final TransformerFactory factory, final String payload) {
        assertNoLeakStrict(() -> identityTransformAndCapture(factory, payload), "Transformer");
    }

    /**
     * Asserts a secure identity Transformer succeeds.
     *
     * <p>{@link Transformer#transform(Source, javax.xml.transform.Result)} on the identity transformer from {@link SecureTransformerFactory#newInstance()};
     * positive control for DOCTYPE-only payloads.</p>
     */
    static void assertTransformerTransforms(final String payload) {
        assertParseSucceeds(() -> identityTransformAndCapture(SecureTransformerFactory.newInstance(), payload), "Transformer");
    }

    /**
     * Asserts a secure Validator validation throws.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link SecureSchemaFactory#newInstance(String)}; only a thrown
     * exception passes (the schema is benign; the attack lives in the instance document).</p>
     */
    static void assertValidatorBlocks(final String xml) {
        assertParseFails(
                () -> strictValidator(strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator", SAXException.class, SecurityException.class);
    }

    /**
     * Asserts a secure Validator either blocks or completes without leaked content. The instance document's unresolvable external entity is either dropped
     * (no leak) or rejected; a rejection surfaces as a SAX/security error or, where the parser attempts the unresolvable systemId directly, an
     * {@link IOException}. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertValidatorBlocksOrDoesNotLeak(final String xml) {
        assertNoLeakOrThrows(() -> {
            strictValidator(strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml));
            return "";
        }, "Validator", SAXException.class, SecurityException.class, IOException.class);
    }

    /**
     * Asserts a secure Validator validation completes without throwing.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link SecureSchemaFactory#newInstance(String)}; use this when the
     * secure contract guarantees the validate succeeds but never resolves the external resource.</p>
     */
    static void assertValidatorDoesNotLeak(final String xml) {
        assertParseSucceeds(
                () -> strictValidator(strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator");
    }

    /**
     * Asserts a secure Validator validation succeeds.
     *
     * <p>{@link Validator#validate(Source)} on a validator from {@link #BENIGN_SCHEMA} compiled via {@link SecureSchemaFactory#newInstance(String)}; positive control
     * for DOCTYPE-only payloads.</p>
     */
    static void assertValidatorValidates(final String xml) {
        assertParseSucceeds(
                () -> strictValidator(strictSchema(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI), streamSource(BENIGN_SCHEMA))).validate(streamSource(xml)),
                "Validator");
    }

    /**
     * Asserts a secure-in-place XMLReader parse of the payload throws.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader secure via {@link SecureSAXParserFactory#secure(XMLReader)}; only a thrown exception passes.</p>
     */
    static void assertXmlReaderBlocks(final String payload) {
        assertParseFails(() -> consumeXmlReader(rawSecureXMLReader(), payload), "XMLReader", SAXException.class);
    }

    /**
     * Asserts a secure-in-place XMLReader parse either blocks at parse or completes without leaked content. See {@link #assertDomBlocksOrDoesNotLeak(String)}.
     */
    static void assertXmlReaderBlocksOrDoesNotLeak(final String payload) {
        assertNoLeakOrThrows(() -> captureCharacters(rawSecureXMLReader(), payload), "XMLReader", SAXException.class);
    }

    /**
     * Asserts a secure-in-place XMLReader parse completes without throwing and without leaked content.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader secure via {@link SecureSAXParserFactory#secure(XMLReader)}; use this when the secure contract
     * guarantees the parse succeeds but never resolves the external resource.</p>
     */
    static void assertXmlReaderDoesNotLeak(final String payload) {
        assertNoLeakStrict(() -> captureCharacters(rawSecureXMLReader(), payload), "XMLReader");
    }

    /**
     * Asserts a secure-in-place XMLReader parse succeeds.
     *
     * <p>{@link XMLReader#parse(InputSource)} on a raw reader secure via {@link SecureSAXParserFactory#secure(XMLReader)}; positive control for DOCTYPE-only
     * payloads.</p>
     */
    static void assertXmlReaderParses(final String payload) {
        assertParseSucceeds(() -> consumeXmlReader(rawSecureXMLReader(), payload), "XMLReader");
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
        return "Securing did not block " + description + "; parse completed successfully.";
    }

    /** Parses the source through the supplied reader, with {@link #STRICT_REPORTER} installed, and returns the accumulated character data. */
    static String captureCharacters(final XMLReader reader, final InputSource source) throws Exception {
        final StringBuilder text = new StringBuilder();
        reader.setContentHandler(capturingHandler(text));
        strictXMLReader(reader).parse(source);
        return text.toString();
    }

    /** Parses the payload through the supplied reader and returns the accumulated character data, used by the SAX-based {@code DoesNotLeak} helpers. */
    static String captureCharacters(final XMLReader reader, final String payload) throws Exception {
        return captureCharacters(reader, inputSource(payload));
    }

    /** Parses the payload through a {@link XMLEventReader} and returns the accumulated character and CDATA data, used by the StAX-based {@code DoesNotLeak} helper. */
    static String captureStaxEventText(final XMLInputFactory factory, final String payload) throws Exception {
        final StringBuilder text = new StringBuilder();
        final XMLEventReader events = factory.createXMLEventReader(new StringReader(payload));
        try {
            while (events.hasNext()) {
                final XMLEvent event = events.nextEvent();
                if (event.isCharacters() || event.getEventType() == XMLStreamConstants.CDATA) {
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

    /** Content handler whose {@code characters} callback accumulates into {@code text}; for tests that install (or pass) the handler themselves. */
    static DefaultHandler capturingHandler(final StringBuilder text) {
        return new DefaultHandler() {
            @Override
            public void characters(final char[] ch, final int start, final int length) {
                text.append(ch, start, length);
            }
        };
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

    private static String domParseAndCaptureText(final DocumentBuilderFactory factory, final String payload) throws Exception {
        final Document doc = strictDocumentBuilder(factory).parse(inputSource(payload));
        if (doc.getDocumentElement() == null) {
            return "";
        }
        // Harmony's DOM returns null from getTextContent() on an element whose only children are unresolved EntityReference nodes.
        final String text = doc.getDocumentElement().getTextContent();
        return text == null ? "" : text;
    }

    private static String identityTransformAndCapture(final TransformerFactory factory, final String payload) throws TransformerException {
        final StringWriter sink = new StringWriter();
        strictTransformer(factory).transform(streamSource(payload), new StreamResult(sink));
        return sink.toString();
    }

    /** Builds an {@link InputSource} backed by a {@link StringReader} over the payload. */
    static InputSource inputSource(final String xml) {
        return new InputSource(new StringReader(xml));
    }

    /** Lifts every entity expansion limit on a {@link DocumentBuilderFactory}. */
    private static void liftEntityLimits(final DocumentBuilderFactory factory) {
        for (final String limit : JDK_ENTITY_LIMITS) {
            suppressException(() -> factory.setAttribute(limit, "0"));
        }
    }

    /** Lifts every entity expansion limit on a {@link SchemaFactory}. */
    private static void liftEntityLimits(final SchemaFactory factory) {
        for (final String limit : JDK_ENTITY_LIMITS) {
            suppressException(() -> factory.setProperty(limit, "0"));
        }
    }

    /** Lifts every entity expansion limit on a {@link XMLInputFactory}. */
    private static void liftEntityLimits(final XMLInputFactory factory) {
        for (final String limit : JDK_ENTITY_LIMITS) {
            suppressException(() -> factory.setProperty(limit, "0"));
        }
        suppressException(() -> factory.setProperty(WSTX_MAX_ENTITY_COUNT, Integer.MAX_VALUE));
    }

    /** Lifts every entity expansion limit on an {@link XMLReader}. */
    private static void liftEntityLimits(final XMLReader reader) {
        for (final String limit : JDK_ENTITY_LIMITS) {
            suppressException(() -> reader.setProperty(limit, "0"));
        }
    }

    /**
     * A permissive, namespace-aware {@link XMLReader} with every entity-expansion limit lifted, for the unconfigured-side controls.
     *
     * <p>On Android the reader is Expat, which accepts {@code namespace-prefixes} at {@code setFeature} time but fails mid-parse; a probing TrAX path (an
     * identity transform, or Xalan's {@code TrAXFilter} self-provisioning) enables that feature, so wrap it to reject the feature eagerly (matching the
     * production {@code SecureExpatXMLReader}) while keeping the control permissive (no floor).</p>
     *
     * @return A permissive reader, wrapped on Android to reject {@code namespace-prefixes} eagerly.
     */
    static XMLReader permissiveReader() {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = strictXMLReader(factory);
        liftEntityLimits(reader);
        return IS_ANDROID ? new PermissiveExpatReader(reader) : reader;
    }

    /**
     * Builds a {@link SAXSource} wrapping the payload, without an explicit parser; used by the unconfigured-side TrAX controls.
     */
    private static SAXSource permissiveSaxSource(final String xml) {
        return new SAXSource(permissiveReader(), new InputSource(new StringReader(xml)));
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

    /** Builds a raw {@link XMLReader} from a deliberately permissive {@link SAXParserFactory} and secures it via {@link SecureSAXParserFactory#secure(XMLReader)}. */
    private static XMLReader rawSecureXMLReader() throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        if (!IS_ANDROID) {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        }
        return SecureSAXParserFactory.secure(factory.newSAXParser().getXMLReader());
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
     * Builds a {@link Transformer} from {@code templates} with {@link #STRICT_REPORTER} installed as its error listener.
     */
    private static Transformer strictTransformer(final Templates templates) throws TransformerConfigurationException {
        final Transformer transformer = templates.newTransformer();
        transformer.setErrorListener(STRICT_REPORTER);
        return transformer;
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

    /** Probes a JAXP configuration call once at class load; {@code false} where the platform default implementation throws (for example Android). */
    private static boolean supportsConfiguration(final Executable action) {
        try {
            action.execute();
            return true;
        } catch (final Throwable t) {
            return false;
        }
    }

    /** Runs the action and silently swallows any thrown exception; used to apply best-effort permissive-side flags that may not be supported. */
    private static void suppressException(final Executable action) {
        try {
            action.execute();
        } catch (final Throwable e) {
            // Ignore
        }
    }

    private static String templatesCompileAndTransform(final TransformerFactory factory, final Source xslt) throws TransformerException {
        final StringWriter sink = new StringWriter();
        final Templates templates = strictTemplates(factory, xslt);
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
