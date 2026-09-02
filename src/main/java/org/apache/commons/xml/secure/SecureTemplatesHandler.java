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

import java.util.Objects;
import java.util.function.Supplier;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.TemplatesHandler;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * {@link TemplatesHandler} wrapper whose only purpose is to return a {@link SecureTemplates} from {@link TemplatesHandler#getTemplates()}.
 *
 * <p>The handler itself only compiles: the caller drives the stylesheet's SAX events, and {@code xsl:include}/{@code xsl:import} hrefs already resolve through
 * the delegate factory's resolver, which is the secure floor. What the raw handler lacks is the runtime side: the {@link Templates} it compiles produce
 * Transformers without a {@link URIResolver} floor. Wrapping {@code getTemplates()} closes that, exactly as
 * {@link javax.xml.transform.TransformerFactory#newTemplates newTemplates} does.</p>
 */
final class SecureTemplatesHandler implements TemplatesHandler {

    private final TemplatesHandler delegate;

    /**
     * Compile-time URIResolver snapshot, restored onto Transformers produced from the compiled Templates.
     */
    private final URIResolver uriResolver;

    /**
     * Empty-{@link Source} supplier for the produced Templates' floor; {@code null} means the default empty DOM.
     */
    private final Supplier<Source> emptySource;

    /**
     * Snapshot of the factory's {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} outcome, carried onto the produced Templates.
     */
    private final boolean overrideDefaultParser;

    /**
     * Constructs a new instance.
     *
     * @param delegate The delegate to wrap; must not be {@code null}.
     * @param uriResolver The compile-time URIResolver snapshot to restore onto Transformers produced from the compiled Templates; may be {@code null}.
     * @param emptySource The empty-{@link Source} supplier for the produced Templates; may be {@code null} for the default empty DOM document.
     * @param overrideDefaultParser whether the produced Templates' source rewrites should use the pluggable parser lookup instead of the platform's built-in parser.
     * @throws NullPointerException Thrown if {@code delegate} is {@code null}.
     */
    SecureTemplatesHandler(final TemplatesHandler delegate, final URIResolver uriResolver, final Supplier<Source> emptySource,
            final boolean overrideDefaultParser) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.uriResolver = uriResolver;
        this.emptySource = emptySource;
        this.overrideDefaultParser = overrideDefaultParser;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        delegate.characters(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        delegate.endDocument();
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        delegate.endElement(uri, localName, qName);
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
    public Templates getTemplates() {
        // Null before the stylesheet's endDocument (and on a failed compile in some implementations).
        final Templates templates = delegate.getTemplates();
        return templates == null ? null : new SecureTemplates(templates, uriResolver, emptySource, overrideDefaultParser);
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
    public void setDocumentLocator(final Locator locator) {
        delegate.setDocumentLocator(locator);
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
    public void startDocument() throws SAXException {
        delegate.startDocument();
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        delegate.startElement(uri, localName, qName, atts);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        delegate.startPrefixMapping(prefix, uri);
    }
}
