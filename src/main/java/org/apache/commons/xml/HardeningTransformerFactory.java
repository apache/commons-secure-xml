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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;

/**
 * {@link javax.xml.transform.TransformerFactory} wrapper that rewrites every Source-taking entry point through {@link SAXParserHardener#hardenSource(Source)} before
 * delegating.
 *
 * <p>Used by providers whose underlying TrAX implementation pulls a fresh {@code SAXParserFactory.newInstance()} for any Source that is not already a
 * {@link SAXSource} carrying its own {@link XMLReader}, and only sets {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING FSP} on the resulting reader.
 * Wrapping the factory and rewriting the Source upstream guarantees the parse runs through an {@link XmlFactories}-hardened reader instead.</p>
 *
 * <p>Three layers cooperate:</p>
 * <ol>
 *   <li>{@link HardeningTransformerFactory} rewrites the Source on every entry point that compiles a stylesheet or transforms a one-shot input.</li>
 *   <li>{@link HardeningTemplates} returns a {@link HardeningTransformer} from {@link Templates#newTransformer()} so runtime source parsing is also covered, and
 *       restores the factory's URIResolver onto the produced Transformer (which the underlying impl typically does not propagate through {@code Templates}).</li>
 *   <li>{@link HardeningTransformer} rewrites the Source on every {@link Transformer#transform(Source, javax.xml.transform.Result)} call.</li>
 * </ol>
 *
 * <h2>Caveats</h2>
 * <ul>
 *   <li>A {@link SAXSource} that carries its own {@link XMLReader} is trusted as-is: the caller is expected to supply a hardened reader (via
 *       {@link XmlFactories#newSAXParserFactory()}) in that case.</li>
 *   <li>{@link TransformerHandler} returned from {@code newTransformerHandler} is not wrapped: it processes incoming SAX events instead of reading a Source, so
 *       it has no inner Source-parsing path. A caller who pulls the inner {@link Transformer} via {@link TransformerHandler#getTransformer()} bypasses the
 *       runtime source rewrite.</li>
 * </ul>
 */
final class HardeningTransformerFactory extends SAXTransformerFactory {

    private final SAXTransformerFactory delegate;

    private final FallbackIgnoreURIResolver floor = new FallbackIgnoreURIResolver(null);

    HardeningTransformerFactory(final SAXTransformerFactory delegate) {
        this.delegate = delegate;
        // Compile-time block for xsl:import/xsl:include and document(); a caller-set resolver is routed through the floor rather than replacing it.
        delegate.setURIResolver(floor);
    }

    @Override
    public void setURIResolver(final URIResolver resolver) {
        floor.setDelegate(resolver);
    }

    @Override
    public URIResolver getURIResolver() {
        return floor.getDelegate();
    }

    @Override
    public Source getAssociatedStylesheet(final Source source, final String media, final String title, final String charset)
            throws TransformerConfigurationException {
        // Xalan's getAssociatedStylesheet drops a SAXSource's reader and self-provisions its own to scan for xml-stylesheet PIs (XALANJ-2849).
        final Source hardened = isXalan(delegate) ? hardenSourceToDom(source) : SAXParserHardener.hardenSource(source);
        return delegate.getAssociatedStylesheet(hardened, media, title, charset);
    }

    /**
     * Whether the delegate is Apache Xalan (either its interpretive or its XSLTC factory), whose {@code getAssociatedStylesheet} ignores a SAXSource reader.
     *
     * @param factory The delegate factory.
     * @return Whether the delegate is an {@code org.apache.xalan.} implementation.
     */
    private static boolean isXalan(final SAXTransformerFactory factory) {
        return factory.getClass().getName().startsWith("org.apache.xalan.");
    }

    /**
     * Parses a reader-less source into a DOM through a hardened, namespace-aware {@link javax.xml.parsers.DocumentBuilder} and returns a {@link DOMSource}
     * carrying its system id, so the consumer walks the tree instead of provisioning its own reader. Any other source is left to
     * {@link SAXParserHardener#hardenSource(Source)}.
     *
     * @param source The source to scan for an associated stylesheet.
     * @return A {@link DOMSource} for a reader-less source, otherwise the result of {@link SAXParserHardener#hardenSource(Source)}.
     * @throws TransformerConfigurationException if the source cannot be parsed.
     */
    private static Source hardenSourceToDom(final Source source) throws TransformerConfigurationException {
        if (source instanceof StreamSource || source instanceof SAXSource && ((SAXSource) source).getXMLReader() == null) {
            final InputSource inputSource = SAXSource.sourceToInputSource(source);
            if (inputSource != null) {
                try {
                    final DocumentBuilderFactory factory = DocumentBuilderHardener.harden(DocumentBuilderFactory.newInstance());
                    factory.setNamespaceAware(true);
                    final Document document = factory.newDocumentBuilder().parse(inputSource);
                    return new DOMSource(document, inputSource.getSystemId());
                } catch (final ParserConfigurationException | SAXException | IOException e) {
                    throw new TransformerConfigurationException("Failed to parse the source for associated-stylesheet lookup", e);
                }
            }
        }
        return SAXParserHardener.hardenSource(source);
    }

    @Override
    public Templates newTemplates(final Source source) throws TransformerConfigurationException {
        final Templates templates = delegate.newTemplates(SAXParserHardener.hardenSource(source));
        return templates == null ? null : new HardeningTemplates(templates, getURIResolver());
    }

    @Override
    public Transformer newTransformer() throws TransformerConfigurationException {
        // Identity transformer: still parses runtime sources, so wrap it to harden Transformer.transform(Source, Result).
        final Transformer transformer = delegate.newTransformer();
        return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver());
    }

    @Override
    public Transformer newTransformer(final Source source) throws TransformerConfigurationException {
        final Transformer transformer = delegate.newTransformer(SAXParserHardener.hardenSource(source));
        return transformer == null ? null : new HardeningTransformer(transformer, getURIResolver());
    }

    @Override
    public TransformerHandler newTransformerHandler(final Source source) throws TransformerConfigurationException {
        return delegate.newTransformerHandler(SAXParserHardener.hardenSource(source));
    }

    // <editor-fold defaultstate="collapsed" desc="Trivial delegation">
    @Override
    public Object getAttribute(final String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public ErrorListener getErrorListener() {
        return delegate.getErrorListener();
    }

    @Override
    public boolean getFeature(final String name) {
        return delegate.getFeature(name);
    }

    @Override
    public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
        return delegate.newTemplatesHandler();
    }

    @Override
    public TransformerHandler newTransformerHandler() throws TransformerConfigurationException {
        return delegate.newTransformerHandler();
    }

    @Override
    public TransformerHandler newTransformerHandler(final Templates templates) throws TransformerConfigurationException {
        return delegate.newTransformerHandler(templates);
    }

    @Override
    public XMLFilter newXMLFilter(final Source source) throws TransformerConfigurationException {
        return delegate.newXMLFilter(source);
    }

    @Override
    public XMLFilter newXMLFilter(final Templates templates) throws TransformerConfigurationException {
        return delegate.newXMLFilter(templates);
    }

    @Override
    public void setAttribute(final String name, final Object value) {
        delegate.setAttribute(name, value);
    }

    @Override
    public void setErrorListener(final ErrorListener listener) {
        delegate.setErrorListener(listener);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws TransformerConfigurationException {
        delegate.setFeature(name, value);
    }
    // </editor-fold>
}
