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

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests that an untrusted schema's content-model expansion is bounded, the one processing limit no reader can supply.
 *
 * <p>{@link BillionLaughsTest} covers entity expansion, which the secure reader injected into every {@code Source} bounds before a schema document reaches the
 * loader. {@code maxOccurs} is a different mechanism: the loader expands a repeated particle into content-model nodes while building the DFA, which happens
 * after parsing and never touches the reader. The bound for it is the schema implementation's own limit ({@code maxOccurLimit}, 3,000 nodes on Xerces), which
 * external Xerces installs only when {@code FEATURE_SECURE_PROCESSING} is set on the {@link SchemaFactory}.</p>
 *
 * <p>The expansion is lazy on Xerces: {@code newSchema} returns in milliseconds whatever {@code maxOccurs} says, and the nodes are built on first validation.
 * The payload therefore has to be validated, not just compiled, and the assertion accepts a rejection at either step. The repeated particle holds two elements
 * so it cannot be collapsed into Xerces' compact repeating-leaf form, and {@link #MAX_OCCURS} clears both limits by little enough that an unbounded run still
 * finishes, in seconds, rather than exhausting the heap.</p>
 */
@Tag("schema")
class SchemaContentModelLimitTest {

    /** Above both recognized implementations' limits (3,000 nodes on Xerces, 5,000 on the stock JDK); an unbounded run still finishes in seconds. */
    private static final int MAX_OCCURS = 10_000;

    private static String maxOccursPayload() {
        return "<?xml version=\"1.0\"?>\n"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                + "  <xs:element name=\"root\" type=\"bomb\"/>\n"
                + "  <xs:complexType name=\"bomb\">\n"
                + "    <xs:sequence>\n"
                + "      <xs:sequence minOccurs=\"0\" maxOccurs=\"" + MAX_OCCURS + "\">\n"
                + "        <xs:element name=\"a\" type=\"xs:string\"/>\n"
                + "        <xs:element name=\"b\" type=\"xs:string\"/>\n"
                + "      </xs:sequence>\n"
                + "    </xs:sequence>\n"
                + "  </xs:complexType>\n"
                + "</xs:schema>\n";
    }

    /** Compiles the payload through {@code factory} and validates a matching instance, the step that forces the expansion. */
    private static void compileAndValidate(final SchemaFactory factory) throws Exception {
        factory.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        final Schema schema = factory.newSchema(AttackTestSupport.streamSource(maxOccursPayload()));
        final Validator validator = schema.newValidator();
        validator.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        validator.validate(AttackTestSupport.streamSource("<root><a>x</a><b>y</b></root>"));
    }

    @Test
    void secureSchemaBoundsContentModelExpansion() {
        AttackTestSupport.assertParseFails(() -> compileAndValidate(SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)),
                "Schema content-model expansion", org.xml.sax.SAXException.class);
    }

    @Test
    void unconfiguredSchemaWithSecureProcessingBoundsContentModelExpansion() {
        // Control: the payload does trip the limit once secure processing is on, so a pass above is the limit firing rather than the payload being harmless.
        AttackTestSupport.assertParseFails(() -> {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            compileAndValidate(factory);
        }, "Schema content-model expansion", org.xml.sax.SAXException.class);
    }

    @Test
    void unconfiguredSchemaValidatesWhereTheLimitIsOptional() {
        // Control: the payload is a valid schema and instance, so a rejection above is the limit firing and not a malformed fixture. It is skipped on an
        // implementation that bounds the expansion unconditionally (the stock JDK), where there is no unbounded run to compare against.
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        AttackTestSupport.assumeDoesNotThrow(() -> compileAndValidate(factory));
    }
}
