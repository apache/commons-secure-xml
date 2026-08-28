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
import java.util.function.Supplier;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * {@link TransformerHandler} wrapper that keeps an ignore-all {@link URIResolver} floor on the live transformer the handler transforms with.
 *
 * <p>The handler's input is SAX events the caller drives, so it has no inner source-parsing path of its own. What needs the floor is its transformer: the
 * handler runs the transformation on the object {@link TransformerHandler#getTransformer()} exposes, and not every implementation seeds that transformer with
 * the factory's resolver (the stock JDK's {@code newTransformerHandler(Templates)} does not). Wrapping that transformer in a {@link SecureTransformer} at
 * construction installs the floor on the live instance, so runtime {@code document()} during the handler's transform is covered, and so is a caller who pulls
 * the transformer out through {@code getTransformer()}.</p>
 */
final class SecureTransformerHandler implements TransformerHandler {

    private final TransformerHandler delegate;

    /**
     * Wraps the handler's LIVE transformer; constructing it installs the resolver floor that the handler's own transform then runs under.
     */
    private final SecureTransformer transformer;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @param uriResolver the compile-time URIResolver snapshot to restore onto the live transformer; may be {@code null}.
     * @param emptySource the empty-{@link Source} supplier for the produced Transformer's floor; {@code null} means the default empty DOM.
     * @param overrideDefaultParser whether the live transformer's source rewrites should use the pluggable parser lookup instead of the platform's built-in parser.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    SecureTransformerHandler(final TransformerHandler delegate, final URIResolver uriResolver, final Supplier<Source> emptySource,
            final boolean overrideDefaultParser) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.transformer = new SecureTransformer(delegate.getTransformer(), uriResolver, emptySource, overrideDefaultParser);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        delegate.characters(ch, start, length);
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        delegate.comment(ch, start, length);
    }


    @Override
    public void endCDATA() throws SAXException {
        delegate.endCDATA();
    }

    @Override
    public void endDocument() throws SAXException {
        delegate.endDocument();
    }

    @Override
    public void endDTD() throws SAXException {
        delegate.endDTD();
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        delegate.endElement(uri, localName, qName);
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        delegate.endEntity(name);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        delegate.endPrefixMapping(prefix);
    }

    @Override
    public String getSystemId() {
        return delegate.getSystemId();
    }

    @Override
    public Transformer getTransformer() {
        return transformer;
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        delegate.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void notationDecl(final String name, final String publicId, final String systemId) throws SAXException {
        delegate.notationDecl(name, publicId, systemId);
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        delegate.processingInstruction(target, data);
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        delegate.setDocumentLocator(locator);
    }

    @Override
    public void setResult(final Result result) {
        delegate.setResult(result);
    }

    @Override
    public void setSystemId(final String systemID) {
        delegate.setSystemId(systemID);
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        delegate.skippedEntity(name);
    }

    @Override
    public void startCDATA() throws SAXException {
        delegate.startCDATA();
    }

    @Override
    public void startDocument() throws SAXException {
        delegate.startDocument();
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        delegate.startDTD(name, publicId, systemId);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        delegate.startElement(uri, localName, qName, atts);
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        delegate.startEntity(name);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        delegate.startPrefixMapping(prefix, uri);
    }

    @Override
    public void unparsedEntityDecl(final String name, final String publicId, final String systemId, final String notationName) throws SAXException {
        delegate.unparsedEntityDecl(name, publicId, systemId, notationName);
    }
}
