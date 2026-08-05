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

package com.simisinc.platform.application.cms;

import java.util.Arrays;

import com.simisinc.platform.domain.model.cms.WikiParserExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gitlab.GitLabExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Renders wiki page markdown to sanitized HTML. Extracted from {@code WikiWidget} so the live page
 * and the editor's preview can never drift, and so preview cannot reintroduce the stored-XSS class
 * {@link HtmlCommand#cleanRenderedMarkdown} guards against: markdown is not safe on its own
 * (CommonMark passes raw HTML through), and wiki bodies are stored unsanitized, so sanitizing at
 * render time is the only safety boundary -- any second implementation of this method is a second
 * place that boundary can be forgotten.
 *
 * @author SimIS
 * @created 7/28/2026
 */
public class RenderWikiMarkdownCommand {

  public static String toHtml(String body, String wikiLinkPrefix) {
    MutableDataSet options = new MutableDataSet();
    options.set(Parser.EXTENSIONS, Arrays.asList(
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListExtension.create(),
        TypographicExtension.create(),
        WikiLinkExtension.create(),
        YouTubeLinkExtension.create(),
        WikiParserExtension.create(),
        GitLabExtension.create()));
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
    options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
    options.set(WikiLinkExtension.LINK_PREFIX, wikiLinkPrefix + "/");
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();

    Node document = parser.parse(body == null ? "" : body);
    return ContentImageSrcsetCommand.injectSrcset(HtmlCommand.cleanRenderedMarkdown(renderer.render(document)));
  }
}
