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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.StringWriter;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

class SecureTransformerHandlerTest {

    @Test
    void forwardsEveryTransformerHandlerMethod() throws Exception {
        final SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        final SecureTransformerHandler handler = new SecureTransformerHandler(factory.newTransformerHandler(), null, null, false);
        final char[] chars = { 'x' };
        handler.setResult(new StreamResult(new StringWriter()));
        handler.setDocumentLocator(new LocatorImpl());
        handler.setSystemId("system");
        handler.startDocument();
        handler.startDTD("root", null, null);
        handler.endDTD();
        handler.startPrefixMapping("p", "urn:test");
        handler.startElement("", "root", "root", new AttributesImpl());
        handler.startCDATA();
        handler.characters(chars, 0, 1);
        handler.ignorableWhitespace(chars, 0, 1);
        handler.comment(chars, 0, 1);
        handler.endCDATA();
        handler.processingInstruction("t", "d");
        handler.notationDecl("n", "p", "s");
        handler.unparsedEntityDecl("e", "p", "s", "n");
        handler.startEntity("e");
        handler.endEntity("e");
        handler.skippedEntity("e");
        handler.endElement("", "root", "root");
        handler.endPrefixMapping("p");
        handler.endDocument();
        assertEquals("system", handler.getSystemId());
        assertInstanceOf(SecureTransformer.class, handler.getTransformer());
    }
}
