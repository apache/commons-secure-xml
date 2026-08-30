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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("trax")
class SecureTransformerTest {

    @Test
    void forwardsEveryTransformerMethod() throws Exception {
        final TransformerFactory factory = TransformerFactory.newInstance();
        final SecureTransformer transformer = new SecureTransformer(factory
                .newTemplates(new StreamSource(new StringReader(
                        "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template match='/'/></xsl:stylesheet>")))
                .newTransformer(), null, null, false);
        transformer.clearParameters();
        transformer.setParameter("p", "v");
        assertNotNull(transformer.getParameter("p"));
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        assertNotNull(transformer.getOutputProperty(OutputKeys.METHOD));
        transformer.setOutputProperties(new Properties());
        assertNotNull(transformer.getOutputProperties());
        transformer.setErrorListener(new ErrorListener() {

            @Override
            public void error(final TransformerException e) {
            }

            @Override
            public void fatalError(final TransformerException e) {
            }

            @Override
            public void warning(final TransformerException e) {
            }
        });
        assertNotNull(transformer.getErrorListener());
        transformer.setURIResolver((href, base) -> null);
        assertNotNull(transformer.getURIResolver());
        transformer.transform(new DOMSource(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()), new StreamResult(new StringWriter()));
        transformer.reset();
    }
}
