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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests that a plain document without a {@code DOCTYPE} declaration parses cleanly through every secure JAXP surface.
 *
 * <p>This is the realistic 99% case for secure input; the secure contract being verified is "documents without a DOCTYPE parse cleanly through every
 * JAXP surface" so accidental tightening (for example, a resolver that refuses the synthetic-external-subset hook) is caught.</p>
 */
class NoDoctypeTest {

    private static String payload() {
        return "<?xml version=\"1.0\"?>\n"
                + AttackTestSupport.xmlBody("hello") + "\n";
    }

    private static String xsdPayload() {
        return "<?xml version=\"1.0\"?>\n"
                + AttackTestSupport.xsdBody("hello") + "\n";
    }

    private static String xsltPayload() {
        return "<?xml version=\"1.0\"?>\n"
                + AttackTestSupport.xsltBody("hello") + "\n";
    }

    @Test
    @Tag("dom")
    void secureDomParses() {
        AttackTestSupport.assertDomParses(payload());
    }

    @Test
    @Tag("sax")
    void secureSaxParses() {
        AttackTestSupport.assertSaxParses(payload());
    }

    @Test
    @Tag("schema")
    void secureSchemaCompiles() {
        AttackTestSupport.assertSchemaCompiles(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void secureStaxParses() {
        AttackTestSupport.assertStaxParses(payload());
    }

    @Test
    @Tag("trax")
    void secureTemplatesCompiles() {
        AttackTestSupport.assertTemplatesCompiles(AttackTestSupport.streamSource(xsltPayload()));
    }

    @Test
    @Tag("trax")
    void secureTransformerTransforms() {
        AttackTestSupport.assertTransformerTransforms(payload());
    }

    @Test
    @Tag("schema")
    void secureValidatorValidates() {
        AttackTestSupport.assertValidatorValidates(payload());
    }

    @Test
    @Tag("sax")
    void secureXmlReaderParses() {
        AttackTestSupport.assertXmlReaderParses(payload());
    }
}
