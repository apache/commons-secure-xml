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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;

import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

class SecureXPathExpressionTest {

    @Test
    void evaluatesEveryXPathExpressionOverload() throws Exception {
        final SecureXPathExpression expression = new SecureXPathExpression(XPathFactory.newInstance().newXPath().compile("/root/text()"), false);
        final InputSource source = new InputSource(new StringReader("<root>value</root>"));
        assertEquals("value", expression.evaluate(source));
        assertEquals("value", expression.evaluate(new InputSource(new StringReader("<root>value</root>")), XPathConstants.STRING));
        assertEquals("value", expression.evaluate(org.apache.commons.xml.SecureXPath.parse(new InputSource(new StringReader("<root>value</root>")), false)));
        assertEquals("value",
                expression.evaluate(SecureXPath.parse(new InputSource(new StringReader("<root>value</root>")), false), XPathConstants.STRING));
    }
}
