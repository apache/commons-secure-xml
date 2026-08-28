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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.XMLFilter;

@Tag("trax")
class XMLFilterParseStringTest {

    private static final String IDENTITY_XSLT =
    // @formatter:off
        "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
        "<xsl:template match=\"/\"><xsl:copy-of select=\".\"/></xsl:template>" +
        "</xsl:stylesheet>";
    // @formatter:on

    private static String entityPayload() {
        // @formatter:off
        return "<?xml version=\"1.0\"?>\n" +
               "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + AttackTestSupport.resourceUrl("referenced.txt") + "\">]>\n" +
               "<root>&xxe;</root>";
        // @formatter:on
    }

    @Test
    void secureFilterParseStringDoesNotLeakExternalEntity(@TempDir final Path tmpDir) throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) SecureTransformerFactory.newInstance();
        final Templates templates = factory.newTemplates(new StreamSource(new StringReader(IDENTITY_XSLT)));
        final XMLFilter filter = factory.newXMLFilter(templates);
        final Path tmp = Files.createTempFile(tmpDir, "xmlfilter", ".xml");
        Files.write(tmp, entityPayload().getBytes(StandardCharsets.UTF_8));
        final StringBuilder out = new StringBuilder();
        filter.setContentHandler(AttackTestSupport.capturingHandler(out));
        // public API – XMLFilter.parse(String) – the path that ends in XMLFilterImpl.parse(String)
        filter.parse(tmp.toUri().toString());
        assertFalse(out.toString().contains(AttackTestSupport.LEAKED_MARKER), "external entity leaked through XMLFilter.parse(String)");
    }
}
