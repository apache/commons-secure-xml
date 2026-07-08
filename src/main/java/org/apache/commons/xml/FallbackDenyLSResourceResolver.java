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

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * {@link LSResourceResolver} floor: consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
 *
 * <p>The schema-compile counterpart of {@link FallbackDenyEntityResolver2}. The hardened {@link javax.xml.validation.SchemaFactory}, {@link
 * javax.xml.validation.Validator} and {@link javax.xml.validation.ValidatorHandler} wrappers install one of these and route a caller-set resolver through
 * {@link #setDelegate} rather than letting it replace the floor. A caller opts a specific resource in by returning a non-{@code null} {@link LSInput};
 * anything left unresolved is denied.</p>
 */
final class FallbackDenyLSResourceResolver implements LSResourceResolver {

    private LSResourceResolver delegate;

    FallbackDenyLSResourceResolver(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }

    void setDelegate(final LSResourceResolver delegate) {
        this.delegate = delegate;
    }

    LSResourceResolver getDelegate() {
        return delegate;
    }

    @Override
    public LSInput resolveResource(final String type, final String namespaceURI, final String publicId, final String systemId, final String baseURI) {
        final LSInput resolved = delegate != null ? delegate.resolveResource(type, namespaceURI, publicId, systemId, baseURI) : null;
        if (resolved != null) {
            return resolved;
        }
        throw new SecurityException(HardeningException.forbidden(type, namespaceURI, publicId, systemId, baseURI));
    }
}
