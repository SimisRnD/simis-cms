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

package com.simisinc.platform.presentation.widgets.ecommerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.ecommerce.LoadProductCommand;
import com.simisinc.platform.domain.model.ecommerce.Product;
import com.simisinc.platform.domain.model.ecommerce.ProductSku;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuRepository;
import com.simisinc.platform.infrastructure.persistence.ecommerce.ProductSkuSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class ProductNameWidgetTest extends WidgetBase {

  @Test
  void executeSetsProductSchemaFieldsForASingleSkuProduct() {
    preferences.put("product", "widget");

    Product product = new Product();
    product.setId(5L);
    product.setUniqueId("widget");
    product.setName("Widget");
    product.setDescription("A fine widget");
    product.setImageUrl("/images/widget.jpg");

    ProductSku sku = new ProductSku();
    sku.setId(1L);
    sku.setProductId(5L);
    sku.setSku("WIDGET-1");
    sku.setPrice(new BigDecimal("19.99"));
    sku.setEnabled(true);
    sku.setStatus(ProductSku.STATUS_AVAILABLE);
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(sku);
    product.setProducts(skuList);

    try (MockedStatic<LoadProductCommand> loadProduct = mockStatic(LoadProductCommand.class);
        MockedStatic<ProductSkuRepository> skuRepository = mockStatic(ProductSkuRepository.class)) {
      loadProduct.when(() -> LoadProductCommand.loadProductByUniqueId("widget")).thenReturn(product);
      skuRepository.when(() -> ProductSkuRepository.findAll(any(ProductSkuSpecification.class), any())).thenReturn(skuList);

      new ProductNameWidget().execute(widgetContext);
    }

    assertEquals("Widget", widgetContext.getProductName());
    assertEquals("A fine widget", widgetContext.getProductDescription());
    assertEquals("/images/widget.jpg", widgetContext.getProductImageUrl());
    assertEquals(0, new BigDecimal("19.99").compareTo(widgetContext.getProductPrice()));
    assertNull(widgetContext.getProductLowPrice());
    assertEquals("USD", widgetContext.getProductCurrency());
    assertEquals("https://schema.org/InStock", widgetContext.getProductAvailability());
    assertEquals(1, widgetContext.getProductOfferCount());
  }

  @Test
  void executeSetsALowPriceForAMultiSkuProductWithDifferentPrices() {
    preferences.put("product", "widget");

    Product product = new Product();
    product.setId(5L);
    product.setUniqueId("widget");
    product.setName("Widget");

    ProductSku small = new ProductSku();
    small.setId(1L);
    small.setProductId(5L);
    small.setPrice(new BigDecimal("9.99"));
    small.setEnabled(true);
    small.setStatus(ProductSku.STATUS_SOLD_OUT);
    ProductSku large = new ProductSku();
    large.setId(2L);
    large.setProductId(5L);
    large.setPrice(new BigDecimal("14.99"));
    large.setEnabled(true);
    large.setStatus(ProductSku.STATUS_AVAILABLE);
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(small);
    skuList.add(large);
    product.setProducts(skuList);

    try (MockedStatic<LoadProductCommand> loadProduct = mockStatic(LoadProductCommand.class);
        MockedStatic<ProductSkuRepository> skuRepository = mockStatic(ProductSkuRepository.class)) {
      loadProduct.when(() -> LoadProductCommand.loadProductByUniqueId("widget")).thenReturn(product);
      skuRepository.when(() -> ProductSkuRepository.findAll(any(ProductSkuSpecification.class), any())).thenReturn(skuList);

      new ProductNameWidget().execute(widgetContext);
    }

    assertNull(widgetContext.getProductPrice());
    assertEquals(0, new BigDecimal("9.99").compareTo(widgetContext.getProductLowPrice()));
    assertEquals(2, widgetContext.getProductOfferCount());
    // one SKU is sold out but the other is available -- a shopper can still buy this product
    assertEquals("https://schema.org/InStock", widgetContext.getProductAvailability());
  }

  @Test
  void determineAvailabilityIgnoresDisabledSkusEvenIfMarkedAvailable() {
    ProductSku disabledButAvailable = new ProductSku();
    disabledButAvailable.setEnabled(false);
    disabledButAvailable.setStatus(ProductSku.STATUS_AVAILABLE);
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(disabledButAvailable);

    assertEquals("https://schema.org/OutOfStock", ProductNameWidget.determineAvailability(skuList));
  }

  @Test
  void determineAvailabilityPrefersBackOrderOverPreOrderWhenBothPresent() {
    ProductSku comingSoon = new ProductSku();
    comingSoon.setEnabled(true);
    comingSoon.setStatus(ProductSku.STATUS_COMING_SOON);
    ProductSku moreOnTheWay = new ProductSku();
    moreOnTheWay.setEnabled(true);
    moreOnTheWay.setStatus(ProductSku.STATUS_MORE_ON_THE_WAY);
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(comingSoon);
    skuList.add(moreOnTheWay);

    assertEquals("https://schema.org/BackOrder", ProductNameWidget.determineAvailability(skuList));
  }

  @Test
  void determineAvailabilityReturnsNullForAnEmptyList() {
    assertNull(ProductNameWidget.determineAvailability(new ArrayList<>()));
    assertNull(ProductNameWidget.determineAvailability(null));
  }

  @Test
  void determineCurrencyPrefersASkusOwnCurrencyOverTheDefault() {
    ProductSku sku = new ProductSku();
    sku.setCurrency("EUR");
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(sku);

    assertEquals("EUR", ProductNameWidget.determineCurrency(skuList));
  }

  @Test
  void determineCurrencyDefaultsToUsdWhenNoSkuHasOneSet() {
    ProductSku sku = new ProductSku();
    List<ProductSku> skuList = new ArrayList<>();
    skuList.add(sku);

    assertEquals("USD", ProductNameWidget.determineCurrency(skuList));
    assertEquals("USD", ProductNameWidget.determineCurrency(new ArrayList<>()));
    assertEquals("USD", ProductNameWidget.determineCurrency(null));
  }
}
