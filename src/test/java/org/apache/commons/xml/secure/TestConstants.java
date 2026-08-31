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

class TestConstants {

    /**
     * Hack for Android builds.
     *
     * Mirrors {@code XMLConstants.ACCESS_EXTERNAL_SCHEMA}, spelled out because Android's {@code XMLConstants} predates JAXP 1.5.
     */
    static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";

    /**
     * Hack for Android builds.
     *
     * Mirrors {@code XMLConstants.ACCESS_EXTERNAL_DTD}, spelled out because Android's {@code XMLConstants} predates JAXP 1.5.
     */
    static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";

    /**
     * Hack for Android builds.
     *
     * Mirrors {@code XMLConstants.ACCESS_EXTERNAL_STYLESHEET}, spelled out because Android's {@code XMLConstants} predates JAXP 1.5.
     */
    static final String ACCESS_EXTERNAL_STYLESHEET = "http://javax.xml.XMLConstants/property/accessExternalStylesheet";

    /** JAXP 1.2 schema-language attribute, recognized by the JDK's internal and the standalone Xerces alike. */
    static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    /** Xerces external-schemaLocation property, recognized by the JDK's internal and the standalone Xerces schema loaders alike. */
    static final String EXTERNAL_SCHEMA_LOCATION = "http://apache.org/xml/properties/schema/external-schemaLocation";

    /** Xerces locale property, recognized by the JDK's internal and the standalone Xerces validators alike. */
    static final String LOCALE_PROPERTY = "http://apache.org/xml/properties/locale";
}
