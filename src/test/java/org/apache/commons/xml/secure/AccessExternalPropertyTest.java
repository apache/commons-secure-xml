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
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.XMLReader;

/**
 * Checks that loosening a JAXP 1.5 {@code accessExternal*} property to {@code all} on a secured factory does not reopen an external fetch.
 *
 * <p>The threat model lists these properties under "Settings you may modify": the securing is independent of them, because a resource supplied by a resolver
 * bypasses their checks and the resolver floor covers every external reference. The whole suite already runs with the {@code javax.xml.accessExternal*} system
 * properties set to {@code all} (see the surefire configuration), so this test guards the one route the system properties cannot: a future recipe that set a
 * property to the empty string and relied on it would be loosened by a caller's per-factory {@code all}, which no system-property-based run could detect.</p>
 *
 * <p>Payloads and expected outcomes mirror {@link ExternalDtdTest} (external DTD via {@code DOCTYPE SYSTEM}), {@link SchemaImportTest} ({@code xs:import}) and
 * {@link TemplatesImportTest} ({@code xsl:import}). An implementation that rejects the property has no knob to loosen, so each set runs through
 * {@link AttackTestSupport#assumeDoesNotThrow} and the test skips there (Android, external Apache Xerces).</p>
 */
class AccessExternalPropertyTest {

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

    @Test
    @Tag("dom")
    void secureDomWithAccessExternalAllDoesNotLeak() {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES,
                "Skipped: platform DOM does not resolve user-defined entities");
        final DocumentBuilderFactory factory = SecureDocumentBuilderFactory.newInstance();
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setAttribute(TestConstants.ACCESS_EXTERNAL_DTD, "all"));
        AttackTestSupport.assertDomDoesNotLeak(factory, xmlPayload());
    }

    @Test
    @Tag("sax")
    void secureSaxWithAccessExternalAllDoesNotLeak() throws Exception {
        final XMLReader reader = SecureSAXParserFactory.newInstance().newSAXParser().getXMLReader();
        AttackTestSupport.assumeDoesNotThrow(() -> reader.setProperty(TestConstants.ACCESS_EXTERNAL_DTD, "all"));
        AttackTestSupport.assertSaxDoesNotLeak(reader, xmlPayload());
    }

    @Test
    @Tag("schema")
    void secureSchemaDoctypeWithAccessExternalAllDoesNotLeak() {
        final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setProperty(TestConstants.ACCESS_EXTERNAL_DTD, "all"));
        AttackTestSupport.assertSchemaDoesNotLeak(factory, AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("schema")
    void secureSchemaImportWithAccessExternalAllBlocks() {
        final SchemaFactory factory = SecureSchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setProperty(TestConstants.ACCESS_EXTERNAL_SCHEMA, "all"));
        AttackTestSupport.assertSchemaBlocks(factory, AttackTestSupport.resourceSource("with-import.xsd"));
    }

    @Test
    @Tag("trax")
    void secureTemplatesImportWithAccessExternalAllDoesNotLeak() {
        final TransformerFactory factory = SecureTransformerFactory.newInstance();
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setAttribute(TestConstants.ACCESS_EXTERNAL_STYLESHEET, "all"));
        AttackTestSupport.assertTemplatesDoesNotLeak(factory, AttackTestSupport.resourceSource("with-import.xsl"));
    }

    @Test
    @Tag("trax")
    void secureTransformerDoctypeWithAccessExternalAllDoesNotLeak() {
        final TransformerFactory factory = SecureTransformerFactory.newInstance();
        AttackTestSupport.assumeDoesNotThrow(() -> factory.setAttribute(TestConstants.ACCESS_EXTERNAL_DTD, "all"));
        AttackTestSupport.assertTransformerDoesNotLeak(factory, xmlPayload());
    }
}
