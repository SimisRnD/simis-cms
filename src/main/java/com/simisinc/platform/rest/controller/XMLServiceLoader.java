/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.rest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.internal.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/17/18 2:11 PM
 */
public class XMLServiceLoader implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(XMLServiceLoader.class);

  private List<Map<String, String>> serviceLibrary = new ArrayList<>();

  public XMLServiceLoader() {
  }

  public List<Map<String, String>> getServiceLibrary() {
    return serviceLibrary;
  }

  public void addDirectory(ServletContext context, String directory) {
    Set<String> files = context.getResourcePaths("/WEB-INF/" + directory);
    for (String file : files) {
      LOG.debug("Directory: " + directory + " found file: " + file);
      try {
        addFile(context, file);
      } catch (Exception e) {
        LOG.error("Could not parse file: " + file, e);
      }
    }
  }

  public void addFile(ServletContext context, String fileName) {
    try {
      Document document = parseDocument(fileName, context);
      addServices(document);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Document parseDocument(String file, ServletContext context)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    DocumentBuilder builder = factory.newDocumentBuilder();
    try (InputStream is = context.getResourceAsStream(file)) {
      return builder.parse(is);
    }
  }

  private void addServices(Document document) {
    NodeList objectTags = document.getElementsByTagName("service");
    for (int i = 0; i < objectTags.getLength(); i++) {
      Element objectTag = (Element) objectTags.item(i);
      String endpoint = objectTag.getAttribute("endpoint");
      String endpointValue = objectTag.getAttribute("endpoint");
      String serviceClass = objectTag.getAttribute("class");
      String method = objectTag.getAttribute("method");
      if (endpoint.contains("/{")) {
        endpoint = endpoint.substring(0, endpoint.indexOf("/{"));
      }
      Map<String, String> service = new HashMap<>();
      service.put("endpoint", endpoint);
      service.put("endpointValue", endpointValue);
      service.put("serviceClass", serviceClass);
      if (StringUtils.hasText(method)) {
        service.put("method", method);
      }
      serviceLibrary.add(service);
      LOG.debug("Found service: " + endpoint + " = " + serviceClass);
    }
  }
}
