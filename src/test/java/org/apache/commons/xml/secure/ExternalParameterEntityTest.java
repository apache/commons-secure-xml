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

package org.apache.commons.xml.secure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tests whether parsers can pull in an external DTD via a parameter-entity reference inside the internal subset.
 *
 * <p>The wrapper declares a parameter entity {@code %xxe;} pointing at {@code src/test/resources/leaked/referenced.dtd} and immediately references it in the
 * internal subset; once expanded, the entity declarations from {@code referenced.dtd} (in particular {@code <!ENTITY leaked "...">}) become part of the
 * document's DTD. Each wrapper body then references {@code &leaked;}, and a secure parser resolves the parameter-entity expansion to empty, which leaves
 * {@code &leaked;} undeclared. This is the one payload in the suite with a genuinely undeclared entity, so the secure outcome is dual: the parser either
 * skips the undefined reference (no leak) or rejects it (per XML 1.0 section 4.1 the reference is an unreported validity constraint here, but the JDK's parser
 * reports it as a well-formedness error and Woodstox rejects undeclared references unconditionally). Either way the external DTD is never fetched. An
 * unconfigured parser fetches and resolves it, and the parse succeeds.</p>
 *
 * <p>Each parser type is exercised twice as a pair (unconfigured factory, expected to parse; secure factory, expected to block or complete without leaked
 * content):</p>
 *
 * <ul>
 *   <li>DOM, SAX and StAX direct XML parsing.</li>
 *   <li>{@code SchemaFactory.newSchema(Source)} compilation of an XSD whose source has the parameter-entity DOCTYPE.</li>
 *   <li>{@link Validator#validate(Source)} of an instance whose source has the parameter-entity DOCTYPE.</li>
 *   <li>Identity {@code Transformer} reading the input XML.</li>
 *   <li>{@code TransformerFactory.newTransformer(Source)} compilation of a stylesheet whose source has the parameter-entity DOCTYPE.</li>
 * </ul>
 */
class ExternalParameterEntityTest {

    /**
     * Benign payload used to probe whether the DOM parser tolerates a parameter-entity reference inside the internal subset.
     */
    private static final String DOM_PARAMETER_ENTITY_PROBE =
            "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE root [\n"
            + "  <!ENTITY % p \"<!ENTITY child 'A'>\">\n"
            + "  %p;\n"
            + "]>\n"
            + "<root>&child;</root>";

    /**
     * Set to {@code true} when the platform's DOM parser supports parameter-entity references.
     *
     * <p>Android's {@code KXmlParser} currently fails this test.</p>
     */
    private static final boolean DOM_ACCEPTS_PARAMETER_ENTITIES = probeDomAcceptsParameterEntities();

    private static final String INSERTION = "&leaked;";

    /**
     * Benign payload used to probe whether the SAX parser invokes the {@link org.xml.sax.EntityResolver} for an external parameter-entity reference. The
     * payload references an external parameter entity by an unfetchable {@code about:invalid} URL; the probe's resolver returns an empty {@code InputSource}
     * to let parsing complete without a network call, and reports whether it was consulted at all.
     */
    private static final String SAX_PARAMETER_ENTITY_PROBE =
            "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE root [\n"
            + "  <!ENTITY % p SYSTEM \"about:invalid\">\n"
            + "  %p;\n"
            + "]>\n"
            + "<root/>";

    /**
     * Set to {@code true} when the platform's SAX parser invokes the entity resolver for an external parameter-entity reference. False on Android because
     * libexpat's default leaves {@code XML_SetParamEntityParsing} disabled and the harmony native bridge does not enable it, so {@code %p;} is silently
     * skipped without consulting the resolver.
     */
    private static final boolean SAX_RESOLVES_PARAMETER_ENTITIES = probeSaxResolvesParameterEntities();

    private static boolean probeDomAcceptsParameterEntities() {
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(AttackTestSupport.inputSource(DOM_PARAMETER_ENTITY_PROBE));
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private static boolean probeSaxResolvesParameterEntities() {
        final AtomicBoolean called = new AtomicBoolean();
        assertDoesNotThrow(() -> {
            final XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) -> {
                if ("about:invalid".equals(systemId)) {
                    called.set(true);
                }
                return new InputSource(new StringReader(""));
            });
            reader.setContentHandler(new DefaultHandler());
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(AttackTestSupport.inputSource(SAX_PARAMETER_ENTITY_PROBE));
        });
        return called.get();
    }

    private static String withDoctype(final String rootQName, final String body) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE " + rootQName + " [\n"
                + "  <!ENTITY % xxe SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.dtd") + "\">\n"
                + "  %xxe;\n"
                + "]>\n"
                + body + "\n";
    }

    private static String xmlPayload() {
        return withDoctype("root", AttackTestSupport.xmlBody(INSERTION));
    }

    private static String xsdPayload() {
        return withDoctype("xs:schema", AttackTestSupport.xsdBody(INSERTION));
    }

    private static String xsltPayload() {
        return withDoctype("xsl:stylesheet", AttackTestSupport.xsltBody(INSERTION));
    }

    @Test
    @Tag("dom")
    void secureDomBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(DOM_ACCEPTS_PARAMETER_ENTITIES,
                "Skipped: platform DOM does not accept parameter entities");
        AttackTestSupport.assertDomBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("sax")
    void secureSaxBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertSaxBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("schema")
    void secureSchemaBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertSchemaBlocksOrDoesNotLeak(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void secureStaxBlocksOrDoesNotLeak() {
        AttackTestSupport.assertStaxBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("trax")
    void secureTemplatesBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertTemplatesBlocksOrDoesNotLeak(AttackTestSupport.streamSource(xsltPayload()));
    }

    @Test
    @Tag("trax")
    void secureTransformerBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertTransformerBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("schema")
    void secureValidatorBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertValidatorBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("sax")
    void secureXmlReaderBlocksOrDoesNotLeak() {
        Assumptions.assumeTrue(SAX_RESOLVES_PARAMETER_ENTITIES,
                "Skipped: platform SAX parser does not invoke the entity resolver for parameter entities");
        AttackTestSupport.assertXmlReaderBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("dom")
    void unconfiguredDomParses() {
        Assumptions.assumeTrue(DOM_ACCEPTS_PARAMETER_ENTITIES,
                "Skipped: platform DOM does not accept parameter entities");
        AttackTestSupport.assertPermissiveDomParses(xmlPayload());
    }

    @Test
    @Tag("sax")
    void unconfiguredSaxParses() {
        AttackTestSupport.assertPermissiveSaxParses(xmlPayload());
    }

    @Test
    @Tag("schema")
    void unconfiguredSchemaCompiles() {
        AttackTestSupport.assertPermissiveSchemaCompiles(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void unconfiguredStaxParses() {
        AttackTestSupport.assertPermissiveStaxParses(xmlPayload());
    }

    @Test
    @Tag("trax")
    void unconfiguredTemplatesCompiles() {
        AttackTestSupport.assertPermissiveTemplatesCompiles(xsltPayload());
    }

    @Test
    @Tag("trax")
    void unconfiguredTransformerTransforms() {
        AttackTestSupport.assertPermissiveTransformerTransforms(xmlPayload());
    }

    @Test
    @Tag("schema")
    void unconfiguredValidatorValidates() {
        AttackTestSupport.assertPermissiveValidatorValidates(xmlPayload());
    }
}
