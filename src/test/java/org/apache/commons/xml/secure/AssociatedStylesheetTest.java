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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

/**
 * Checks the two untrusted inputs {@code getAssociatedStylesheet} handles: the document it scans, and the href the {@code xml-stylesheet} PI names.
 *
 * <p>The scan parses the prolog, where a {@code DOCTYPE} with an external subset is processed before the root element. Apache Xalan runs it on a reader the
 * engine provisions itself, ignoring one passed in a {@link SAXSource} (XALANJ-2849), and the JDK's XSLTC did the same before 8u162; the wrapper hands those a
 * {@code DOMSource} it pre-parsed through a secure {@code DocumentBuilder}, so the external DTD resolves to empty instead of being fetched.</p>
 *
 * <p>The href is attacker-controlled content, so the wrapper routes it through the same floor as any other content-named reference: unresolved by default,
 * fetched only where a caller's {@code URIResolver} opts it in. Compiling the returned Source is the one documented use of this method, so returning it live
 * would be handing back a URI the document chose. Tagged {@code trax}, so it runs on the stock JDK, Apache Xalan, Saxon, and the Android runtime.</p>
 */
@Tag("trax")
class AssociatedStylesheetTest {

    private static TransformerFactory secureFactory() {
        final TransformerFactory factory = SecureTransformerFactory.newInstance();
        factory.setErrorListener(AttackTestSupport.STRICT_REPORTER);
        return factory;
    }

    @Test
    void secureGetAssociatedStylesheetIgnoresExternalDtd() throws TransformerConfigurationException {
        // The prolog declares an unreachable external DTD; the secure scan resolves it to empty rather than fetching it, so the lookup completes instead of
        // throwing. The PI is found, and its href is floored, so what comes back names no URI.
        final Source associated = secureFactory()
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet.xml"), null, null, null);
        assertNotNull(associated, "expected the associated stylesheet PI to be found");
        assertNull(associated.getSystemId(), "the PI href must not come back as a live URI: " + associated.getSystemId());
    }

    @Test
    void secureGetAssociatedStylesheetIgnoresExternalDtdWithCallerReader() throws Exception {
        // Same scan through a SAXSource carrying a caller-supplied secure reader. Xalan and Java 8 XSLTC drop that reader, so this shape has to be pre-parsed
        // like the reader-less one rather than passed through.
        final SAXSource source = new SAXSource(SecureSAXParserFactory.newInstance().newSAXParser().getXMLReader(),
                new InputSource(AttackTestSupport.resourceUrl("associated-stylesheet.xml").toString()));
        final Source associated = secureFactory().getAssociatedStylesheet(source, null, null, null);
        assertNotNull(associated, "expected the associated stylesheet PI to be found");
        assertNull(associated.getSystemId(), "the PI href must not come back as a live URI: " + associated.getSystemId());
    }

    @Test
    void secureGetAssociatedStylesheetOptsInThroughResolver() throws TransformerConfigurationException {
        // A caller resolver is consulted for the href exactly as for any other reference. What comes back names the opted-in stylesheet rather than nothing,
        // which is what separates an opt-in from the floored default; the floor still re-parses it through a secure reader, so the shape is its own.
        final StreamSource opted = new StreamSource(AttackTestSupport.resourceUrl("included.xsl").toString());
        final TransformerFactory factory = secureFactory();
        factory.setURIResolver((href, base) -> href != null && href.endsWith("included.xsl") ? opted : null);
        final Source associated = factory
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet-plain.xml"), null, null, null);
        assertNotNull(associated, "expected the associated stylesheet PI to be found");
        assertNotNull(associated.getSystemId(), "an opted-in href must resolve to the caller's stylesheet, not to the empty default");
        assertTrue(associated.getSystemId().endsWith("included.xsl"), "unexpected associated stylesheet: " + associated.getSystemId());
    }

    @Test
    void secureGetAssociatedStylesheetReturnsStylesheet() throws TransformerConfigurationException {
        // Positive control: a plain document with no DOCTYPE is scanned end to end and its PI found, with the href floored.
        final Source associated = secureFactory()
                .getAssociatedStylesheet(AttackTestSupport.resourceSource("associated-stylesheet-plain.xml"), null, null, null);
        assertNotNull(associated, "expected the associated stylesheet PI to be found");
        assertNull(associated.getSystemId(), "the PI href must not come back as a live URI: " + associated.getSystemId());
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
