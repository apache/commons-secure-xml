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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.validation.Schema;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * {@link DocumentBuilder} wrapper that keeps a deny-all {@link EntityResolver} as a non-overridable floor.
 *
 * <p>A caller-set resolver is sandwiched inside a {@link FallbackDenyEntityResolver2} instead of replacing the deny-all one, so an external lookup the
 * caller's resolver does not satisfy is denied rather than fetched. {@link #reset()} re-establishes the bare deny-all floor, matching the just-constructed
 * state.</p>
 */
final class HardeningDocumentBuilder extends DocumentBuilder {

    private final DocumentBuilder delegate;

    private final FallbackDenyEntityResolver2 floor = new FallbackDenyEntityResolver2(null);

    HardeningDocumentBuilder(final DocumentBuilder delegate) {
        this.delegate = delegate;
        delegate.setEntityResolver(floor);
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        floor.setDelegate(resolver);
    }

    @Override
    public void reset() {
        delegate.reset();
        floor.setDelegate(null);
        delegate.setEntityResolver(floor);
    }

    // <editor-fold defaultstate="collapsed" desc="Trivial delegation">
    @Override
    public Document parse(final InputSource is) throws SAXException, IOException {
        return delegate.parse(is);
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
    public void setErrorHandler(final ErrorHandler eh) {
        delegate.setErrorHandler(eh);
    }

    @Override
    public Document newDocument() {
        return delegate.newDocument();
    }

    @Override
    public DOMImplementation getDOMImplementation() {
        return delegate.getDOMImplementation();
    }

    @Override
    public Schema getSchema() {
        return delegate.getSchema();
    }
    // </editor-fold>
}
