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

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * {@link XMLReader} wrapper that keeps a {@link FallbackIgnoreEntityResolver2} floor as the reader's entity resolver, non-overridable by the caller.
 *
 * <p>The floor is installed once and stays the reader's entity resolver for the wrapper's lifetime; {@link #setEntityResolver(EntityResolver)} routes the
 * caller's resolver through {@link FallbackIgnoreEntityResolver2#setDelegate} instead of replacing it. This includes the {@code DefaultHandler} that
 * {@link javax.xml.parsers.SAXParser#parse(org.xml.sax.InputSource, org.xml.sax.helpers.DefaultHandler) SAXParser.parse(source, handler)} installs as the
 * reader's entity resolver, which would otherwise silently replace the floor. {@link #getEntityResolver()} reports the caller's resolver unwrapped.</p>
 *
 * <p>Every other method forwards to the wrapped delegate; subclasses (e.g. {@code HardeningExpatXMLReader}) add per-implementation fixups on top of the floor.</p>
 */
class HardeningXMLReader implements XMLReader {

    private final XMLReader delegate;

    private final FallbackIgnoreEntityResolver2 floor;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningXMLReader(final XMLReader delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.floor = new FallbackIgnoreEntityResolver2(null);
        delegate.setEntityResolver(floor);
    }

    @Override
    public ContentHandler getContentHandler() {
        return delegate.getContentHandler();
    }

    @Override
    public DTDHandler getDTDHandler() {
        return delegate.getDTDHandler();
    }

    @Override
    public EntityResolver getEntityResolver() {
        return floor.getDelegate();
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return delegate.getErrorHandler();
    }

    @Override
    public boolean getFeature(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    @Override
    public Object getProperty(final String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return delegate.getProperty(name);
    }

    @Override
    public void parse(final InputSource input) throws IOException, SAXException {
        delegate.parse(input);
    }

    @Override
    public void parse(final String systemId) throws IOException, SAXException {
        delegate.parse(systemId);
    }

    @Override
    public void setContentHandler(final ContentHandler handler) {
        delegate.setContentHandler(handler);
    }

    @Override
    public void setDTDHandler(final DTDHandler handler) {
        delegate.setDTDHandler(handler);
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        floor.setDelegate(resolver);
    }

    @Override
    public void setErrorHandler(final ErrorHandler handler) {
        delegate.setErrorHandler(handler);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public void setProperty(final String name, final Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setProperty(name, value);
    }

}
