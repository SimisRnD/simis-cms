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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/5/2022 6:45 PM
 * @song Save Your Tears (Remix) - The Weeknd & Ariana Grande
 * @song Dua Lipa - Future Nostalgia
 */
public class XMLWebPageTemplateLoader implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(XMLHeaderLoader.class);

  public XMLWebPageTemplateLoader() {
  }

  public static List<WebPageTemplate> retrieveTemplateList(ServletContext context) {
    List<WebPageTemplate> webPageTemplateList = new ArrayList<>();
    retrieveTemplateList(context, webPageTemplateList, "/WEB-INF/web-templates/");
    return webPageTemplateList;
  }

  public static void retrieveTemplateList(ServletContext context, List<WebPageTemplate> webPageTemplateList, String directory) {
    Set<String> paths = context.getResourcePaths(directory);
    if (paths == null || paths.isEmpty()) {
      return;
    }
    for (String filePath : paths) {
      if (filePath.endsWith("/")) {
        // Scan this subdirectory
        retrieveTemplateList(context, webPageTemplateList, filePath);
      } else if (filePath.endsWith(".xml")) {
        // Parse the file
        String arg = directory.substring(0, directory.lastIndexOf("/"));
        String category = arg.substring(arg.lastIndexOf("/") + 1);
        WebPageTemplate webPageTemplate = loadTemplateFromFile(context, category, filePath);
        webPageTemplateList.add(webPageTemplate);
      }
    }
  }

  public static WebPageTemplate loadTemplateFromFile(ServletContext context, String category, String fileName) {
    try {
      Document document = readDocument(context, fileName);
      return parseDocument(document, category);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static Document readDocument(ServletContext context, String file)
      throws FactoryConfigurationError, ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputStream is = context.getResourceAsStream(file);
    return builder.parse(is);
  }

  private static WebPageTemplate parseDocument(Document document, String category) {

    Element docEl = document.getDocumentElement();
    NodeList pageObjectTags = document.getElementsByTagName("page");
    Element pageEl = (Element) pageObjectTags.item(0);

    // Extract the XML Values
    String priority = docEl.getAttribute("priority");
    String name = docEl.getAttribute("name");
    String image = docEl.getAttribute("image");
    String pageXml = getXmlContent(pageEl);
    String css = null;
    NodeList cssObjectTags = document.getElementsByTagName("css");
    if (cssObjectTags.getLength() > 0) {
      Element cssEl = (Element) cssObjectTags.item(0);
      css = cssEl.getTextContent();
    }

    // The template needs a uniqueId
    String uniqueId = category + " / " + name;
    long idValue = ByteBuffer.wrap(DigestUtils.md5(uniqueId)).getInt();
    long id = idValue + Integer.MAX_VALUE;

    // Construct the object
    WebPageTemplate webPageTemplate = new WebPageTemplate();
    webPageTemplate.setId(-1L);
    webPageTemplate.setUniqueId(id);
    if (StringUtils.isNumeric(priority)) {
      webPageTemplate.setTemplateOrder(Integer.parseInt(priority));
    }
    webPageTemplate.setName(name);
    webPageTemplate.setCategory(category);
    webPageTemplate.setImagePath(image);
    webPageTemplate.setPageXml(pageXml);
    webPageTemplate.setCss(css);

    return webPageTemplate;
  }

  private static String getXmlContent(Element el) {
    String xml = null;
    try {
      TransformerFactory transFactory = TransformerFactory.newInstance();
      Transformer transformer = transFactory.newTransformer();
      StringWriter buffer = new StringWriter();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(new DOMSource(el),
          new StreamResult(buffer));
      xml = buffer.toString();
    } catch (TransformerException e) {
      e.printStackTrace();
    }
    return xml;
  }
}
