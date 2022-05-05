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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DB;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.List;

import static com.simisinc.platform.presentation.controller.cms.ContentWidget.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class ContentWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");
    // <p>${uniqueId:sample-content}</p>

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
  }

  @Test
  void executeInLineContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>${uniqueId:another-content}");

    Content content2 = new Content();
    content2.setUniqueId("another-content");
    content2.setContent("<p>This is additional content</p>");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("another-content"))).thenReturn(content2);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertTrue(contentHtml.contains("Hello"));
    Assertions.assertTrue(contentHtml.contains("This is additional content"));
  }



  @Test
  void executeCardContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");
    preferences.put("view", "cards");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p><hr><p>Card 2</p>");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);

    List<String> cardList = (List) request.getAttribute("cardList");
    Assertions.assertNotNull(cardList);
    Assertions.assertEquals(2, cardList.size());

    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(CARD_JSP, widgetContext.getJsp());
  }

  @Test
  void executeAccordionContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");
    preferences.put("view", "accordion");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<h1>Our Products</h1>\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How do I know which product is right for me?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Example&rsquo;s Nuit and Jour are designed to meet your skin&rsquo;s specific needs and fit into your lifestyle. Do you want to make sure your skin is protected from harmful digital and UV rays? Try Jour. Or are you looking for a deeply moisturizing cream to add to your nightly routine? Try Nuit. Take a look at our product pages to learn more about each product. </span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How do I apply Jour?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Jour is a premier under-foundation cream. Apply it in gentle, upward strokes paying special attention to areas that receive the most interaction with UV and blue light.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How do I apply Nuit?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Nuit is designed to help lull you to sleep. We recommend applying it in gentle, upward strokes at the end of your nightly skincare routine. As you massage Nuit into your skin, bring your awareness to the product&rsquo;s specially-formulated scent - we designed it to help relax the mind and body!</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How often should I use Nuit/Jour?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">For best results use a pea-sized amount daily.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Should I use them together?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">These products shouldn&rsquo;t be used at the same time or layered. However, you can use them daily.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Do your products expire?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">No, our products don&rsquo;t expire.</span> For best results, use within 18 months.&nbsp;</p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Will Nuit really help me sleep?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Results will vary based on use, but chamomile and lavender extracts have been proven to help users sleep. You can read a recent study <a href=\"https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3612440/\" target=\"_blank\" rel=\"noopener\">here</a>.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Are your products tested on animals?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">No, we are cruelty-free! </span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Where can I store Example products?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Store Example products in a cool, dry place.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">What is Phytospherix?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">PhytoSpherix&trade; was created by Mirexus Inc. It is a completely natural form of glycogen, sourced and produced from plants by a new, patented green process in Canada.&nbsp; As a key energy source to skin cells, it helps to revitalize skin &ndash; dramatically improving smoothness, minimizing age spots and refining skin tone while increasing firmness and moisture content of the skin.</span></p>\n" +
        "<hr />\n" +
        "<h1>Services and Shipping</h1>\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Where does Example ship?</span></p>\n" +
        "<p>Example ships to the United States only, expanding soon!</p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How can I receive news about Example?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">You can learn about new products, updates, and events by signing up for our newsletter.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">How can I receive a sample?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">You can request a sample at </span><a href=\"mailto:media@example.com\"><span style=\"font-weight: 400;\">media@example.com</span></a><span style=\"font-weight: 400;\"> Please note we don&rsquo;t typically send out samples.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Can I cancel an order?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">We&rsquo;re sorry to see you go. Please complete the <a href=\"/contact-us\" target=\"_blank\" rel=\"noopener\">form</a> here or reach out to </span><a href=\"mailto:info@example.com\"><span style=\"font-weight: 400;\">info@example.com</span></a><span style=\"font-weight: 400;\"> with your order number and we&rsquo;ll take care of the rest.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Can I exchange an order?</span></p>\n" +
        "<p>Exchanges are available anytime. <span style=\"font-weight: 400;\">Please complete the <a href=\"/contact-us\" target=\"_blank\" rel=\"noopener\">form</a> here or reach out to </span><a href=\"mailto:info@example.com\"><span style=\"font-weight: 400;\">info@example.com</span></a><span style=\"font-weight: 400;\"> with your order number and we&rsquo;ll take care of the rest.</span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">My item arrived broken. What can I do?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">We are sorry to hear about it! Please complete the <a href=\"/contact-us\" target=\"_blank\" rel=\"noopener\">form</a> here or reach out to </span><a href=\"mailto:info@example.com\"><span style=\"font-weight: 400;\">info@example.com</span></a><span style=\"font-weight: 400;\"> with your order number and the product you&rsquo;d like to return/exchange, and we&rsquo;ll take care of the rest.</span><span style=\"font-weight: 400;\"></span></p>\n" +
        "<hr />\n" +
        "<p>&gt; <span style=\"font-weight: 400;\">Do you have gift cards?</span></p>\n" +
        "<p><span style=\"font-weight: 400;\">Example gift cards are right around the corner!</span></p>");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);

    List<AccordionSection> sectionList = (List) request.getAttribute("sectionList");

    Assertions.assertNotNull(sectionList);
    Assertions.assertEquals(2, sectionList.size());

    Assertions.assertEquals(10, sectionList.get(0).getContentList().size());
    Assertions.assertEquals(7, sectionList.get(1).getContentList().size());

    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ACCORDION_JSP, widgetContext.getJsp());
  }

  @Test
  void executeRevealContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");
    preferences.put("view", "reveal");
    preferences.put("addReveal", "true");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p><img src=\"/assets/img/1604951037677-235/Example.png\" alt=\"Example1\" width=\"600\" height=\"695\" /></p>\n" +
        "<h5><a href=\"#reveal-example-bio\">Example Name</a></h5>\n" +
        "<p>Director<br />of Development</p>\n" +
        "<p><a href=\"mailto:example@example.com\"><span class=\"fas fa-envelope-square tinymce-noedit\">&nbsp;</span></a> <a href=\"#my-example\" target=\"_blank\" rel=\"noopener\"><span class=\"fab fa-linkedin tinymce-noedit\">&nbsp;</span></a></p>\n" +
        "<hr />\n" +
        "<p><img src=\"/assets/img/1564502222206-109/Example2.jpg\" alt=\"Brian Donahue\" width=\"600\" height=\"695\" /></p>\n" +
        "<h5><a href=\"#reveal-example2-bio\">Example Name2</a></h5>\n" +
        "<p>Asst. Director<br />of Development</p>\n" +
        "<p><a href=\"mailto:example2@example.com\"><span class=\"fas fa-envelope-square tinymce-noedit\">&nbsp;</span></a> <a href=\"#my-example-2\" target=\"_blank\" rel=\"noopener\"><span class=\"fab fa-linkedin tinymce-noedit\">&nbsp;</span></a></p>\n" +
        "<hr />");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);

    List<String> cardList = (List) request.getAttribute("cardList");
    Assertions.assertNotNull(cardList);
    Assertions.assertEquals(2, cardList.size());

    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(REVEAL_JSP, widgetContext.getJsp());
  }

  @Test
  void action() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Widgets can have parameters
    widgetContext.getParameterMap().put("action", new String[]{"publish"});

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p><hr><p>Card 2</p>");
    content.setDraftContent("<p>This is Card 1</p><hr><p>This is Card 2</p>");

    // Execute the widget action
    // Mock DB calls
    Connection jdbcConnection = mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);
      try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
        staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
        ContentWidget contentWidget = new ContentWidget();
        widgetContext = contentWidget.action(widgetContext);
      }
    }
  }
}