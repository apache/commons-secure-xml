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

import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;

/**
 * {@link Schema} wrapper that secures every {@link Validator} and {@link ValidatorHandler} the inner Schema produces: each {@link Validator} is wrapped in
 * {@link SecureValidator} (which rewrites the Source through {@link SecureSAXParserFactory#secure(javax.xml.transform.Source, boolean)} and installs the resolver
 * floor), and each {@link ValidatorHandler} is wrapped in a {@link SecureValidatorHandler} that keeps the same ignore-all resolver floor so
 * {@code xsi:schemaLocation} is not resolved during SAX-driven validation.
 */
final class SecureSchema extends Schema {

    private final Schema delegate;

    /**
     * Snapshot of the factory's {@value SecureSAXParserFactory#OVERRIDE_DEFAULT_PARSER} outcome, carried onto every produced Validator.
     */
    final boolean overrideDefaultParser;

    /**
     * Constructs a new instance.
     *
     * @param delegate         the delegate to wrap; must not be {@code null}.
     * @param overrideDefaultParser whether the produced Validators' source rewrites should use the pluggable parser lookup instead of the platform's built-in parser.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    SecureSchema(final Schema delegate, final boolean overrideDefaultParser) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.overrideDefaultParser = overrideDefaultParser;
    }

    @Override
    public Validator newValidator() {
        return new SecureValidator(delegate.newValidator(), overrideDefaultParser);
    }

    @Override
    public ValidatorHandler newValidatorHandler() {
        return new SecureValidatorHandler(delegate.newValidatorHandler());
    }
}
