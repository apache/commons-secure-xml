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
 * Checks whether the XSLT {@code document()} function is resolved at transform time. Compilation succeeds (the function call is just XPath); the leak vector
 * fires only when {@code Transformer.transform} is called and the function evaluates its URI argument.
 */
@Tag("trax")
class TransformerDocumentTest {

    @Test
    void secureTransformerBlocks() {
        AttackTestSupport.assertTemplatesDoesNotLeak(AttackTestSupport.resourceSource("with-document.xsl"));
    }
}
