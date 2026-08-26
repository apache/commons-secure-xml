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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;

import org.xml.sax.EntityResolver;

/**
 * {@link DocumentBuilderFactory} wrapper that keeps an ignore-all {@link EntityResolver} floor on every {@link DocumentBuilder} produced.
 *
 * <p>Wraps each produced builder in a {@link HardeningDocumentBuilder}; required when the underlying factory carries no resolver of its own and does not honor
 * JAXP 1.5 {@code ACCESS_EXTERNAL_*} (e.g. the external Xerces distribution). A caller-set resolver is routed through the floor rather than replacing it. Kept
 * as a standalone wrapper so any hardener can reuse the floor.</p>
 */
final class HardeningDocumentBuilderFactory extends DocumentBuilderFactory {

    private final DocumentBuilderFactory delegate;

    HardeningDocumentBuilderFactory(final DocumentBuilderFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object getAttribute(final String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public boolean getFeature(final String name) throws ParserConfigurationException {
        return delegate.getFeature(name);
    }

    @Override
    public Schema getSchema() {
        return delegate.getSchema();
    }

    @Override
    public boolean isCoalescing() {
        return delegate.isCoalescing();
    }

    @Override
    public boolean isExpandEntityReferences() {
        return delegate.isExpandEntityReferences();
    }

    @Override
    public boolean isIgnoringComments() {
        return delegate.isIgnoringComments();
    }

    @Override
    public boolean isIgnoringElementContentWhitespace() {
        return delegate.isIgnoringElementContentWhitespace();
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
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return new HardeningDocumentBuilder(delegate.newDocumentBuilder());
    }

    @Override
    public void setAttribute(final String name, final Object value) {
        delegate.setAttribute(name, value);
    }

    @Override
    public void setCoalescing(final boolean coalescing) {
        delegate.setCoalescing(coalescing);
    }

    @Override
    public void setExpandEntityReferences(final boolean expandEntityRef) {
        delegate.setExpandEntityReferences(expandEntityRef);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws ParserConfigurationException {
        delegate.setFeature(name, value);
    }

    @Override
    public void setIgnoringComments(final boolean ignoreComments) {
        delegate.setIgnoringComments(ignoreComments);
    }

    @Override
    public void setIgnoringElementContentWhitespace(final boolean whitespace) {
        delegate.setIgnoringElementContentWhitespace(whitespace);
    }

    @Override
    public void setNamespaceAware(final boolean awareness) {
        delegate.setNamespaceAware(awareness);
    }

    @Override
    public void setSchema(final Schema schema) {
        delegate.setSchema(schema);
    }

    @Override
    public void setValidating(final boolean validating) {
        delegate.setValidating(validating);
    }

    @Override
    public void setXIncludeAware(final boolean state) {
        delegate.setXIncludeAware(state);
    }

}
