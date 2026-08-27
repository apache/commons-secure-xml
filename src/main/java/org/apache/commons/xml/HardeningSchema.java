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

import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;

/**
 * {@link Schema} wrapper that hardens every {@link Validator} and {@link ValidatorHandler} the inner Schema produces: each {@link Validator} is wrapped in
 * {@link HardeningValidator} (which rewrites the Source through {@link HardeningSAXParserFactory#harden(javax.xml.transform.Source)} and installs the resolver
 * floor), and each {@link ValidatorHandler} is wrapped in a {@link HardeningValidatorHandler} that keeps the same ignore-all resolver floor so
 * {@code xsi:schemaLocation} is not resolved during SAX-driven validation.
 */
final class HardeningSchema extends Schema {

    private final Schema delegate;

    /**
     * Constructs a new instance.
     *
     * @param delegate the delegate to wrap; must not be {@code null}.
     * @throws NullPointerException if {@code delegate} is {@code null}.
     */
    HardeningSchema(final Schema delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Validator newValidator() {
        return new HardeningValidator(delegate.newValidator());
    }

    @Override
    public ValidatorHandler newValidatorHandler() {
        return new HardeningValidatorHandler(delegate.newValidatorHandler());
    }
}
