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

import com.simisinc.platform.domain.model.cms.WikiParserExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/21/2021 3:48 PM
 */
public class MarkdownCommand {

  private static Log LOG = LogFactory.getLog(MarkdownCommand.class);

  /**
   * Turns markdown into HTML
   *
   * @param markdown the markdown text to output as HTML
   * @return
   */
  public static String html(String markdown) {
    if (StringUtils.isBlank(markdown)) {
      return "";
    }

    // Set the markup conversion settings
    MutableDataSet options = new MutableDataSet();
    options.set(Parser.EXTENSIONS, Arrays.asList(
//        AnchorLinkExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create(),
            TaskListExtension.create(),
            TypographicExtension.create(),
            WikiLinkExtension.create(),
            YouTubeLinkExtension.create(),
            WikiParserExtension.create()
        )
    );
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
    options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
//    options.set(WikiLinkExtension.LINK_PREFIX, wikiLinkPrefix + "/");
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();

    // Convert the markup to html
    Node document = parser.parse(markdown);
    return renderer.render(document);
  }

}
