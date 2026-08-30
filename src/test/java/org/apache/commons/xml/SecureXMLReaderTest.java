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

import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.xml.sax.helpers.XMLFilterImpl;

class SecureXMLReaderTest {

    private static final class RecordingReader extends XMLFilterImpl {

        boolean inputSourceParsed;

        boolean systemIdParsed;

        @Override
        public void parse(final InputSource input) {
            inputSourceParsed = true;
        }

        @Override
        public void parse(final String systemId) {
            systemIdParsed = true;
        }
    }

    @Test
    void forwardsBothParseOverloads() throws Exception {
        final RecordingReader delegate = new RecordingReader();
        final SecureXMLReader reader = new SecureXMLReader(delegate);
        reader.parse(new InputSource());
        reader.parse("system");
        Assertions.assertTrue(delegate.inputSourceParsed);
        Assertions.assertTrue(delegate.systemIdParsed);
    }

    @Test
    void forwardsReaderConfigurationAndParse() throws Exception {
        final SecureXMLReader reader = new SecureXMLReader(SAXParserFactory.newInstance().newSAXParser().getXMLReader());
        final DefaultHandler handler = new DefaultHandler();
        reader.setContentHandler(handler);
        reader.setDTDHandler(handler);
        reader.setErrorHandler(handler);
        reader.setEntityResolver((publicId, systemId) -> null);
        reader.getContentHandler();
        reader.getDTDHandler();
        reader.getErrorHandler();
        reader.getEntityResolver();
        reader.parse(new InputSource(new StringReader("<root/>")));
        Assertions.assertThrows(IOException.class, () -> reader.parse("file:/definitely-not-present-commons-xml-test.xml"));
    }
}
