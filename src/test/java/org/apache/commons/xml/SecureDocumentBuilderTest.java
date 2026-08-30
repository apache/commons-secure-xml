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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class SecureDocumentBuilderTest {

    @Test
    void createsDocument() throws Exception {
        assertNotNull(new SecureDocumentBuilder(DocumentBuilderFactory.newInstance().newDocumentBuilder()).newDocument());
    }

    @Test
    void forwardsDocumentBuilderStateAndDomImplementation() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setSchema(null);
        final SecureDocumentBuilder builder = new SecureDocumentBuilder(factory.newDocumentBuilder());
        assertTrue(builder.isNamespaceAware());
        assertFalse(builder.isValidating());
        assertFalse(builder.isXIncludeAware());
        assertNull(builder.getSchema());
        assertNotNull(builder.getDOMImplementation());
    }
}
