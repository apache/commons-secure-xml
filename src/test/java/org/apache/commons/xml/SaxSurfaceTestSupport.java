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

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Shared plumbing for the {@link SAXTransformerFactory} extension-surface tests ({@code TransformerHandlerTest}, {@code TemplatesHandlerTest},
 * {@code XMLFilterTest}).
 *
 * <p>The handler products consume SAX events the caller drives, so these tests need a reader to feed them; the feed reader is a hardened, namespace-aware one,
 * because the vector under test is what the <em>transform</em> resolves, not what the feed parses.</p>
 */
final class SaxSurfaceTestSupport {

    /** Feeds the input's SAX events into the handler through a hardened, namespace-aware reader. */
    static void feed(final ContentHandler handler, final InputSource input) throws Exception {
        final SAXParserFactory factory = SecureSAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.setErrorHandler(AttackTestSupport.STRICT_REPORTER);
        reader.parse(input);
    }

    /** The hardened factory, as its runtime {@link SAXTransformerFactory} type. */
    static SAXTransformerFactory hardenedFactory() {
        return (SAXTransformerFactory) SecureTransformerFactory.newInstance();
    }

    /** Opens a fixture under {@code leaked/} as an {@link InputSource} preserving its system id, so relative hrefs resolve normally. */
    static InputSource resourceInput(final String name) throws IOException {
        final URL url = AttackTestSupport.resourceUrl(name);
        final InputSource input = new InputSource(url.openStream());
        input.setSystemId(url.toString());
        return input;
    }

    /** A benign {@code <root/>} input document. */
    static InputSource rootInput() {
        return new InputSource(new StringReader("<root/>"));
    }

    private SaxSurfaceTestSupport() {
    }
}
