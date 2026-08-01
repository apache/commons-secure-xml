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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks whether parsers reject a Billion Laughs payload (nested entity expansion in the internal DTD subset).
 *
 * <p>Each {@code hardened*} test asserts the library blocks the payload;
 * its {@code unconfigured*} positive control asserts the same payload parses once the limit is disabled,
 * so a block reflects the hardening rather than a broken wrapper.
 * The library pins no custom entity-expansion limit; each parser keeps its own secure-processing default, which varies by implementation:
 * {@code 2,500} (stock JDK),
 * {@code 64,000} (external Xerces under {@code FEATURE_SECURE_PROCESSING}),
 * {@code 100,000} (external Xerces' and Woodstox's own security managers).
 * A payload therefore has to exceed the largest of these.</p>
 *
 * <p>Every payload shares one six-level x10 {@link #DTD} (declaring entities is free until they are referenced) and varies only the body it expands:</p>
 *
 * <ul>
 *   <li>{@link #CONTENT_120K} ({@code 120,000}) on the JVM: above every JVM parser default.</li>
 *   <li>{@link #CONTENT_9M} ({@code 9,000,000}) on Android: above libexpat's 8 MiB billion-laughs activation threshold, the only defense there since the limit is
 *       not configurable. For that same reason the positive controls do not run on Android (see {@link #assumeEntityLimitConfigurable()}): a payload the hardened test
 *       blocks cannot be parsed even without hardening.</li>
 * </ul>
 *
 * <p>The XSLT payload spreads those same {@code 120,000} expansions over two literal result elements with content {@link #CONTENT_60K} rather than one text node,
 * because XSLTC caps a compiled literal at 65,535 bytes; see {@link #xsltPayload()}.
 * A parser counts expansions across the whole document, so the split changes nothing on the hardened side.</p>
 *
 * <p>Why a single character {@code "A"}: it makes the expanded size equal the expansion count, so a payload's size maps directly onto each parser's limit, and
 * (being ASCII) onto XSLTC's byte-counted constant-pool ceiling as well.</p>
 */
class BillionLaughsTest {

    /** 6 x 10,000 = 60,000 expansions; each half of the split XSLT body. */
    private static final String CONTENT_60K = repeatRef("lol4", 6);
    /** 100,000 + 2 x 10,000 = 120,000 expansions; above every JVM parser's secure default (2,500 / 64,000 / 100,000). */
    private static final String CONTENT_120K = "&lol5;&lol4;&lol4;";
    /** 9 x 1,000,000 = 9,000,000 expansions; above libexpat's 8 MiB billion-laughs activation threshold. */
    private static final String CONTENT_9M = repeatRef("lol6", 9);
    /**
     * Shared DTD for every payload: a six-level x10 ladder, {@code &lol1;} through {@code &lol6;} ({@code &lol6;} expands to 1,000,000). Declaring an entity costs
     * nothing until it is referenced, so the DTD is identical on every platform and only the expanded body ({@link #content()}) varies.
     */
    private static final String DTD =
            "  <!ENTITY lol \"A\">\n"
            + entityLine("lol1", "lol")     // 10
            + entityLine("lol2", "lol1")    // 100
            + entityLine("lol3", "lol2")    // 1000
            + entityLine("lol4", "lol3")    // 10000
            + entityLine("lol5", "lol4")    // 100000
            + entityLine("lol6", "lol5");   // 1000000

    /**
     * Skips a positive control on Android.
     *
     * <p>The controls prove the hardened test blocked a payload that would otherwise parse, so they must use the very payload the hardened test blocks.
     * On Android the entity-expansion limit is not configurable (libexpat's billion-laughs check cannot be lifted), so that payload
     * cannot be parsed even without hardening, leaving nothing to prove.</p>
     */
    private static void assumeEntityLimitConfigurable() {
        Assumptions.assumeFalse(AttackTestSupport.IS_ANDROID, "Skipped on Android: the entity-expansion limit is not configurable");
    }

/** The body to expand: 9,000,000 on Android, where libexpat is the only defense and the limit is not configurable, 120,000 on the JVM. */
    private static String content() {
        return AttackTestSupport.IS_ANDROID ? CONTENT_9M : CONTENT_120K;
    }

    /** Renders {@code  <!ENTITY name "&ref;&ref;...">}, one ladder rung: ten copies of {@code &ref;}. */
    private static String entityLine(final String name, final String ref) {
        return "  <!ENTITY " + name + " \"" + repeatRef(ref, 10) + "\">\n";
    }

    /** Builds {@code times} copies of the entity reference {@code &name;}. */
    private static String repeatRef(final String name, final int times) {
        final String ref = "&" + name + ";";
        final StringBuilder sb = new StringBuilder(ref.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(ref);
        }
        return sb.toString();
    }

    private static String withDoctype(final String rootQName, final String body) {
        return "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE " + rootQName + " [\n"
                + DTD
                + "]>\n"
                + body + "\n";
    }

    /** Payload for DOM/SAX/XmlReader/StAX/Transformer/Validator. */
    private static String xmlPayload() {
        return withDoctype("root", AttackTestSupport.xmlBody(content()));
    }

    /** XSD payload. */
    private static String xsdPayload() {
        return withDoctype("xs:schema", AttackTestSupport.xsdBody(content()));
    }

    /**
     * XSLT payload; the JVM body splits its expansions across two literal result elements.
     *
     * <p>XSLTC compiles each literal text node into a class-file string constant, and a {@code CONSTANT_Utf8} entry holds at most 65,535 bytes.
     * We split the payload into two constants to still trigger the Xerces 100k expansion limit, but without any text node above 64 KiB.</p>
     *
     * <p>Android keeps the single {@link #CONTENT_9M} body: libexpat aborts during the parse, so no translet is ever compiled.</p>
     */
    private static String xsltPayload() {
        final String body = AttackTestSupport.IS_ANDROID
                ? CONTENT_9M
                : "<a>" + CONTENT_60K + "</a><b>" + CONTENT_60K + "</b>";
        return withDoctype("xsl:stylesheet", AttackTestSupport.xsltBody(body));
    }

    @Test
    @Tag("dom")
    void hardenedDomBlocks() {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES,
                "Skipped: platform DOM does not resolve user-defined entities");
        AttackTestSupport.assertDomBlocks(xmlPayload());
    }

    @Test
    @Tag("sax")
    void hardenedSaxBlocks() {
        AttackTestSupport.assertSaxBlocks(xmlPayload());
    }

    @Test
    @Tag("schema")
    void hardenedSchemaBlocks() {
        AttackTestSupport.assertSchemaBlocks(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void hardenedStaxBlocks() {
        AttackTestSupport.assertStaxBlocks(xmlPayload());
    }

    @Test
    @Tag("trax")
    void hardenedTemplatesBlocks() {
        AttackTestSupport.assertTemplatesBlocks(AttackTestSupport.streamSource(xsltPayload()));
    }

    @Test
    @Tag("trax")
    void hardenedTransformerBlocks() {
        AttackTestSupport.assertTransformerBlocks(xmlPayload());
    }

    @Test
    @Tag("schema")
    void hardenedValidatorBlocks() {
        AttackTestSupport.assertValidatorBlocks(xmlPayload());
    }

    @Test
    @Tag("sax")
    void hardenedXmlReaderBlocks() {
        AttackTestSupport.assertXmlReaderBlocks(xmlPayload());
    }

    @Test
    @Tag("dom")
    void unconfiguredDomParses() {
        Assumptions.assumeTrue(AttackTestSupport.DOM_RESOLVES_INTERNAL_ENTITIES,
                "Skipped: platform DOM does not resolve user-defined entities");
        AttackTestSupport.assertPermissiveDomParses(xmlPayload());
    }

    @Test
    @Tag("sax")
    void unconfiguredSaxParses() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveSaxParses(xmlPayload());
    }

    @Test
    @Tag("schema")
    void unconfiguredSchemaCompiles() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveSchemaCompiles(AttackTestSupport.streamSource(xsdPayload()));
    }

    @Test
    @Tag("stax")
    void unconfiguredStaxParses() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveStaxParses(xmlPayload());
    }

    @Test
    @Tag("trax")
    void unconfiguredTemplatesCompiles() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveTemplatesCompiles(xsltPayload());
    }

    @Test
    @Tag("trax")
    void unconfiguredTransformerTransforms() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveTransformerTransforms(xmlPayload());
    }

    @Test
    @Tag("schema")
    void unconfiguredValidatorValidates() {
        assumeEntityLimitConfigurable();
        AttackTestSupport.assertPermissiveValidatorValidates(xmlPayload());
    }
}
