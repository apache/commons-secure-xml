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

import java.util.Objects;

import javax.xml.parsers.SAXParser;
import javax.xml.validation.Schema;

import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderAdapter;

/**
 * {@link SAXParser} that exposes a hardened {@link XMLReader} and a matching SAX 1 {@link Parser}.
 *
 * <p>Both views are produced from the same hardened reader, so a caller reaching the parser through either the SAX 2 ({@link #getXMLReader()}) or the legacy
 * SAX 1 ({@link #getParser()}) path gets the same hardening. The SAX 1 view matters because some consumers, such as Xalan's identity transformer, still ask
 * for a {@link Parser}.</p>
 *
 * <p>The hardened reader is computed lazily on first access and cached: hardening an {@link XMLReader} can install a new wrapper (Android's Expat path), so
 * every parse must run through the same instance. The {@code parse(...)} overloads inherited from {@link SAXParser} dispatch virtually to {@link #getXMLReader()}
 * and {@link #getParser()}, so they too run through the hardened views without further overrides.</p>
 */
final class SecureSAXParser extends SAXParser {

    private final SAXParser delegate;

    private XMLReader hardenedReader;
    private Parser hardenedParser;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    SecureSAXParser(final SAXParser delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @SuppressWarnings("deprecation")
    public Parser getParser() throws SAXException {
        if (hardenedParser == null) {
            final XMLReader reader = getXMLReader();
            // Reuse the reader directly if it already is a SAX 1 parser; otherwise adapt it, so the SAX 1 path runs through the same hardened reader.
            hardenedParser = reader instanceof Parser ? (Parser) reader : new XMLReaderAdapter(reader);
        }
        return hardenedParser;
    }

    @Override
    public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getProperty(name);
    }

    @Override
    public Schema getSchema() {
        return delegate.getSchema();
    }

    @Override
    public XMLReader getXMLReader() throws SAXException {
        if (hardenedReader == null) {
            hardenedReader = HardeningSAXParserFactory.harden(delegate.getXMLReader());
        }
        return hardenedReader;
    }

    @Override
    public boolean isNamespaceAware() {
        return delegate.isNamespaceAware();
    }

    @Override
    public boolean isValidating() {
        return delegate.isValidating();
    }

    @Override
    public boolean isXIncludeAware() {
        return delegate.isXIncludeAware();
    }

    @Override
    public void reset() {
        delegate.reset();
        // The JAXP reset contract reverts the delegate to its just-created state, which strips the post-creation reader hardening.
        // We reset the cached readers, so hardening can be applied again.
        hardenedReader = null;
        hardenedParser = null;
    }

    @Override
    public void setProperty(final String name, final Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setProperty(name, value);
    }

}
