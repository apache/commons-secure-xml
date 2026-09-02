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

package org.apache.commons.xml.secure;

import java.io.IOException;
import java.util.Objects;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link XMLFilter} that transforms the parsed input through a {@link SecureTemplates} and emits the result as SAX events.
 *
 * <p>Composed from the library's own wrappers instead of delegating to the implementation's filter, because the implementation filters self-provision an
 * unsecured reader for the input (the stock JDK's does so as early as {@code setContentHandler}) and cast a supplied {@link javax.xml.transform.Templates} to
 * their own type, which a wrapped Templates is not. Here the input is parsed by the parent reader, a secure one installed on first {@code parse} when the
 * caller has not set a parent (a caller-set parent is trusted configuration, used as-is), and the transformation runs on a {@link SecureTransformer}, so
 * runtime {@code document()} sits on the resolver floor. The filter is also the transformer's {@link ErrorListener}, forwarding TrAX error reports to the
 * caller-set {@link org.xml.sax.ErrorHandler} the way the parent reader's SAX reports are.</p>
 */
final class SecureXMLFilter extends XMLFilterImpl implements ErrorListener {

    /**
     * Bridges a TrAX report to the SAX callback shape.
     *
     * @param e The reported exception.
     * @return The original {@link SAXParseException} where one is the cause, otherwise a synthetic one carrying the locator.
     */
    private static SAXParseException toSAXParseException(final TransformerException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof SAXParseException) {
            return (SAXParseException) cause;
        }
        // Embed the cause rather than the TrAX wrapper, so the originating exception stays directly reachable in the reported chain.
        final Exception embedded = cause instanceof Exception ? (Exception) cause : e;
        final SourceLocator locator = e.getLocator();
        return locator == null
                ? new SAXParseException(e.getMessage(), null, null, -1, -1, embedded)
                : new SAXParseException(e.getMessage(), locator.getPublicId(), locator.getSystemId(), locator.getLineNumber(), locator.getColumnNumber(), embedded);
    }

    private final SecureTemplates templates;

    /**
     * Constructs a new instance.
     *
     * @param templates The templates to wrap; must not be {@code null}.
     * @throws NullPointerException Thrown if {@code templates} is {@code null}.
     */
    SecureXMLFilter(final SecureTemplates templates) {
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    /**
     * Forwards a recoverable transformation error to the caller-set {@link org.xml.sax.ErrorHandler}, mirroring the SAX contract: the transformation continues
     * unless that handler throws.
     */
    @Override
    public void error(final TransformerException e) throws TransformerException {
        try {
            error(toSAXParseException(e));
        } catch (final SAXException se) {
            throw new TransformerException(se);
        }
    }

    /**
     * Forwards a fatal transformation error to the caller-set {@link org.xml.sax.ErrorHandler}, then fails the parse like a SAX parser does after
     * {@code fatalError}: some implementations' lenient default listeners would otherwise only print and abort the parse silently.
     */
    @Override
    public void fatalError(final TransformerException e) throws TransformerException {
        try {
            fatalError(toSAXParseException(e));
        } catch (final SAXException se) {
            throw new TransformerException(se);
        }
        throw e;
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
            setParent(SecureSAXParserFactory.newXMLReader(templates.overrideDefaultParser));
        }
        final XMLReader parent = getParent();
        // Like XMLFilterImpl.setupParse, minus the ContentHandler: the transformer owns the parent's content events and delivers the transformed stream to
        // the caller's handler through the SAXResult instead.
        parent.setEntityResolver(this);
        parent.setDTDHandler(this);
        parent.setErrorHandler(this);
        final SAXResult result = new SAXResult(handler);
        if (handler instanceof LexicalHandler) {
            result.setLexicalHandler((LexicalHandler) handler);
        }
        try {
            // A new SecureTransformer per parse: the floor is installed on it, and transformers are not reusable across concurrent parses.
            final Transformer transformer = templates.newTransformer();
            // The filter is the listener, so TrAX error reports reach the caller-set ErrorHandler like the parent reader's SAX reports do.
            transformer.setErrorListener(this);
            transformer.transform(new SAXSource(parent, input), result);
        } catch (final TransformerException e) {
            // The parent reader's parse errors and the handler's own exceptions arrive wrapped; rethrow the original rather than nesting the hierarchies.
            final Throwable cause = e.getCause();
            if (cause instanceof SAXException) {
                throw (SAXException) cause;
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new SAXException(e);
        }
    }

    /** Forwards a transformation warning to the caller-set {@link org.xml.sax.ErrorHandler}; the transformation continues unless that handler throws. */
    @Override
    public void warning(final TransformerException e) throws TransformerException {
        try {
            warning(toSAXParseException(e));
        } catch (final SAXException se) {
            throw new TransformerException(se);
        }
    }
}
