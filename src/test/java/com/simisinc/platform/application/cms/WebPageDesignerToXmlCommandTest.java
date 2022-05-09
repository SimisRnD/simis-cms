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

import com.simisinc.platform.domain.model.cms.WebPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class WebPageDesignerToXmlCommandTest {

  @Test
  void convertFromBootstrapHtml() {

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
            "  <div class=\"column col-sm-12 col-md-12 col-xs-12\"><!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region--><!--gm-editable-region--><h3 data-widget=\"widget-prototype\"><span style=\"font-size: inherit;\">Some</span></h3><!--/gm-editable-region--></div>\n" +
            "</div>\n" +
            "div class=\"row\">\n" +
            " <div class=\"column col-md-7 col-sm-7 col-xs-7\">\n" +
            "   <!--gm-editable-region--><h3 data-widget=\"widget-prototype\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
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
            "    <!--gm-editable-region--><h3 data-widget=\"widget-prototype\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
            "  </div>\n" +
            "  <div class=\"column col-md-5 col-sm-5 col-xs-5 callout radius primary text-center\">\n" +
            "    <!--gm-editable-region--><h3 data-widget=\"widget-prototype\">Headline</h3><p>Write a description</p><!--/gm-editable-region-->\n" +
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
            "      <widget name=\"widget-prototype\">\n" +
            "        <html><![CDATA[<h3>Some</h3>]]></html>\n" +
            "        <uniqueId>web-page-content-area-6</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-7 cell\">\n" +
            "      <widget name=\"widget-prototype\">\n" +
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
            "      <widget name=\"widget-prototype\">\n" +
            "        <html><![CDATA[<h3>Headline</h3><p>Write a description</p>]]></html>\n" +
            "        <uniqueId>web-page-content-area-10</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "    <column class=\"small-5 cell text-center callout radius primary\">\n" +
            "      <widget name=\"widget-prototype\">\n" +
            "        <html><![CDATA[<h3>Headline</h3><p>Write a description</p>]]></html>\n" +
            "        <uniqueId>web-page-content-area-11</uniqueId>\n" +
            "      </widget>\n" +
            "    </column>\n" +
            "  </section>\n" +
            "\n" +
            "</page>\n";

    WebPage webPage = new WebPage();
    webPage.setLink("/web-page");
    String result = WebPageDesignerToXmlCommand.convertFromBootstrapHtml(webPage, content);
    Assertions.assertEquals(xml, result);
  }
}