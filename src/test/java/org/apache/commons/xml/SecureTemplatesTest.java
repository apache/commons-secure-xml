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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("trax")
class SecureTemplatesTest {

    @Test
    void delegatesPropertiesAndWrapsProducedTransformer() throws Exception {
        final Templates delegate = TransformerFactory.newInstance().newTemplates(new StreamSource(
                new StringReader("<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:template match='/'/></xsl:stylesheet>")));
        final SecureTemplates templates = new SecureTemplates(delegate, (href, base) -> null, null, false);
        assertNotNull(templates.getOutputProperties());
        assertInstanceOf(SecureTransformer.class, templates.newTransformer());
        assertNotNull(templates.getDelegate());
    }

    @Test
    void preservesANullTransformerFromTheDelegate() throws Exception {
        final Templates delegate = new Templates() {

            @Override
            public Properties getOutputProperties() {
                return new Properties();
            }

            @Override
            public Transformer newTransformer() {
                return null;
            }
        };
        assertNull(new SecureTemplates(delegate, null, null, false).newTransformer());
    }
}
