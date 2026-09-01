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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests whether {@code xs:include schemaLocation="included.xsd"} of a sibling schema sharing the wrapper's target namespace is resolved at compile time. The
 * wrapper references an element defined only in the included schema, so compilation cannot succeed unless the schemaLocation is fetched.
 */
@Tag("schema")
class SchemaIncludeTest {

    private static final String RESOURCE = "with-include.xsd";

    @Test
    void secureSchemaBlocks() {
        AttackTestSupport.assertSchemaBlocks(AttackTestSupport.resourceSource(RESOURCE));
    }

    @Test
    void unconfiguredSchemaCompiles() {
        AttackTestSupport.assertPermissiveSchemaCompiles(AttackTestSupport.resourceSource(RESOURCE));
    }
}
