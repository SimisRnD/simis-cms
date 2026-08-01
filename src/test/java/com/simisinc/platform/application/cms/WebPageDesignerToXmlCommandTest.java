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

package com.simisinc.platform.application.cms;

import static org.mockito.Mockito.mockStatic;

import java.util.Map;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.WebPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class WebPageDesignerToXmlCommandTest {

  // The real registry (WebPageXmlLayoutCommand.getWidgetLibrary()) is loaded once at app startup from
  // widget-library.xml via a ServletContext -- not populated in a plain unit test, so every test here
  // mocks it, matching the established pattern in MutateLayoutCommandTest.
  private static final Map<String, String> REGISTERED_WIDGETS = Map.of(
      "content", "com.simisinc.platform.presentation.widgets.cms.ContentWidget",
      "prototype", "com.simisinc.platform.presentation.widgets.cms.PrototypeWidget",
      "map", "com.simisinc.platform.presentation.widgets.maps.MapWidget");

  private static String convert(WebPage webPage, String content) throws DataException {
    try (MockedStatic<WebPageXmlLayoutCommand> cmd = mockStatic(WebPageXmlLayoutCommand.class)) {
      cmd.when(WebPageXmlLayoutCommand::getWidgetLibrary).thenReturn(REGISTERED_WIDGETS);
      return WebPageDesignerToXmlCommand.convertFromBootstrapHtml(webPage, content);
    }
  }

  @Test
  void convertFromBootstrapHtml() throws DataException {

    String content =
        "<div class=\"row\">\n" +
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\">\n" +
            "    <!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region-->\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"prototype\"><span style=\"font-size: inherit;\">Some</span></h3><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "</div>\n" +
            "<div class=\"row\">\n" +
            "  <div class=\"column col-md-7 col-sm-7 col-xs-7\"></div><div class=\"column col-md-5 col-sm-5 col-xs-5\"></div>\n" +
            "</div>\n" +
            "\n" +
            "<div class=\"row\">\n" +
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\"><!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region--><!--gm-editable-region--><h3 data-widget=\"content\"><span style=\"font-size: inherit;\">Some</span></h3><!--/gm-editable-region--></div>\n" +
            "</div>\n" +
            "div class=\"row\">\n" +
            " <div class=\"column col-md-7 col-sm-7 col-xs-7\">\n" +
            "   <!--gm-editable-region--><h3 data-widget=\"content\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            " </div>\n" +
            " <div class=\"column col-md-5 col-sm-5 col-xs-5\">\n" +
            "   <!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region-->\n" +
            " </div>\n" +
            "/div>\n" +
            "\n" +
            " <div class=\"row text-center\">\n" +
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\"><!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region--></div>\n" +
            "</div>\n" +
            "<div class=\"row\">\n" +
            "  <div class=\"column col-md-7 col-sm-7 col-xs-7\">\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"content\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "  <div class=\"column col-md-5 col-sm-5 col-xs-5 callout radius primary text-center\">\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"content\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "</div>";

    String xml =
        "<page>\n" +
            "\n" +
            "  <section>\n" +
            "    <column class=\"small-12 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-1</uniqueId>\n" +
            "      </widget>\n" +
            "      <widget name=\"prototype\">\n" +
            "        <html><![CDATA[<h3>Some</h3>]]></html>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "  <section>\n" +
            "    <column class=\"small-7 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-3</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-5 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-4</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "  <section>\n" +
            "    <column class=\"small-12 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-5</uniqueId>\n" +
            "      </widget>\n" +
            "      <widget name=\"content\">\n" +
            "        <html><![CDATA[<h3>Some</h3>]]></html>\n" +
            "        <uniqueId>web-page-content-area-6</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-7 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <html><![CDATA[<h3>Headline</h3><p>Write a description</p>]]></html>\n" +
            "        <uniqueId>web-page-content-area-7</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-5 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-8</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "  <section class=\"text-center\">\n" +
            "    <column class=\"small-12 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <uniqueId>web-page-content-area-9</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "  <section>\n" +
            "    <column class=\"small-7 cell\">\n" +
            "      <widget name=\"content\">\n" +
            "        <html><![CDATA[<h3>Headline</h3><p>Write a description</p>]]></html>\n" +
            "        <uniqueId>web-page-content-area-10</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-5 cell text-center callout radius primary\">\n" +
            "      <widget name=\"content\">\n" +
            "        <html><![CDATA[<h3>Headline</h3><p>Write a description</p>]]></html>\n" +
            "        <uniqueId>web-page-content-area-11</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "</page>\n";

    WebPage webPage = new WebPage();
    webPage.setLink("/web-page");
    String result = convert(webPage, content);
    Assertions.assertEquals(xml, result);
  }

  // Regression coverage for issue #532: the legacy page designer's widget picker had no real widget-type
  // chooser, so a click always inserted a hardcoded "prototype" placeholder. Fixing that meant letting the
  // client insert any widget name it wants -- which meant the server-side conversion could no longer trust
  // it blindly. Before this, an unregistered data-widget value saved successfully and then silently
  // vanished at render time (XMLContainerCommands.appendWidgets() drops unknown widgets with no error
  // anywhere), which is a worse failure mode than never adding it at all on this save path specifically
  // (no draft/publish safety net -- see WebPageDesignerWidget.post()).

  @Test
  void convertFromBootstrapHtmlRejectsAnUnregisteredWidgetName() {
    String content =
        "<div class=\"row\">\n" +
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\">\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"not-a-real-widget\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "</div>";

    WebPage webPage = new WebPage();
    webPage.setLink("/web-page");
    DataException exception = Assertions.assertThrows(DataException.class, () -> convert(webPage, content));
    Assertions.assertTrue(exception.getMessage().contains("not-a-real-widget"),
        "the error should name the offending widget type, not just say something failed");
  }

  @Test
  void convertFromBootstrapHtmlAcceptsARealNonContentWidgetType() throws DataException {
    // The whole point of #532: a real widget type (map, per the issue's own example), not just content.
    String content =
        "<div class=\"row\">\n" +
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\">\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"map\">Map</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "</div>";

    WebPage webPage = new WebPage();
    webPage.setLink("/web-page");
    String result = convert(webPage, content);

    Assertions.assertTrue(result.contains("<widget name=\"map\">"), result);
  }
}