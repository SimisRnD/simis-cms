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

package com.simisinc.platform.domain.model.cms;

import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.html.MutableAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * Wiki extension to set link attributes
 *
 * @author matt rajkowski
 * @created 2/25/20 7:00 PM
 */
public class WikiParserExtension implements HtmlRenderer.HtmlRendererExtension {

  public static WikiParserExtension create() {
    return new WikiParserExtension();
  }

  @Override
  public void rendererOptions(@NotNull MutableDataHolder mutableDataHolder) {
    // add any configuration settings to options you want to apply to everything, here
  }

  @Override
  public void extend(HtmlRenderer.@NotNull Builder builder, @NotNull String s) {
    builder.attributeProviderFactory(WikiParserProvider.Factory());
  }

  static class WikiParserProvider implements AttributeProvider {
    @Override
    public void setAttributes(@NotNull Node node, @NotNull AttributablePart part, @NotNull MutableAttributes attributes) {
      if (node instanceof Link && part == AttributablePart.LINK) {
        Link link = (Link) node;
        if (link.getUrl().startsWith("https://") || link.getUrl().startsWith("http://")) {
          attributes.replaceValue("target", "_blank");
        }
      }
    }

    static AttributeProviderFactory Factory() {
      return new IndependentAttributeProviderFactory() {
        @NotNull
        @Override
        public AttributeProvider apply(@NotNull LinkResolverContext context) {
          return new WikiParserProvider();
        }
      };
    }
  }
}
