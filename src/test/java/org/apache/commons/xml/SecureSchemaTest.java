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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("schema")
class SecureSchemaTest {

    @Test
    void wrapsAllSchemaProducts() throws Exception {
        final SecureSchema schema = new SecureSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(), false);
        assertInstanceOf(SecureValidator.class, schema.newValidator());
        assertInstanceOf(SecureValidatorHandler.class, schema.newValidatorHandler());
    }
}
