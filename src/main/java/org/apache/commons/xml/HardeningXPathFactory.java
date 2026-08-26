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

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/**
 * {@link XPathFactory} wrapper that returns a {@link HardeningXPath} from {@link #newXPath()}.
 *
 * <p>Required because {@link javax.xml.XMLConstants#FEATURE_SECURE_PROCESSING} on the factory governs only the XPath engine: the stock JDK and Apache Xalan
 * implement the {@link org.xml.sax.InputSource}-taking {@code evaluate} entry points by provisioning an internal document parser the feature does not reach.
 * The wrapper performs that document build itself through a hardened parser instead; see {@link HardeningXPath}.</p>
 */
final class HardeningXPathFactory extends XPathFactory {

    private final XPathFactory delegate;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningXPathFactory(final XPathFactory delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean getFeature(final String name) throws XPathFactoryConfigurationException {
        return delegate.getFeature(name);
    }

    @Override
    public boolean isObjectModelSupported(final String objectModel) {
        return delegate.isObjectModelSupported(objectModel);
    }

    @Override
    public XPath newXPath() {
        final XPath xpath = delegate.newXPath();
        return xpath == null ? null : new HardeningXPath(xpath);
    }

    @Override
    public void setFeature(final String name, final boolean value) throws XPathFactoryConfigurationException {
        delegate.setFeature(name, value);
    }

    @Override
    public void setXPathFunctionResolver(final XPathFunctionResolver resolver) {
        delegate.setXPathFunctionResolver(resolver);
    }

    @Override
    public void setXPathVariableResolver(final XPathVariableResolver resolver) {
        delegate.setXPathVariableResolver(resolver);
    }
}
