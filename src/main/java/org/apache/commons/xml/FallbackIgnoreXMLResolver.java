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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

/**
 * {@link FallbackDenyXMLResolver} variant whose unresolved policy returns an empty input instead of throwing, so the parse continues with no replacement
 * content. Used on Woodstox's DTD-subset and undeclared-entity hooks (where a missing resource must be skipped, not denied), while still consulting an
 * optional caller-supplied resolver first.
 */
class FallbackIgnoreXMLResolver extends FallbackDenyXMLResolver {

    /**
     * Empty {@link ByteArrayInputStream} shared across every call. {@code read()} on a zero-length array always returns {@code -1}, so reusing the instance
     * is safe even though the type is technically stateful.
     */
    private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);

    FallbackIgnoreXMLResolver(final XMLResolver delegate) {
        super(delegate);
    }

    @Override
    protected Object onUnresolved(final String publicID, final String systemID, final String baseURI, final String namespace) throws XMLStreamException {
        return EMPTY;
    }
}
