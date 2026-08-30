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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks that {@code getAssociatedStylesheet} scans for {@code xml-stylesheet} PIs without fetching an external DTD declared in the document prolog.
 *
 * <p>The PI scan parses the prolog, where a {@code DOCTYPE} with an external subset is processed before the root element. On Apache Xalan the scan runs on a
 * reader the engine provisions itself, ignoring a secure reader passed in a {@link SAXSource} (XALANJ-2849); the wrapper works around
 * that by handing Xalan a {@code DOMSource} it pre-parsed through a secure {@code DocumentBuilder}. The JDK's XSLTC honors the secure reader directly. Either
 * way the external DTD resolves to empty instead of being fetched. Tagged {@code trax}, so it runs on the stock JDK, Apache Xalan, Saxon, and the Android
 * runtime.</p>
 */
@Tag("trax")
class AssociatedStylesheetTest {

    /** The PI was found (non-null); where the engine exposes a system id, it points at the declared stylesheet. */
    private static void assertAssociatedStylesheet(final Source associated) {
        assertNotNull(associated, "expected the associated stylesheet PI to be found");
        if (associated.getSystemId() != null) {
            assertTrue(associated.getSystemId().endsWith("included.xsl"), "unexpected associated stylesheet: " + associated.getSystemId());
        }
    }

    private static TransformerFactory secureFactory() {
        final TransformerFactory factory = SecureTransformerFactory.newInstance();
        factory.setErrorListener(AttackTestSupport.STRICT_REPORTER);
        return factory;
    }

    @Test
    void secureGetAssociatedStylesheetIgnoresExternalDtd() throws TransformerConfigurationException {
        // The prolog declares an unreachable external DTD; the secure parse resolves it to empty rather than fetching it, so the PI scan completes and finds
        // the stylesheet instead of throwing on a fetch. (The returned Source's shape is engine-specific: XSLTC and Xalan point it at included.xsl, while Saxon
        // resolves the href through its own floor and returns an empty source; both mean the scan ran without fetching the DTD.)
        final Source associated = secureFactory()
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet.xml"), null, null, null);
        assertAssociatedStylesheet(associated);
    }

    @Test
    void secureGetAssociatedStylesheetReturnsStylesheet() throws TransformerConfigurationException {
        // Positive control: a plain document with no DOCTYPE resolves its xml-stylesheet PI end to end.
        final Source associated = secureFactory()
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet-plain.xml"), null, null, null);
        assertAssociatedStylesheet(associated);
    }

    @Test
    void unconfiguredGetAssociatedStylesheetFetchesExternalDtd() {
        // Leak/discrimination control: the unconfigured engine attempts to fetch the unreachable external DTD and fails. Android's KXmlParser does not fetch
        // external DTDs, so it has nothing to demonstrate here.
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Android's KXmlParser does not fetch external DTDs");
        assertThrows(TransformerConfigurationException.class, () -> TransformerFactory.newInstance()
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet.xml"), null, null, null));
    }
}
