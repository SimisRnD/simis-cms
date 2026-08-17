/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for XMLWebPageTemplateLoader's parsing of a template's rules block (issue #1287).
 *
 * @author matt rajkowski
 */
class XMLWebPageTemplateLoaderTest {

  private static ServletContext servletContextFor(String xml) {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getResourceAsStream(anyString()))
        .thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    return servletContext;
  }

  @Test
  void parsesCollectionAndFolderRulesFromTheRulesBlock() {
    String xml = "<template name=\"Job Listings\" image=\"x.png\">"
        + "<rules>"
        + "<collection uniqueId=\"job-listings\" name=\"Job Listings\" />"
        + "<folder uniqueId=\"photo-and-video-library\" name=\"Photo and Video Library\" />"
        + "</rules>"
        + "<page><section><column></column></section></page>"
        + "</template>";
    ServletContext servletContext = servletContextFor(xml);

    WebPageTemplate template = XMLWebPageTemplateLoader.loadTemplateFromFile(servletContext, "portal", "/x.xml");

    Assertions.assertNotNull(template);
    Assertions.assertEquals(2, template.getRuleList().size());
    Assertions.assertEquals("collection", template.getRuleList().get(0).getType());
    Assertions.assertEquals("job-listings", template.getRuleList().get(0).getUniqueId());
    Assertions.assertEquals("Job Listings", template.getRuleList().get(0).getName());
    Assertions.assertEquals("folder", template.getRuleList().get(1).getType());
    Assertions.assertEquals("photo-and-video-library", template.getRuleList().get(1).getUniqueId());
    Assertions.assertEquals("Photo and Video Library", template.getRuleList().get(1).getName());
  }

  @Test
  void templatesWithoutARulesBlockGetAnEmptyRuleListNotNull() {
    String xml = "<template name=\"Community Landing Page\" image=\"x.png\">"
        + "<page><section><column></column></section></page>"
        + "</template>";
    ServletContext servletContext = servletContextFor(xml);

    WebPageTemplate template = XMLWebPageTemplateLoader.loadTemplateFromFile(servletContext, "portal", "/x.xml");

    Assertions.assertNotNull(template);
    Assertions.assertNotNull(template.getRuleList());
    Assertions.assertTrue(template.getRuleList().isEmpty());
  }
}
