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

import java.util.Properties;
import java.util.function.Supplier;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;

/**
 * {@link Templates} wrapper whose only purpose is to return a {@link HardeningTransformer} from {@link Templates#newTransformer()}, with the factory's
 * compile-time {@link URIResolver} pre-installed.
 *
 * <p>Both Apache Xalan 2.7 and stock-JDK XSLTC fail to propagate the factory's URIResolver through {@code Templates.newTransformer()}: the produced runtime
 * Transformer has a null URIResolver unless the caller sets one, leaving runtime {@code document()} calls unguarded. Snapshotting the resolver at compile time
 * and restoring it onto the runtime Transformer matches the JAXP-conformant intuition that the factory's resolver is the default for any Transformer the
 * factory ultimately produces.</p>
 */
final class HardeningTemplates implements Templates {

    private final Templates delegate;

    /** Compile-time URIResolver snapshot; the underlying impl does not propagate the factory's resolver onto Transformers obtained from Templates. */
    private final URIResolver uriResolver;

    /** Empty-{@link Source} supplier for the produced Transformer's floor; {@code null} means the default empty DOM. */
    private final Supplier<Source> emptySource;

    HardeningTemplates(final Templates delegate, final URIResolver uriResolver, final Supplier<Source> emptySource) {
        this.delegate = delegate;
        this.uriResolver = uriResolver;
        this.emptySource = emptySource;
    }

    /**
     * The wrapped implementation Templates, for factory methods whose implementations cast {@code newTransformer()} to their own type.
     *
     * @return the wrapped {@link Templates} implementation.
     */
    Templates getDelegate() {
        return delegate;
    }

    @Override
    public Properties getOutputProperties() {
        return delegate.getOutputProperties();
    }


    @Override
    public Transformer newTransformer() throws TransformerConfigurationException {
        final Transformer transformer = delegate.newTransformer();
        if (transformer == null) {
            return null;
        }
        return new HardeningTransformer(transformer, uriResolver, emptySource);
    }
}
