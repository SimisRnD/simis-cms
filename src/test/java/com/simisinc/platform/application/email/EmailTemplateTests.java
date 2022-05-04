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

package com.simisinc.platform.application.email;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.io.File;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests HTML Templates
 *
 * @author matt rajkowski
 * @created 4/26/2021 9:32 PM
 */
class EmailTemplateTests {

  @Test
  void emailTemplateDirectoryTest() {

    Path path = Path.of("", "src/main/webapp/WEB-INF/email-templates");
    File directory = path.toFile();
    String prefix = directory.getAbsolutePath() + "/";

    FileTemplateResolver templateResolver = new FileTemplateResolver();
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setPrefix(prefix);
    templateResolver.setSuffix(".html");
    templateResolver.setCacheTTLMs(Long.valueOf(3600000L));
    templateResolver.setCacheable(true);

    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);
    for (File file : directory.listFiles()) {
      processFile(templateEngine, file);
    }
  }

  void processFile(TemplateEngine templateEngine, File thisFile) {
    if (thisFile.isDirectory()) {
      for (File file : thisFile.listFiles()) {
        processFile(templateEngine, file);
      }
      return;
    }

    if (!thisFile.getName().endsWith(".html")) {
      return;
    }

    String file = thisFile.getName();
    String parent = thisFile.getParentFile().getName();
    String template = parent + "/" + file;

    // Site information
    Context ctx = new Context();

    Map<String, String> siteObject = new HashMap<>();
    siteObject.put("name", "Test Site");
    siteObject.put("keyword", null);
    siteObject.put("url", "http://site.example.com");
    siteObject.put("logo", "http://site.example.com/assets/image/logo.png");
    siteObject.put("accountPageUrl", "http://site.example.com/my-page");
    siteObject.put("contactUsUrl", "http://site.example.com/contact-us");
    ctx.setVariable("site", siteObject);

    Map<String, String> user = new HashMap<>();
    user.put("firstName", "First");
    user.put("lastName", "Last");
    user.put("fullName", "First Last");
    user.put("email", "email@example.com");
    user.put("organization", "Organization");
    ctx.setVariable("user", user);
    ctx.setVariable("ipAddress", "0.0.0.0");
    ctx.setVariable("location", "Some location");

    ctx.setVariable("validateAccountUrl", "/validate-account?confirmation=TEST");

    if ("cms".equals(parent)) {
      // Registration
      Map<String, String> invitedBy = new HashMap<>();
      invitedBy.put("firstName", "First");
      invitedBy.put("lastName", "Last");
      invitedBy.put("fullName", "First Last");
      ctx.setVariable("invitedBy", invitedBy);
      // Form Data
      Map<String, String> formData = new HashMap<>();
      formData.put("formUniqueId", "contact-us");
      formData.put("ipAddress", "0:0:0:0");
      ctx.setVariable("formData", formData);
    } else if ("ecommerce".equals(parent)) {
      // Shipping info
      Map<String, String> shippingAddress = new HashMap<>();
      shippingAddress.put("firstName", "First");
      shippingAddress.put("lastName", "Last");
      shippingAddress.put("fullName", "First Last");
      shippingAddress.put("street", "123 Street");
      shippingAddress.put("city", "City");
      shippingAddress.put("state", "State");
      shippingAddress.put("postalCode", "12345");
      shippingAddress.put("country", "United States");
      ctx.setVariable("shippingAddress", shippingAddress);

      // Product info
      List<Map<String, Object>> products = new ArrayList<>();
      Map<String, Object> product1 = new HashMap<>();
      product1.put("name", "Product Name");
      product1.put("barcode", "0800000000");
      product1.put("sku", "SKU");
      product1.put("price", 3.99);
      product1.put("quantity", 3);
      products.add(product1);
      products.add(product1);
      ctx.setVariable("products", products);

      // Order Info
      Map<String, Object> order = new HashMap<>();
      order.put("uniqueId", "000000-0000-0000");
      order.put("date", new Timestamp(System.currentTimeMillis()));
      order.put("shippedDate", new Timestamp(System.currentTimeMillis()));
      order.put("canceledDate", new Timestamp(System.currentTimeMillis()));
      order.put("refundedDate", new Timestamp(System.currentTimeMillis()));
      order.put("totalAmount", 19.99);
      order.put("subtotalAmount", 18.99);
      order.put("shippingFee", 5.99);
      order.put("salesTax", 1.99);
      order.put("discountAmount", 3.99);
      order.put("promoCode", "10OFF");
      order.put("paid", true);
      order.put("refundAmount", 19.99);
      order.put("paymentBrand", "Visa");
      order.put("shippingMethod", "Standard Delivery");
      order.put("shippingAddress", shippingAddress);
      order.put("ipAddress", "0.0.0.0");
      ctx.setVariable("order", order);

      // Shipping method
      Map<String, Object> shippingMethod = new HashMap<>();
      shippingMethod.put("title", "Standard Delivery");
      ctx.setVariable("shippingMethod", shippingMethod);
    }

    String html = templateEngine.process(template, ctx);
    Assertions.assertNotNull(html);

    if (html.contains("contact us")) {
      Assertions.assertTrue(html.contains("/contact-us"));
    }

    Assertions.assertFalse(html.contains("<span></span>"));
  }

}
