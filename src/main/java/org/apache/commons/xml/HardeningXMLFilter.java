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
import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link XMLFilter} that transforms the parsed input through a {@link HardeningTemplates} and emits the result as SAX events.
 *
 * <p>Composed from the library's own wrappers instead of delegating to the implementation's filter, because the implementation filters self-provision an
 * unhardened reader for the input (the stock JDK's does so as early as {@code setContentHandler}) and cast a supplied {@link javax.xml.transform.Templates} to
 * their own type, which a wrapped Templates is not. Here the input is parsed by the parent reader, a hardened one installed on first {@code parse} when the
 * caller has not set a parent (a caller-set parent is trusted configuration, used as-is), and the transformation runs on a {@link HardeningTransformer}, so
 * runtime {@code document()} sits on the resolver floor.</p>
 */
final class HardeningXMLFilter extends XMLFilterImpl {

    private final HardeningTemplates templates;

    /**
     * Constructs a new instance.
     *
     * @param templates the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningXMLFilter(final HardeningTemplates templates) {
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public void parse(final InputSource input) throws SAXException, IOException {
        final ContentHandler handler = getContentHandler();
        if (handler == null) {
            throw new SAXException("No ContentHandler set on the XMLFilter to receive the transformation result");
        }
        if (getParent() == null) {
            try {
                setParent(HardeningSAXParserFactory.newHardenedReader());
            } catch (final TransformerException e) {
                throw new SAXException(e);
            }
        }
        final SAXResult result = new SAXResult(handler);
        if (handler instanceof LexicalHandler) {
            result.setLexicalHandler((LexicalHandler) handler);
        }
        try {
            // A new HardeningTransformer per parse: the floor is installed on it, and transformers are not reusable across concurrent parses.
            final Transformer transformer = templates.newTransformer();
            transformer.transform(new SAXSource(getParent(), input), result);
        } catch (final TransformerException e) {
            throw new SAXException(e);
        }
    }
}
