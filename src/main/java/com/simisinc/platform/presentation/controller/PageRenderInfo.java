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

package com.simisinc.platform.presentation.controller;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/8/18 2:15 PM
 */
public class PageRenderInfo implements ContainerRenderInfo, Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  private List<SectionRenderInfo> sections = new ArrayList<SectionRenderInfo>();
  private boolean hasWidgets = false;
  private String targetWidget = null;

  // Output properties
  private String name;
  private String title;
  private String keywords;
  private String description;
  private String imageUrl;
  private String pagePath;
  private String canonicalUrl;
  private String pageType = "website";
  private String pageUrl;
  private String cssClass = null;
  private String jsonLdData;

  // Product schema fields (issue #403), bridged from an ecommerce widget like ProductNameWidget
  private String productName;
  private String productDescription;
  private String productImageUrl;
  private BigDecimal productPrice;
  private BigDecimal productLowPrice;
  private String productCurrency;
  private String productAvailability;
  private Integer productOfferCount;

  public PageRenderInfo() {
  }

  public PageRenderInfo(Page page, String pagePath) {
    this.name = page.getName();
    this.title = page.getTitle();
    this.keywords = page.getKeywords();
    this.description = page.getDescription();
    this.cssClass = page.getCssClass();
    this.pagePath = pagePath;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public boolean hasWidgets() {
    return hasWidgets;
  }

  public void setHasWidgets(boolean hasWidgets) {
    this.hasWidgets = hasWidgets;
  }

  public String getTargetWidget() {
    return targetWidget;
  }

  public void setTargetWidget(String targetWidget) {
    this.targetWidget = targetWidget;
  }

  public List<SectionRenderInfo> getSectionRenderInfoList() {
    return sections;
  }

  public void addSection(SectionRenderInfo sectionRenderInfo) {
    sections.add(sectionRenderInfo);
  }

  public String getPagePath() {
    return pagePath;
  }

  public void setPagePath(String pagePath) {
    this.pagePath = pagePath;
  }

  public String getCssClass() {
    return cssClass;
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public void setCanonicalUrl(String canonicalUrl) {
    this.canonicalUrl = canonicalUrl;
  }

  public String getPageType() {
    return pageType;
  }

  public void setPageType(String pageType) {
    this.pageType = pageType;
  }

  public String getPageUrl() {
    return pageUrl;
  }

  public void setPageUrl(String pageUrl) {
    this.pageUrl = pageUrl;
  }

  public String getJsonLdData() {
    return jsonLdData;
  }

  public void setJsonLdData(String jsonLdData) {
    this.jsonLdData = jsonLdData;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public String getProductDescription() {
    return productDescription;
  }

  public void setProductDescription(String productDescription) {
    this.productDescription = productDescription;
  }

  public String getProductImageUrl() {
    return productImageUrl;
  }

  public void setProductImageUrl(String productImageUrl) {
    this.productImageUrl = productImageUrl;
  }

  public BigDecimal getProductPrice() {
    return productPrice;
  }

  public void setProductPrice(BigDecimal productPrice) {
    this.productPrice = productPrice;
  }

  public BigDecimal getProductLowPrice() {
    return productLowPrice;
  }

  public void setProductLowPrice(BigDecimal productLowPrice) {
    this.productLowPrice = productLowPrice;
  }

  public String getProductCurrency() {
    return productCurrency;
  }

  public void setProductCurrency(String productCurrency) {
    this.productCurrency = productCurrency;
  }

  public String getProductAvailability() {
    return productAvailability;
  }

  public void setProductAvailability(String productAvailability) {
    this.productAvailability = productAvailability;
  }

  public Integer getProductOfferCount() {
    return productOfferCount;
  }

  public void setProductOfferCount(Integer productOfferCount) {
    this.productOfferCount = productOfferCount;
  }

}
