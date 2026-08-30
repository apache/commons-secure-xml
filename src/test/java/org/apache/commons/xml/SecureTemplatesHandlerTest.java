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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

class SecureTemplatesHandlerTest {

    private static final class RecordingHandler extends DefaultHandler implements TemplatesHandler {

        final List<String> calls = new ArrayList<>();

        Templates templates;

        String systemId = "initial";

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            calls.add("characters:" + start + ':' + length);
        }

        @Override
        public void endDocument() {
            calls.add("endDocument");
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            calls.add("endElement:" + uri + ':' + localName + ':' + qName);
        }

        @Override
        public void endPrefixMapping(final String prefix) {
            calls.add("endPrefixMapping:" + prefix);
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public Templates getTemplates() {
            return templates;
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) {
            calls.add("ignorableWhitespace:" + start + ':' + length);
        }

        @Override
        public void processingInstruction(final String target, final String data) {
            calls.add("processingInstruction:" + target + ':' + data);
        }

        @Override
        public void setDocumentLocator(final Locator locator) {
            calls.add("setDocumentLocator");
        }

        @Override
        public void setSystemId(final String value) {
            systemId = value;
            calls.add("setSystemId:" + value);
        }

        @Override
        public void skippedEntity(final String name) {
            calls.add("skippedEntity:" + name);
        }

        @Override
        public void startDocument() {
            calls.add("startDocument");
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts) {
            calls.add("startElement:" + uri + ':' + localName + ':' + qName);
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) {
            calls.add("startPrefixMapping:" + prefix + ':' + uri);
        }
    }

    @Test
    void forwardsEveryTemplatesHandlerMethodAndWrapsTemplates() throws Exception {
        final RecordingHandler delegate = new RecordingHandler();
        delegate.templates = TransformerFactory.newInstance()
                .newTemplates(new StreamSource(new StringReader("<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'/>")));
        final SecureTemplatesHandler handler = new SecureTemplatesHandler(delegate, null, null, false);
        final char[] chars = { 'x', 'y' };
        handler.characters(chars, 1, 1);
        handler.endDocument();
        handler.endElement("u", "l", "q");
        handler.endPrefixMapping("p");
        handler.ignorableWhitespace(chars, 0, 2);
        handler.processingInstruction("target", "data");
        handler.setDocumentLocator(null);
        handler.setSystemId("system");
        handler.skippedEntity("entity");
        handler.startDocument();
        handler.startElement("u", "l", "q", null);
        handler.startPrefixMapping("p", "u");
        assertEquals("system", handler.getSystemId());
        assertInstanceOf(SecureTemplates.class, handler.getTemplates());
        assertEquals(12, delegate.calls.size());
    }

    @Test
    void preservesNullTemplates() {
        final RecordingHandler delegate = new RecordingHandler();
        assertNull(new SecureTemplatesHandler(delegate, null, null, false).getTemplates());
        assertSame(null, delegate.templates);
    }
}
