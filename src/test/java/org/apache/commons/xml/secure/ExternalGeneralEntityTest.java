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

import javax.xml.transform.Source;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks whether parsers can pull in an external general entity declared inline in the internal subset.
 *
 * <p>The wrapper declares {@code <!ENTITY xxe SYSTEM "file:.../referenced.txt">} and uses {@code &xxe;} in the body. The general entity expands to the
 * content of {@code src/test/resources/leaked/referenced.txt} when the external reference is resolved. A secure parser keeps the entity declared but
 * resolves its content to empty, so the parse completes without the file's content; an unconfigured parser fetches the file, the entity resolves, and the
 * parse succeeds.</p>
 *
 * <p>Each parser type is exercised twice as a pair (unconfigured factory, expected to parse; secure factory, expected to complete without leaked
 * content):</p>
 *
 * <ul>
 *   <li>DOM, SAX and StAX direct XML parsing.</li>
 *   <li>{@code SchemaFactory.newSchema(Source)} compilation of an XSD whose source has the entity-bearing DOCTYPE.</li>
 *   <li>{@link Validator#validate(Source)} of an instance whose source has the entity-bearing DOCTYPE.</li>
 *   <li>Identity {@code Transformer} reading the input XML.</li>
 *   <li>{@code TransformerFactory.newTransformer(Source)} compilation of a stylesheet whose source has the entity-bearing DOCTYPE.</li>
 * </ul>
 */
class ExternalGeneralEntityTest {

    private static final String INSERTION = "&xxe;";

    private static String withDoctype(final String rootQName, final String body) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE " + rootQName + " [\n"
                + "  <!ENTITY xxe SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.txt") + "\">\n"
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
    void secureStaxDoesNotLeak() {
        AttackTestSupport.assertStaxDoesNotLeak(xmlPayload());
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
