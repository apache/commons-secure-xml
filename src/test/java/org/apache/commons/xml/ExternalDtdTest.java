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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.xml.transform.Source;
import javax.xml.validation.Validator;

/**
 * Checks whether parsers can pull in an external DTD declared via {@code <!DOCTYPE root SYSTEM "...">}.
 *
 * <p>The wrapper points at {@code src/test/resources/leaked/referenced.dtd}, which declares a {@code leaked} entity. Each wrapper body references
 * {@code &leaked;}, so the entity can only resolve if the DTD is actually fetched: a secure parser resolves the external subset to empty, leaving
 * {@code &leaked;} undeclared, and skips the undefined reference (per XML 1.0 section 4.1 an undeclared reference is a validity constraint when the DOCTYPE
 * has a system identifier, so a non-validating parse completes); an unconfigured parser fetches the DTD, the entity resolves, and the parse succeeds. The one
 * exception is Woodstox, which rejects undeclared references unconditionally, so the StAX case accepts a block as well.</p>
 *
 * <p>Each parser type is exercised twice as a pair (unconfigured factory, expected to parse; secure factory, expected to complete without leaked
 * content):</p>
 *
 * <ul>
 *   <li>DOM, SAX and StAX direct XML parsing.</li>
 *   <li>{@code SchemaFactory.newSchema(Source)} compilation of an XSD whose source has the DOCTYPE.</li>
 *   <li>{@link Validator#validate(Source)} of an instance whose source has the DOCTYPE.</li>
 *   <li>Identity {@code Transformer} reading the input XML.</li>
 *   <li>{@code TransformerFactory.newTransformer(Source)} compilation of a stylesheet whose source has the DOCTYPE.</li>
 * </ul>
 */
class ExternalDtdTest {

    private static final String INSERTION = "&leaked;";

    private static String withDoctype(final String rootQName, final String body) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE " + rootQName + " SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.dtd") + "\">\n"
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
    void secureDomDoesNotLeak() {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES,
                "Skipped: platform DOM does not resolve user-defined entities");
        AttackTestSupport.assertDomDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("sax")
    void secureSaxDoesNotLeak() {
        AttackTestSupport.assertSaxDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("schema")
    void secureSchemaDoesNotLeak() {
        AttackTestSupport.assertSchemaDoesNotLeak(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void secureStaxBlocksOrDoesNotLeak() {
        // Woodstox rejects a reference to an entity declared only in the emptied external subset; the Xerces lineage skips it as an unreported validity
        // constraint because the DOCTYPE has a system identifier.
        AttackTestSupport.assertStaxBlocksOrDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("trax")
    void secureTemplatesDoesNotLeak() {
        AttackTestSupport.assertTemplatesDoesNotLeak(AttackTestSupport.streamSource(xsltPayload()));
    }

    @Test
    @Tag("trax")
    void secureTransformerDoesNotLeak() {
        AttackTestSupport.assertTransformerDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("schema")
    void secureValidatorDoesNotLeak() {
        AttackTestSupport.assertValidatorDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("sax")
    void secureXmlReaderDoesNotLeak() {
        AttackTestSupport.assertXmlReaderDoesNotLeak(xmlPayload());
    }

    @Test
    @Tag("dom")
    void unconfiguredDomParses() {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES,
                "Skipped: platform DOM does not resolve user-defined entities");
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
