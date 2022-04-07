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
import com.simisinc.platform.presentation.controller.cms.Column;
import com.simisinc.platform.presentation.controller.cms.Page;
import com.simisinc.platform.presentation.controller.cms.Section;
import com.simisinc.platform.presentation.controller.cms.Widget;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/9/18 10:00 AM
 */
public class WebPageDesignerToXmlCommand {

  private static Log LOG = LogFactory.getLog(WebPageDesignerToXmlCommand.class);

  public static String convertFromBootstrapHtml(WebPage webPage, String content) {

    //  <div class="row">
    //    <div class="column col-sm-12 col-md-12 col-xs-12">
    //      <!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region-->
    //      <!--gm-editable-region--><h3 data-widget="prototype"><span style="font-size: inherit;">Some</span></h3><!--/gm-editable-region-->
    //    </div>
    //  </div>
    //  <div class="row">
    //    <div class="column col-md-7 col-sm-7 col-xs-7"></div><div class="column col-md-5 col-sm-5 col-xs-5"></div>
    //  </div>

    //  <div class="row">
    //    <div class="column col-sm-12 col-md-12 col-xs-12"><!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region--><!--gm-editable-region--><h3 data-widget="widget-prototype"><span style="font-size: inherit;">Some</span></h3><!--/gm-editable-region--></div>
    //  </div>
    // <div class="row">
    //   <div class="column col-md-7 col-sm-7 col-xs-7">
    //     <!--gm-editable-region--><h3 data-widget="widget-prototype">Headline</h3><p>Write a description</p><!--/gm-editable-region-->
    //   </div>
    //   <div class="column col-md-5 col-sm-5 col-xs-5">
    //     <!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region-->
    //   </div>
    // </div>

    //   <div class="row text-center">
    //    <div class="column col-sm-12 col-md-12 col-xs-12"><!--gm-editable-region--><p>Write your content</p><!--/gm-editable-region--></div>
    //  </div>
    //  <div class="row">
    //    <div class="column col-md-7 col-sm-7 col-xs-7">
    //      <!--gm-editable-region--><h3 data-widget="widget-prototype">Headline</h3><p>Write a description</p><!--/gm-editable-region-->
    //    </div>
    //    <div class="column col-md-5 col-sm-5 col-xs-5 callout radius primary text-center">
    //      <!--gm-editable-region--><h3 data-widget="widget-prototype">Headline</h3><p>Write a description</p><!--/gm-editable-region-->
    //    </div>
    //  </div>

    Page page = new Page();
    String uniqueIdPrefix = webPage.getLink().substring(1);
    if (StringUtils.isBlank(uniqueIdPrefix)) {
      uniqueIdPrefix = "home";
    }

    int widgetCount = 0;

    // Process the rows
    int rowCount = 0;
    int rowIdx = -1;
    while ((rowIdx = content.indexOf("<div class=\"row", rowIdx)) > -1) {
      ++rowCount;
      // Determine the row content range
      int endRowIdx = content.indexOf("<div class=\"row", rowIdx + 1);
      if (endRowIdx == -1) {
        endRowIdx = content.length() - 1;
      }
      String thisRow = content.substring(rowIdx, endRowIdx);
      LOG.debug("Found row (" + rowCount + ")");

      List<String> rowClasses = new ArrayList<>(Arrays.asList(thisRow.substring(12, thisRow.indexOf("\"", 12)).split(" ")));
      LOG.debug("row classes: " + rowClasses.size() + " " + rowClasses.toString());

      Section section = new Section();
      section.setCssClass(makeSectionCss(rowClasses));
      page.getSections().add(section);

      // For each row, process the columns
      int columnCount = 0;
      int columnIdx = -1;
      while ((columnIdx = thisRow.indexOf("<div class=\"column", columnIdx)) > -1) {
        ++columnCount;
        // Determine the column content range
        int endColumnIdx = thisRow.indexOf("<div class=\"column", columnIdx + 1);
        if (endColumnIdx == -1) {
          endColumnIdx = thisRow.length() - 1;
        }
        String thisColumn = thisRow.substring(columnIdx, endColumnIdx);
        LOG.debug("Found column (" + columnCount + ")");

        List<String> columnClasses = new ArrayList<>(Arrays.asList(thisColumn.substring(12, thisColumn.indexOf("\"", 12)).split(" ")));
        LOG.debug("column classes: " + columnClasses.size() + " " + columnClasses.toString());

        Column column = new Column();
        column.setCssClass(makeColumnCss(columnClasses));
        section.getColumns().add(column);

        // For each column, process the widgets (if there are any, else add a default one)
        int widgetIdx = -1;
        int columnWidgetCount = 0;
        while ((widgetIdx = thisColumn.indexOf("<!--gm-editable-region-->", widgetIdx)) > -1) {
          ++widgetCount;
          ++columnWidgetCount;
          // Determine the column content range
          int endWidgetIdx = thisColumn.indexOf("<!--gm-editable-region-->", widgetIdx + 1);
          if (endWidgetIdx == -1) {
            endWidgetIdx = thisColumn.length() - 1;
          }
          // Create a widget
          String thisWidget = thisColumn.substring(widgetIdx, endWidgetIdx);
          Widget widget = createWidget(uniqueIdPrefix, widgetCount, thisWidget);
          LOG.debug("Found widget (" + widgetCount + "): " + thisWidget);
          column.getWidgets().add(widget);
          ++widgetIdx;
        }
        if (columnWidgetCount == 0) {
          // Add a default 1 (use content)
          ++widgetCount;
          LOG.debug("Creating widget (" + widgetCount + "): " + "<not specified>");
          Widget widget = new Widget();
          widget.setWidgetName("content");
          widget.getPreferences().put("uniqueId", uniqueIdPrefix + "-content-area-" + widgetCount);
          column.getWidgets().add(widget);
        }
        ++columnIdx;
      }
      ++rowIdx;
    }
    // Generate the XML from the page/sections/columns/widgets
    return WebPageDesignerToXmlCommand.toXml(page);
  }

  private static Widget createWidget(String uniqueIdPrefix, int widgetCount, String thisWidget) {
    Widget widget = new Widget();
    widget.setWidgetName("content");
    int widgetDataIdx = thisWidget.indexOf("data-widget=\"");
    if (widgetDataIdx > -1) {
      String widgetName = thisWidget.substring(widgetDataIdx + 13, thisWidget.indexOf("\"", widgetDataIdx + 13));
      if (StringUtils.isNotBlank(widgetName)) {
        widget.setWidgetName(widgetName);
      }
    }
    // Determine the widget preferences // ideally have a library of widgets and their preferences...
    String htmlContent = thisWidget.substring(25, thisWidget.indexOf("<!--/gm-editable-region-->"));
    String cleanContent = HtmlCommand.cleanContent(htmlContent);
    if ("prototype".equals(widget.getWidgetName())) {
      widget.getPreferences().put("html", cleanContent);
    } else {
      widget.getPreferences().put("uniqueId", uniqueIdPrefix + "-content-area-" + widgetCount);
      if (!"<p>Write your content</p>".equals(htmlContent)) {
        widget.getPreferences().put("html", cleanContent);
      }
    }
//          widget.setCssClass();
    return widget;
  }

  private static String makeSectionCss(List<String> cssList) {
    // row grid-x margin-x padding-x align-center text-center margin-bottom-x
    StringBuilder sb = new StringBuilder();

    // Allow 1 grid type
    if (cssList.contains("margin-x")) {
      sb.append("grid-x margin-x");
    } else if (cssList.contains("padding-x")) {
      sb.append("grid-x padding-x");
    } else if (cssList.contains("grid-x")) {
      sb.append("grid-x");
    }

    // Allow 1 formatting type
    if (cssList.contains("align-center")) {
      appendWithSpace(sb, "align-center");
    } else if (cssList.contains("align-left")) {
      appendWithSpace(sb, "align-left");
    } else if (cssList.contains("align-right")) {
      appendWithSpace(sb, "align-right");
    } else if (cssList.contains("align-justify")) {
      appendWithSpace(sb, "align-justify");
    } else if (cssList.contains("align-spaced")) {
      appendWithSpace(sb, "align-spaced");
    }

    // Allow 1 formatting type
    if (cssList.contains("text-center")) {
      appendWithSpace(sb, "text-center");
    } else if (cssList.contains("text-left")) {
      appendWithSpace(sb, "text-left");
    } else if (cssList.contains("text-right")) {
      appendWithSpace(sb, "text-right");
    } else if (cssList.contains("text-justify")) {
      appendWithSpace(sb, "text-justify");
    }

    // Add margin and padding
    for (String value: cssList) {
      if (value.startsWith("margin-") || value.startsWith("padding-")) {
        appendWithSpace(sb,value);
      }
    }
    return sb.toString();
  }

  private static String makeColumnCss(List<String> cssList) {
    // column col-sm-12 col-md-12 col-xs-12 callout radius primary secondary box
    StringBuilder sb = new StringBuilder();
    // Determine the small, medium, large
    String small = "12";
    String medium = "12";
    String large = "12";
    for (String css : cssList) {
      if (css.startsWith("col-xs-")) {
        small = css.substring(7);
      } else if (css.startsWith("col-sm-")) {
        medium = css.substring(7);
      } else if (css.startsWith("col-md-")) {
        large = css.substring(7);
      }
    }
    sb.append("small-").append(small);
    if (!medium.equals(small)) {
      sb.append(" medium-").append(medium);
    }
    if (!large.equals(medium)) {
      sb.append(" large-").append(large);
    }
    sb.append(" cell");
    // Extras...
    if (cssList.contains("text-center")) {
      sb.append(" text-center");
    }
    // Callout
    if (cssList.contains("callout")) {
      sb.append(" callout");
      if (cssList.contains("radius")) {
        sb.append(" radius");
      }
      if (cssList.contains("primary")) {
        sb.append(" primary");
      } else if (cssList.contains("secondary")) {
        sb.append(" secondary");
      } else if (cssList.contains("box")) {
        sb.append(" box");
      }
    }
    return sb.toString();
  }

  public static String toXml(Page page) {
    StringBuilder sb = new StringBuilder();
    sb.append("<page>").append("\n");
    for (Section section : page.getSections()) {
      sb.append("\n");
      sb.append("  <section");
      if (StringUtils.isNotBlank(section.getCssClass())) {
        sb.append(" class=\"").append(section.getCssClass()).append("\"");
      }
      sb.append(">").append("\n");
      for (Column column : section.getColumns()) {
        sb.append("    <column");
        if (StringUtils.isNotBlank(column.getCssClass())) {
          sb.append(" class=\"").append(column.getCssClass()).append("\"");
        }
        sb.append(">").append("\n");
        for (Widget widget : column.getWidgets()) {
          sb.append("      <widget");
          sb.append(" name=\"").append(widget.getWidgetName()).append("\"");
          if (StringUtils.isNotBlank(widget.getCssClass())) {
            sb.append(" class=\"").append(widget.getCssClass()).append("\"");
          }
          sb.append(">").append("\n");
          for (String key : widget.getPreferences().keySet()) {
            String value = widget.getPreferences().get(key);
            boolean useCdata = true;
            if (StringUtils.isAlphanumeric(RegExUtils.removeAll(value, "-"))) {
              useCdata = false;
            }
            sb.append("        <").append(key).append(">");
            if (useCdata) {
              sb.append("<![CDATA[");
            }
            sb.append(value);
            if (useCdata) {
              sb.append("]]>");
            }
            sb.append("</").append(key).append(">").append("\n");
          }
          sb.append("      </widget>").append("\n");
        }
        sb.append("    </column>").append("\n");
      }
      sb.append("  </section>").append("\n");
    }
    sb.append("\n");
    sb.append("</page>").append("\n");
    return sb.toString();
  }

  public static StringBuilder appendWithSpace(StringBuilder sb, String value) {
    if (StringUtils.isEmpty(value)) {
      return sb;
    }
    if (sb.length() > 0) {
      sb.append(" ");
    }
    sb.append(value);
    return sb;
  }
}
