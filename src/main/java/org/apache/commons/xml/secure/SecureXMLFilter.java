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
 * {@link XMLFilter} that transforms the parsed input through a {@link SecureTransformer} and emits the result as SAX events.
 *
 * <p>
 * Composed from the library's own wrappers instead of delegating to the implementation's filter, because the implementation filters self-provision an
 * unsecured reader for the input (the stock JDK's does so as early as {@code setContentHandler}) and cast a supplied {@link javax.xml.transform.Templates} to
 * their own type, which a wrapped Templates is not. Here the input is parsed by the parent reader, a secure one installed on first {@code parse} when the
 * caller has not set a parent (a caller-set parent is trusted configuration, used as-is), and the transformation runs on a {@link SecureTransformer}, so
 * runtime {@code document()} sits on the resolver floor. The filter is also the transformer's {@link ErrorListener}, forwarding TrAX error reports to the
 * caller-set {@link org.xml.sax.ErrorHandler} the way the parent reader's SAX reports are.
 * </p>
 *
 * <p>
 * Every parse runs on the one {@link SecureTransformer} the filter is constructed with, the way every stock TrAX filter is built from a single
 * Transformer. The filter is therefore reusable for successive parses and inherits that transformer's reuse contract: one parse at a time, not two threads at
 * once.
 * </p>
 */
final class SecureXMLFilter extends XMLFilterImpl implements ErrorListener {

    /**
     * System ID of the {@link InputSource} substituted for a caller's {@code null} one in {@link #parse(InputSource)}; a URN, so nothing can fetch it.
     */
    static final String NO_INPUT_SYSTEM_ID = "urn:uuid:a47f732e-9111-42db-b648-5e24b7d663f3";

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

    /**
     * Snapshot of the transformer's {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} outcome, carried onto the self-provisioned parent reader.
     */
    private final boolean overrideDefaultParser;

    /**
     * Where the transformation writes, rebuilt whenever the caller sets a ContentHandler; {@code null} until one is set.
     */
    private SAXResult result;

    private final Transformer transformer;

    /**
     * Constructs a new instance.
     *
     * @param transformer The transformer every parse runs on; must not be {@code null}.
     * @throws NullPointerException Thrown if {@code transformer} is {@code null}.
     */
    SecureXMLFilter(final SecureTransformer transformer) {
        this.transformer = Objects.requireNonNull(transformer, "transformer");
        this.overrideDefaultParser = transformer.overrideDefaultParser;
        // The filter is the listener, so TrAX error reports reach the caller-set ErrorHandler like the parent reader's SAX reports do.
        transformer.setErrorListener(this);
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
     * Gets the Transformer this filter drives.
     *
     * @return the filter's {@link Transformer}, never {@code null}.
     */
    Transformer getTransformer() {
        return transformer;
    }

    /**
     * {@inheritDoc}
     *
     * @throws FactoryConfigurationError Thrown from a factory in case of a {@link java.util.ServiceConfigurationError service
     *                                   configuration error} or if the implementation is not available or cannot be instantiated.
     */
    @Override
    public void parse(final InputSource input) throws SAXException, IOException {
        if (result == null) {
            throw new SAXException("No ContentHandler set on the XMLFilter to receive the transformation result");
        }
        if (getParent() == null) {
            setParent(SecureSAXParserFactory.newXMLReader(overrideDefaultParser));
        }
        try {
            // A self-driven parent needs no InputSource, so a caller may pass null here; most TrAX implementations dereference the one they get unchecked.
            // See: https://issues.apache.org/jira/browse/XALANJ-2851
            transformer.transform(new SAXSource(getParent(), input != null ? input : new InputSource(NO_INPUT_SYSTEM_ID)), result);
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

    /**
     * Sets the content handler that receives the transformation result.
     *
     * <p>
     * Builds the destination the transformation writes to, so a parse only has to run it. A handler that is also a {@link LexicalHandler} receives the
     * result's comments and CDATA boundaries too, the way {@link javax.xml.transform.sax.SAXResult} expects them to be supplied.
     * </p>
     */
    @Override
    public void setContentHandler(final ContentHandler handler) {
        super.setContentHandler(handler);
        result = handler == null ? null : new SAXResult(handler);
        if (handler instanceof LexicalHandler) {
            result.setLexicalHandler((LexicalHandler) handler);
        }
    }

    /**
     * Sets the parent reader that supplies the input to the transformation.
     *
     * <p>
     * Wires the filter onto the new parent the way {@link XMLFilterImpl#setupParse()} would, minus the ContentHandler: the transformer owns the parent's
     * content events and delivers the transformed stream to the caller's handler through a {@link SAXResult} instead. Wiring the parent here rather than per
     * parse is enough because it is the filter that is installed, not the caller's callbacks, so a callback the caller sets afterward is still reached.
     * </p>
     */
    @Override
    public void setParent(final XMLReader parent) {
        super.setParent(parent);
        // XMLFilterImpl tolerates a null parent, so do not wire one.
        if (parent != null) {
            parent.setEntityResolver(this);
            parent.setDTDHandler(this);
            parent.setErrorHandler(this);
        }
    }

    /**
     * Always throws {@link SAXException} because pushed events would reach the caller's handler untransformed.
     *
     * <p>
     * The stock filters make that role inert too, by dropping the events (Apache Xalan, the JDK) or by not implementing it at all (Saxon).
     * </p>
     *
     * @throws SAXException Thrown on every invocation.
     */
    @Override
    public void startDocument() throws SAXException {
        throw new SAXException("This XMLFilter only implements ContentHandler for technical reasons. To push SAX events, use newTransformerHandler instead.");
    }

    /**
     * Forwards a transformation warning to the caller-set {@link org.xml.sax.ErrorHandler}; the transformation continues unless that handler throws.
     */
    @Override
    public void warning(final TransformerException e) throws TransformerException {
        try {
            warning(toSAXParseException(e));
        } catch (final SAXException se) {
            throw new TransformerException(se);
        }
    }
}
