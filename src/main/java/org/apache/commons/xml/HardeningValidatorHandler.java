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

import javax.xml.validation.TypeInfoProvider;
import javax.xml.validation.ValidatorHandler;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * {@link ValidatorHandler} wrapper that keeps a deny-all {@link LSResourceResolver} floor a caller cannot remove.
 *
 * <p>Blocks {@code xsi:schemaLocation} resolution during SAX-driven validation. A caller-set resolver is routed through a {@link
 * FallbackDenyLSResourceResolver} rather than replacing the floor, so a schema the caller does not resolve is denied instead of fetched.</p>
 */
final class HardeningValidatorHandler extends ValidatorHandler {

    private final ValidatorHandler delegate;

    private final FallbackDenyLSResourceResolver floor = new FallbackDenyLSResourceResolver(null);

    HardeningValidatorHandler(final ValidatorHandler delegate) {
        this.delegate = delegate;
        delegate.setResourceResolver(floor);
    }

    @Override
    public void setResourceResolver(final LSResourceResolver resourceResolver) {
        floor.setDelegate(resourceResolver);
    }

    @Override
    public LSResourceResolver getResourceResolver() {
        return floor.getDelegate();
    }

    // <editor-fold defaultstate="collapsed" desc="Trivial delegation">
    @Override
    public void setContentHandler(final ContentHandler receiver) {
        delegate.setContentHandler(receiver);
    }

    @Override
    public ContentHandler getContentHandler() {
        return delegate.getContentHandler();
    }

    @Override
    public void setErrorHandler(final ErrorHandler errorHandler) {
        delegate.setErrorHandler(errorHandler);
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return delegate.getErrorHandler();
    }

    @Override
    public TypeInfoProvider getTypeInfoProvider() {
        return delegate.getTypeInfoProvider();
    }

    @Override
    public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getProperty(name);
    }

    @Override
    public void setProperty(final String name, final Object object) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setProperty(name, object);
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        delegate.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        delegate.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        delegate.endDocument();
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        delegate.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        delegate.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        delegate.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        delegate.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        delegate.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        delegate.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        delegate.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        delegate.skippedEntity(name);
    }
    // </editor-fold>
}
