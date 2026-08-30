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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.xml.validation.Schema;

/**
 * Checks that {@link SecureSchemaFactory#newInstance(String)} passes the schema language through to {@link SchemaFactory#newInstance}.
 *
 * <p>The working W3C XML Schema path is exercised by the whole schema suite; this test covers only the language-selection contract.</p>
 */
@Tag("schema")
class SchemaFactoryLanguageTest {

    @Test
    void unknownSchemaLanguageThrows() {
        assertThrows(IllegalArgumentException.class, () -> SecureSchemaFactory.newInstance("urn:example:unknown-schema-language"),
                "an unsupported schema language should surface SchemaFactory.newInstance's IllegalArgumentException");
    }
}
