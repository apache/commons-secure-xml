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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.validation.ValidatorHandler;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.XMLReader;

/**
 * Setter helpers shared by the bundled hardening providers.
 *
 * <p>Each overload wraps a single JAXP setter (feature, attribute or property) in a try/catch that translates any thrown exception into a
 * {@link HardeningException} whose message names the offending feature, attribute or property and the concrete factory class.</p>
 */
final class JaxpSetters {

    /** Action that may throw any exception; used to share a single try/catch around every JAXP setter. */
    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final String KIND_FEATURE = "feature";

    private static void apply(final Object factory, final String kind, final String name, final ThrowingAction action) {
        try {
            action.run();
        } catch (final Exception e) {
            throw new HardeningException("Failed to set " + kind + " '" + name + "' on " + factory.getClass().getName(), e);
        }
    }

    static void setFeature(final DocumentBuilderFactory factory, final String feature, final boolean value) {
        apply(factory, KIND_FEATURE, feature, () -> factory.setFeature(feature, value));
    }

    static void setFeature(final SAXParserFactory factory, final String feature, final boolean value) {
        apply(factory, KIND_FEATURE, feature, () -> factory.setFeature(feature, value));
    }

    static void setFeature(final TransformerFactory factory, final String feature, final boolean value) {
        apply(factory, KIND_FEATURE, feature, () -> factory.setFeature(feature, value));
    }

    static void setFeature(final XPathFactory factory, final String feature, final boolean value) {
        apply(factory, KIND_FEATURE, feature, () -> factory.setFeature(feature, value));
    }

    static void setFeature(final SchemaFactory factory, final String feature, final boolean value) {
        apply(factory, KIND_FEATURE, feature, () -> factory.setFeature(feature, value));
    }

    static void setFeature(final Validator validator, final String feature, final boolean value) {
        apply(validator, KIND_FEATURE, feature, () -> validator.setFeature(feature, value));
    }

    static void setFeature(final ValidatorHandler handler, final String feature, final boolean value) {
        apply(handler, KIND_FEATURE, feature, () -> handler.setFeature(feature, value));
    }

    static void setFeature(final XMLReader reader, final String feature, final boolean value) {
        apply(reader, KIND_FEATURE, feature, () -> reader.setFeature(feature, value));
    }

    static void setOptionalFeature(final DocumentBuilderFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            // Ignored: the implementation does not recognize this feature.
        }
    }

    static void setOptionalFeature(final XMLReader reader, final String feature, final boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (final Exception e) {
            // Ignored: the implementation does not recognize this feature.
        }
    }

    static void setOptionalFeature(final XPathFactory factory, final String feature, final boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (final Exception e) {
            // Ignored: the implementation does not recognize this feature.
        }
    }

    static void setOptionalProperty(final XMLInputFactory factory, final String property, final Object value) {
        trySetProperty(factory, property, value);
    }

    /**
     * Sets a property on an {@link XMLReader} and returns whether the implementation accepted it.
     *
     * @param reader   The target reader on which to set the property.
     * @param property The name of the property to set.
     * @param value    The value of the property to set.
     * @return {@code true} if the property was applied, {@code false} if the implementation rejected it.
     */
    static boolean trySetProperty(final XMLReader reader, final String property, final Object value) {
        try {
            reader.setProperty(property, value);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Sets a property on an {@link XMLInputFactory} and returns whether the implementation accepted it.
     *
     * @param factory   The target factory on which to set the property.
     * @param property The name of the property to set.
     * @param value     The value of the property to set.
     * @return {@code true} if the property was applied, {@code false} if the implementation rejected it.
     */
    static boolean trySetProperty(final XMLInputFactory factory, final String property, final Object value) {
        try {
            factory.setProperty(property, value);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private JaxpSetters() {
    }
}
