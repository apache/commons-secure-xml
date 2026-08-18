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

package org.apache.commons.xml;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Checks that the JAXP {@code reset()} lifecycle methods do not strip the hardening floors.
 *
 * <p>The JAXP reset contract returns an object to its just-created state, and the stock JDK / Xerces implementations take that literally: they re-install
 * their initial (null) resolvers, silently removing any floor the hardened wrappers installed after creation. Each test resets a hardened object and asserts
 * that an external reference is still either blocked at parse or resolved to empty content afterwards; the tests are skipped on platforms whose
 * implementation does not support {@code reset()} at all (there the hardening cannot be stripped in the first place).</p>
 */
class ResetHardeningTest {

    /** systemId of the external general entity the floor must keep covering after a reset (its content carries {@link AttackTestSupport#LEAKED_MARKER}). */
    private static final String UNLISTED = AttackTestSupport.resourceUrl("referenced.xml").toString();

    private static String entityPayload(final String entitySystemId) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [\n  <!ENTITY xxe SYSTEM \"" + entitySystemId + "\">\n]>\n"
                + "<root>&xxe;</root>";
    }

    @Test
    @Tag("dom")
    void documentBuilderResetKeepsEntityResolverFloor() throws Exception {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES, "platform DOM does not resolve user-defined entities");
        final DocumentBuilder builder = XmlFactories.newDocumentBuilderFactory().newDocumentBuilder();
        AttackTestSupport.assumeDoesNotThrow(builder::reset);
        try {
            final Document doc = builder.parse(AttackTestSupport.inputSource(entityPayload(UNLISTED)));
            assertFalse(doc.getDocumentElement().getTextContent().contains(AttackTestSupport.LEAKED_MARKER), "external entity leaked after reset");
        } catch (final SAXException blocked) {
            // Acceptable: rejected at parse rather than resolved to empty.
        }
    }

    @Test
    @Tag("sax")
    void saxParserResetKeepsEntityResolverFloor() throws Exception {
        final SAXParser parser = XmlFactories.newSAXParserFactory().newSAXParser();
        // Materialize the hardened reader before the reset, so a stale cached wrapper would be observable.
        parser.getXMLReader();
        AttackTestSupport.assumeDoesNotThrow(parser::reset);
        final XMLReader reader = parser.getXMLReader();
        final String text;
        try {
            text = AttackTestSupport.captureCharacters(reader, entityPayload(UNLISTED));
        } catch (final SAXException blocked) {
            return; // Acceptable: rejected at parse rather than resolved to empty.
        }
        assertFalse(text.contains(AttackTestSupport.LEAKED_MARKER), "external entity leaked after reset:\n" + text);
    }

    @Test
    @Tag("schema")
    void validatorResetKeepsResourceResolverFloor() throws Exception {
        // A Schema built without sources validates against the instance's xsi:schemaLocation hints, so the resolver floor is the only barrier between the
        // validator and the external schema fetch.
        final Validator validator = XmlFactories.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema().newValidator();
        AttackTestSupport.assumeDoesNotThrow(validator::reset);
        validator.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        // schema-location-instance.xml hints at schema-location.xsd, which declares its root: a validator whose floor was stripped fetches it and validates
        // cleanly, while the floor resolves the hint to empty content, which fails the validation.
        AttackTestSupport.assertParseFails(() -> validator.validate(AttackTestSupport.resourceSource("schema-location-instance.xml")),
                "Validator after reset", SAXException.class, SecurityException.class);
    }

    @Test
    @Tag("trax")
    void transformerResetKeepsUriResolverFloor() throws Exception {
        // with-document.xsl copies document('referenced.xml') into the output at transform time, so a transformer whose floor was stripped leaks the marker.
        final Transformer transformer = XmlFactories.newTransformerFactory()
                .newTemplates(AttackTestSupport.resourceSource("with-document.xsl")).newTransformer();
        AttackTestSupport.assumeDoesNotThrow(transformer::reset);
        final StringWriter sink = new StringWriter();
        try {
            transformer.transform(AttackTestSupport.streamSource("<root/>"), new StreamResult(sink));
        } catch (final TransformerException blocked) {
            return; // Acceptable: rejected at transform rather than resolved to empty.
        }
        assertFalse(sink.toString().contains(AttackTestSupport.LEAKED_MARKER), "document() leaked after reset:\n" + sink);
    }
}
