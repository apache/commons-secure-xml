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

import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Examples for the Javadoc {@code overview.html} file.
 */
public class JaxbExamples {

    static class MyJaxbModel {
        // JAXB model fields and methods
    }

    public MyJaxbModel unmarshalSecurelyWithSax(final InputStream xmlStream) throws Exception {
        final JAXBContext context = JAXBContext.newInstance(MyJaxbModel.class);
        final Unmarshaller unmarshaller = context.createUnmarshaller();
        // Create a secure SAXParserFactory via Apache Commons Secure XML
        final SAXParserFactory spf = SecureSAXParserFactory.newDefaultNSInstance();
        // Generate a hardened XMLReader and wrap the input source
        final XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        final SAXSource source = new SAXSource(xmlReader, new InputSource(xmlStream));
        // Safe from XXE injection and entity-expansion DoS attacks
        return (MyJaxbModel) unmarshaller.unmarshal(source);
    }

    public MyJaxbModel unmarshalSecurelyWithStax(final InputStream xmlStream) throws Exception {
        final JAXBContext context = JAXBContext.newInstance(MyJaxbModel.class);
        final Unmarshaller unmarshaller = context.createUnmarshaller();
        // Create a secure XMLInputFactory via Apache Commons Secure XML
        final XMLInputFactory xif = SecureXMLInputFactory.newDefaultFactory();
        // Create a hardened cursor reader
        final XMLStreamReader xmlReader = xif.createXMLStreamReader(xmlStream);
        try {
            // Safe from XXE injection and entity-expansion DoS attacks
            return (MyJaxbModel) unmarshaller.unmarshal(xmlReader);
        } finally {
            xmlReader.close();
        }
    }
}
