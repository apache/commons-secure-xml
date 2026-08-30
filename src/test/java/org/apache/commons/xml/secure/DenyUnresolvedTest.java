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

import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSException;
import org.xml.sax.SAXException;

/**
 * Checks that the resolver floors reject unresolved references when the {@value SecureException#THROW_ON_UNRESOLVED} system property is set.
 *
 * <p>The floors are exercised directly: with the property set and no caller delegate, each must throw its hook's exception instead of resolving to empty
 * content. The property is read at resolution time, so setting it around a single test cannot leak into the rest of the suite.</p>
 */
class DenyUnresolvedTest {

    private static final String SYSTEM_ID = "http://invalid.example.invalid/external.dtd";

    @AfterEach
    void clearThrowOnUnresolved() {
        System.clearProperty(SecureException.THROW_ON_UNRESOLVED);
    }

    @BeforeEach
    void enableThrowOnUnresolved() {
        System.setProperty(SecureException.THROW_ON_UNRESOLVED, "true");
    }

    @Test
    void floorsThrowOnUnresolved() {
        assertThrows(SAXException.class, () -> new FallbackIgnoreEntityResolver2(null).resolveEntity(null, SYSTEM_ID),
                "EntityResolver2 floor should throw on an unresolved entity");
        assertThrows(XMLStreamException.class, () -> new FallbackIgnoreXMLResolver(null).resolveEntity(null, SYSTEM_ID, null, null),
                "XMLResolver floor should throw on an unresolved entity");
        assertThrows(LSException.class, () -> new FallbackIgnoreLSResourceResolver(null).resolveResource(null, null, null, SYSTEM_ID, null),
                "LSResourceResolver floor should throw on an unresolved resource");
        assertThrows(TransformerException.class, () -> new FallbackIgnoreURIResolver(null, null, () -> false).resolve(SYSTEM_ID, null),
                "URIResolver floor should throw on an unresolved URI");
    }
}
