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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.FormatDateCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.domain.model.cms.FaqQuestion;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the schema.org JSON-LD graph a page emits for search engines (issue #1795). Split out of
 * PageServlet, which had grown past the file-length limit: none of this touches the servlet, the
 * request, or the session -- every method is a pure function of a page's already-loaded content,
 * which is also what makes the whole graph testable without a container.
 *
 * @author SimIS Inc.
 */
public class StructuredDataCommand {

  private static Log LOG = LogFactory.getLog(StructuredDataCommand.class);

  /**
   * Without a menu, for callers that have none. The menu only feeds the breadcrumb fallback in
   * {@link #computeMenuBreadcrumbList}, so omitting it yields exactly the output this method
   * produced before issue #1795 -- which is what the existing tests assert.
   */
  static String generateJsonLdData(PageRenderInfo pageRenderInfo, String siteUrl, String pagePath,
                                    Map<String, String> sitePropertyMap,
                                    Item item, Collection collection, WebPage webPage,
                                    List<SocialMediaLink> socialMediaLinkList) {
    return generateJsonLdData(pageRenderInfo, siteUrl, pagePath, sitePropertyMap, item, collection,
        webPage, socialMediaLinkList, null);
  }

  static String generateJsonLdData(PageRenderInfo pageRenderInfo, String siteUrl, String pagePath,
                                    Map<String, String> sitePropertyMap,
                                    Item item, Collection collection, WebPage webPage,
                                    List<SocialMediaLink> socialMediaLinkList,
                                    List<MenuTab> menuTabList) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> jsonLd = new LinkedHashMap<>();
      jsonLd.put("@context", "https://schema.org");

      List<Map<String, Object>> graph = new ArrayList<>();

      // Add Organization schema (for homepage) - include on every page for consistency
      if (StringUtils.isNotBlank(sitePropertyMap.get("site.name"))) {
        Map<String, Object> organization = new LinkedHashMap<>();
        organization.put("@type", "Organization");
        organization.put("@id", siteUrl + "#organization");
        organization.put("name", sitePropertyMap.get("site.name"));
        organization.put("url", siteUrl);

        // The same site.description that already feeds the meta description, the Open Graph tags,
        // llms.txt and the feeds -- it was simply never put on the Organization node (issue #1795).
        // Jackson serialises the graph and escapeForInlineScript handles the script context, so the
        // admin-entered value goes in as-is rather than being escaped twice.
        String siteDescription = sitePropertyMap.get("site.description");
        if (StringUtils.isNotBlank(siteDescription)) {
          organization.put("description", siteDescription);
        }

        // The postal address and founding year an administrator can now enter on Site Settings
        // (issue #1795). Every part is optional and a blank one is left out rather than emitted
        // empty, so a site that fills in only a city and country says exactly that much and no
        // more. When nothing is filled in there is no address key at all, which is why an existing
        // site's output is byte-identical until someone sets one.
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("@type", "PostalAddress");
        putIfNotBlank(address, "streetAddress", sitePropertyMap.get("site.address.street"));
        putIfNotBlank(address, "addressLocality", sitePropertyMap.get("site.address.city"));
        putIfNotBlank(address, "addressRegion", sitePropertyMap.get("site.address.state"));
        putIfNotBlank(address, "postalCode", sitePropertyMap.get("site.address.postalCode"));
        putIfNotBlank(address, "addressCountry", sitePropertyMap.get("site.address.country"));
        // Size 1 is the @type alone -- an address node saying only that it is an address is worse
        // than none, so it is not attached.
        if (address.size() > 1) {
          organization.put("address", address);
        }

        // schema.org foundingDate is an ISO 8601 date, and a bare year is one. The field is
        // labelled "Year founded" to steer that; a value that is not a date is passed through
        // rather than guessed at, the same as every other admin-entered value in this graph.
        putIfNotBlank(organization, "foundingDate", sitePropertyMap.get("site.founded"));

        String siteLogo = sitePropertyMap.get("site.image");
        if (StringUtils.isNotBlank(siteLogo)) {
          if (siteLogo.startsWith("/")) {
            organization.put("logo", siteUrl + siteLogo);
          } else {
            organization.put("logo", siteLogo);
          }
        }

        // sameAs links this Organization to its social profiles (issue #403). Passed in rather
        // than queried here -- the caller already loaded this same list once for the page's own
        // footer/socialMediaLinks-widget rendering (PageServlet.service()); re-querying it a
        // second time per request was a redundant, uncached DB round trip on every page view.
        if (socialMediaLinkList != null && !socialMediaLinkList.isEmpty()) {
          List<String> sameAs = new ArrayList<>();
          for (SocialMediaLink socialMediaLink : socialMediaLinkList) {
            if (StringUtils.isNotBlank(socialMediaLink.getUrl())) {
              sameAs.add(socialMediaLink.getUrl());
            }
          }
          if (!sameAs.isEmpty()) {
            organization.put("sameAs", sameAs);
          }
        }

        graph.add(organization);

        // A WebPage is part of a WebSite, not part of a company: schema.org's isPartOf expects a
        // CreativeWork, and Organization is not one, so pointing a page's isPartOf straight at
        // #organization is invalid and the schema.org validator rejects it. The WebSite is the
        // entity that was missing in between. It is emitted inside this same site.name guard as the
        // Organization it publishes, so the two are always present or absent together and the
        // publisher reference below can never dangle.
        Map<String, Object> webSite = new LinkedHashMap<>();
        webSite.put("@type", "WebSite");
        webSite.put("@id", siteUrl + "#website");
        webSite.put("url", siteUrl);
        webSite.put("name", sitePropertyMap.get("site.name"));
        webSite.put("publisher", Collections.singletonMap("@id", siteUrl + "#organization"));
        graph.add(webSite);
      }

      // Add WebPage schema for all pages
      Map<String, Object> webPageSchema = new LinkedHashMap<>();
      webPageSchema.put("@type", "WebPage");
      if (StringUtils.isNotBlank(pageRenderInfo.getPageUrl())) {
        webPageSchema.put("url", pageRenderInfo.getPageUrl());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getTitle())) {
        webPageSchema.put("name", pageRenderInfo.getTitle());
      }
      if (StringUtils.isNotBlank(pageRenderInfo.getDescription())) {
        webPageSchema.put("description", pageRenderInfo.getDescription());
      }
      // Guarded on the same condition that emits the WebSite above: with no site.name there is no
      // WebSite node, and an isPartOf pointing at an @id that appears nowhere in the graph is a
      // dangling reference -- a different defect from the one being fixed, not an improvement.
      if (StringUtils.isNotBlank(sitePropertyMap.get("site.name"))) {
        webPageSchema.put("isPartOf", Collections.singletonMap("@id", siteUrl + "#website"));
      }

      // Add image if available
      if (StringUtils.isNotBlank(pageRenderInfo.getImageUrl())) {
        String imageUrl = pageRenderInfo.getImageUrl();
        if (imageUrl.startsWith("/")) {
          imageUrl = siteUrl + imageUrl;
        }
        webPageSchema.put("image", imageUrl);
      }

      // dateModified/datePublished are freshness signals AI answer engines weigh for citation
      // (issue #403). datePublished prefers publishAt (the page's actual go-live date, which can
      // differ from when the row was first created via scheduled publishing) over created.
      if (webPage != null) {
        if (webPage.getModified() != null) {
          webPageSchema.put("dateModified", webPage.getModified().toInstant().toString());
        }
        Timestamp publishedDate = webPage.getPublishAt() != null ? webPage.getPublishAt() : webPage.getCreated();
        if (publishedDate != null) {
          webPageSchema.put("datePublished", publishedDate.toInstant().toString());
        }
      }

      graph.add(webPageSchema);

      // Add Article schema for blog post pages (issue #403)
      Map<String, Object> article = computeArticleSchema(pageRenderInfo, siteUrl);
      if (article != null) {
        graph.add(article);
      }

      // Add BreadcrumbList schema for pages more than one level deep (issue #403)
      List<Map<String, Object>> breadcrumbItemList = computeBreadcrumbList(siteUrl, pagePath, item, collection);
      if (breadcrumbItemList == null || breadcrumbItemList.isEmpty()) {
        // A flat URL is not a shallow page. Since issue #1728 the navigation carries three
        // levels, so a product page sits two below a tab while its URL has one segment -- the
        // URL-segment trail above cannot see that, and returned nothing for every ordinary page
        // on the site. The menu knows the position; use it when the URL does not (issue #1795).
        breadcrumbItemList = computeMenuBreadcrumbList(siteUrl, pagePath, menuTabList);
      }
      if (breadcrumbItemList != null && !breadcrumbItemList.isEmpty()) {
        Map<String, Object> breadcrumbList = new LinkedHashMap<>();
        breadcrumbList.put("@type", "BreadcrumbList");
        breadcrumbList.put("itemListElement", breadcrumbItemList);
        graph.add(breadcrumbList);
      }

      // Add FAQPage schema if this page has a FaqWidget (issue #416)
      Map<String, Object> faqPage = computeFaqSchema(pageRenderInfo);
      if (faqPage != null) {
        graph.add(faqPage);
      }

      // Add Product schema for a real ecommerce product page (issue #403); bridged from
      // pageRenderInfo the same way Article is, since a product's identity is never resolvable
      // from the URL the way an Item/Collection's is (see computeProductSchema)
      Map<String, Object> product = computeProductSchema(pageRenderInfo, siteUrl);
      if (product != null) {
        graph.add(product);
      }

      // Add Event schema for a single calendar event page (issue #1181); bridged like Product,
      // since a calendar event is not resolvable from the URL by PageServlet itself
      Map<String, Object> event = computeEventSchema(pageRenderInfo, siteUrl);
      if (event != null) {
        graph.add(event);
      }

      // Add a VideoObject for each self-hosted video the page shows (issue #1795); bridged from
      // ContentWidget, which is the only thing that knows a video is on the page -- a video is
      // hand-authored markup inside a content block, not a page-level feature
      graph.addAll(computeVideoSchemas(pageRenderInfo, siteUrl));

      jsonLd.put("@graph", graph);
      return escapeForInlineScript(mapper.writeValueAsString(jsonLd));
    } catch (Exception e) {
      LOG.warn("Error generating JSON-LD data: " + e.getMessage());
      return null;
    }
  }

  /**
   * Builds the Article schema for a blog post page (issue #403). Gated on articleHeadline since
   * that's only set by a content widget (BlogPostWidget) for a post that's actually published --
   * every other page type leaves it blank, so this doubles as the "is this a blog post" check.
   */
  static Map<String, Object> computeArticleSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    if (StringUtils.isBlank(pageRenderInfo.getArticleHeadline())) {
      return null;
    }
    Map<String, Object> article = new LinkedHashMap<>();
    // NewsArticle rather than the generic Article parent (issue #1366): this schema is only built
    // for blog posts, and Google's news surfaces look for the specific subtype. BlogPosting is the
    // other candidate -- if a site ever runs a blog that is not news (engineering notes, say), the
    // right answer is to derive this per blog rather than to fall back to the generic parent.
    article.put("@type", "NewsArticle");
    article.put("headline", pageRenderInfo.getArticleHeadline());
    if (pageRenderInfo.getArticlePublishedDate() != null) {
      article.put("datePublished", pageRenderInfo.getArticlePublishedDate().toInstant().toString());
    }
    if (pageRenderInfo.getArticleModifiedDate() != null) {
      article.put("dateModified", pageRenderInfo.getArticleModifiedDate().toInstant().toString());
    }
    if (StringUtils.isNotBlank(pageRenderInfo.getArticleAuthorName())) {
      Map<String, Object> author = new LinkedHashMap<>();
      author.put("@type", "Person");
      author.put("name", pageRenderInfo.getArticleAuthorName());
      article.put("author", author);
    }
    // Google's Article guidance treats an image as strongly recommended -- without one a post is
    // unlikely to qualify for rich results however correct the rest is. Absolutised the same way
    // the WebPage node above does it, since a relative path is not resolvable by a consumer that
    // only has the JSON-LD.
    String imageUrl = pageRenderInfo.getImageUrl();
    if (StringUtils.isNotBlank(imageUrl) && StringUtils.isNotBlank(siteUrl)) {
      article.put("image", imageUrl.startsWith("/") ? siteUrl + imageUrl : imageUrl);
    }
    // Referenced by @id rather than repeating the object -- the Organization node is already in
    // the graph, and duplicating it would let the two copies drift.
    if (StringUtils.isNotBlank(siteUrl)) {
      article.put("publisher", Collections.singletonMap("@id", siteUrl + "#organization"));
    }
    return article;
  }

  /**
   * Builds the Product schema for a real ecommerce product page (issue #403). Gated on
   * productName since that's only set by an ecommerce widget (e.g. ProductNameWidget) for a page
   * that actually has one -- every other page type leaves it blank. A single-SKU product (or one
   * where every SKU shares the same price) gets a plain Offer; a product with multiple,
   * differently-priced SKUs gets an AggregateOffer instead, since there's no one price to quote.
   */
  static Map<String, Object> computeProductSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    if (StringUtils.isBlank(pageRenderInfo.getProductName())) {
      return null;
    }
    Map<String, Object> product = new LinkedHashMap<>();
    product.put("@type", "Product");
    product.put("name", pageRenderInfo.getProductName());
    if (StringUtils.isNotBlank(pageRenderInfo.getProductDescription())) {
      product.put("description", pageRenderInfo.getProductDescription());
    }
    if (StringUtils.isNotBlank(pageRenderInfo.getProductImageUrl())) {
      String imageUrl = pageRenderInfo.getProductImageUrl();
      if (imageUrl.startsWith("/")) {
        imageUrl = siteUrl + imageUrl;
      }
      product.put("image", imageUrl);
    }

    BigDecimal price = pageRenderInfo.getProductPrice();
    BigDecimal lowPrice = pageRenderInfo.getProductLowPrice();
    if (price != null || lowPrice != null) {
      Map<String, Object> offer = new LinkedHashMap<>();
      String currency = StringUtils.isNotBlank(pageRenderInfo.getProductCurrency()) ? pageRenderInfo.getProductCurrency() : "USD";
      if (price != null) {
        offer.put("@type", "Offer");
        offer.put("price", price.stripTrailingZeros().toPlainString());
      } else {
        offer.put("@type", "AggregateOffer");
        offer.put("lowPrice", lowPrice.stripTrailingZeros().toPlainString());
        if (pageRenderInfo.getProductOfferCount() != null) {
          offer.put("offerCount", pageRenderInfo.getProductOfferCount());
        }
      }
      offer.put("priceCurrency", currency);
      if (StringUtils.isNotBlank(pageRenderInfo.getProductAvailability())) {
        offer.put("availability", pageRenderInfo.getProductAvailability());
      }
      product.put("offers", offer);
    }

    return product;
  }

  /**
   * Builds the Event schema for a single calendar event page (issue #1181). Gated on the bridged
   * CalendarEvent, which CalendarEventDetailsWidget only sets after its own calendar-enabled
   * visibility check -- so a non-null event here is already one the visitor can read. Like Product,
   * the record is bridged rather than resolved here: /calendar-event{/event-unique-id} is a
   * wildcard page and only the widget performs the uniqueId lookup.
   */
  static Map<String, Object> computeEventSchema(PageRenderInfo pageRenderInfo, String siteUrl) {
    CalendarEvent calendarEvent = pageRenderInfo.getCalendarEvent();
    if (calendarEvent == null || StringUtils.isBlank(calendarEvent.getTitle())) {
      return null;
    }

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("@type", "Event");
    event.put("name", calendarEvent.getTitle());

    if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(calendarEvent.getUniqueId())) {
      event.put("url", siteUrl + "/calendar-event/" + calendarEvent.getUniqueId());
    }

    // startDate is required by Google for Event rich results; endDate is optional but strongly
    // recommended. An all-day event is a calendar date rather than an instant, so it's emitted as
    // a bare yyyy-MM-dd resolved in the site's timezone -- rendering it as a UTC instant would
    // shift the day for any site west of Greenwich.
    String startDate = formatEventDate(calendarEvent.getStartDate(), calendarEvent.getAllDay());
    if (startDate != null) {
      event.put("startDate", startDate);
    }
    String endDate = formatEventDate(calendarEvent.getEndDate(), calendarEvent.getAllDay());
    if (endDate != null) {
      event.put("endDate", endDate);
    }

    // Prefer the curated summary; fall back to the body with markup stripped, since JSON-LD
    // description is plain text and raw HTML there is ignored at best
    String description = StringUtils.trimToNull(calendarEvent.getSummary());
    if (description == null && StringUtils.isNotBlank(calendarEvent.getBody())) {
      description = StringUtils.trimToNull(HtmlCommand.text(calendarEvent.getBody()));
    }
    if (description != null) {
      event.put("description", description);
    }

    if (StringUtils.isNotBlank(calendarEvent.getImageUrl())) {
      String imageUrl = calendarEvent.getImageUrl();
      if (imageUrl.startsWith("/") && StringUtils.isNotBlank(siteUrl)) {
        imageUrl = siteUrl + imageUrl;
      }
      event.put("image", imageUrl);
    }

    Map<String, Object> location = computeEventLocation(calendarEvent);
    if (location != null) {
      event.put("location", location);
      // Only claim an attendance mode when there's a real place backing it; asserting "offline"
      // for an event with no location at all would be inventing data
      event.put("eventAttendanceMode", "https://schema.org/OfflineEventAttendanceMode");
    }

    Map<String, Object> organizer = computeEventOrganizer(calendarEvent);
    if (organizer != null) {
      event.put("organizer", organizer);
    }

    Map<String, Object> performer = computeEventPerformer(calendarEvent);
    if (performer != null) {
      event.put("performer", performer);
    }

    Map<String, Object> offers = computeEventOffers(calendarEvent, siteUrl);
    if (offers != null) {
      event.put("offers", offers);
    }

    event.put("eventStatus", "https://schema.org/EventScheduled");

    return event;
  }

  /**
   * Builds the organizer sub-object for an Event. Returns null without a name, which is the
   * deliberate default: most events on a site like this are third-party ones the organization
   * attends, so falling back to the site owner would assert it runs conferences it merely exhibits
   * at. Search Console reports this property as missing until an editor supplies the real
   * organizer -- an absent recommended property is a suggestion, a wrong one is misleading markup.
   */
  static Map<String, Object> computeEventOrganizer(CalendarEvent calendarEvent) {
    if (StringUtils.isBlank(calendarEvent.getOrganizerName())) {
      return null;
    }
    Map<String, Object> organizer = new LinkedHashMap<>();
    organizer.put("@type", "Organization");
    organizer.put("name", calendarEvent.getOrganizerName());
    if (StringUtils.isNotBlank(calendarEvent.getOrganizerUrl())) {
      organizer.put("url", calendarEvent.getOrganizerUrl());
    }
    return organizer;
  }

  /**
   * Builds the performer sub-object for an Event, typed as a Person: schema.org accepts a Person
   * or an Organization here, and the field is labelled for a speaker in the admin form, which is
   * what an editor on this platform actually has -- someone presenting at a conference. Returns
   * null without a name rather than naming the site owner, for the same reason as the organizer.
   */
  static Map<String, Object> computeEventPerformer(CalendarEvent calendarEvent) {
    if (StringUtils.isBlank(calendarEvent.getPerformerName())) {
      return null;
    }
    Map<String, Object> performer = new LinkedHashMap<>();
    performer.put("@type", "Person");
    performer.put("name", calendarEvent.getPerformerName());
    if (StringUtils.isNotBlank(calendarEvent.getPerformerUrl())) {
      performer.put("url", calendarEvent.getPerformerUrl());
    }
    return performer;
  }

  /**
   * Builds the Offer sub-object from the event's existing sign-up URL -- no new field is needed,
   * since a registration link is exactly what Offer.url means. A site-relative link is made
   * absolute the same way the event image is.
   *
   * Deliberately carries only the URL. Offer.price, priceCurrency and availability would each be
   * an assertion this record cannot support: nothing here knows what registration costs or whether
   * it is still open, and stating "InStock" for a closed registration is worse than omitting it.
   */
  static Map<String, Object> computeEventOffers(CalendarEvent calendarEvent, String siteUrl) {
    String signUpUrl = StringUtils.trimToNull(calendarEvent.getSignUpUrl());
    if (signUpUrl == null) {
      return null;
    }
    if (signUpUrl.startsWith("/") && StringUtils.isNotBlank(siteUrl)) {
      signUpUrl = siteUrl + signUpUrl;
    }
    Map<String, Object> offer = new LinkedHashMap<>();
    offer.put("@type", "Offer");
    offer.put("url", signUpUrl);
    return offer;
  }

  /**
   * Formats a calendar event date for schema.org: a bare calendar date for an all-day event
   * (resolved in the site timezone) and a full ISO-8601 instant otherwise. Returns null for a
   * missing date so the caller can omit the property rather than emit an empty one.
   */
  static String formatEventDate(Timestamp timestamp, boolean allDay) {
    if (timestamp == null) {
      return null;
    }
    if (allDay) {
      return timestamp.toInstant().atZone(FormatDateCommand.getSiteZoneId()).toLocalDate().toString();
    }
    return timestamp.toInstant().toString();
  }

  /**
   * Builds the Place sub-object for an Event (issue #1181). Returns null when the record carries
   * neither a location name nor any address line, since a Place with no identifying detail adds
   * nothing and Google treats an empty location as a validation error.
   */
  static Map<String, Object> computeEventLocation(CalendarEvent calendarEvent) {
    boolean hasAddress = StringUtils.isNotBlank(calendarEvent.getStreet())
        || StringUtils.isNotBlank(calendarEvent.getCity())
        || StringUtils.isNotBlank(calendarEvent.getState())
        || StringUtils.isNotBlank(calendarEvent.getPostalCode())
        || StringUtils.isNotBlank(calendarEvent.getCountry());
    if (StringUtils.isBlank(calendarEvent.getLocation()) && !hasAddress) {
      return null;
    }

    Map<String, Object> place = new LinkedHashMap<>();
    place.put("@type", "Place");
    if (StringUtils.isNotBlank(calendarEvent.getLocation())) {
      place.put("name", calendarEvent.getLocation());
    }

    if (hasAddress) {
      Map<String, Object> address = new LinkedHashMap<>();
      address.put("@type", "PostalAddress");
      if (StringUtils.isNotBlank(calendarEvent.getStreet())) {
        address.put("streetAddress", calendarEvent.getStreet());
      }
      if (StringUtils.isNotBlank(calendarEvent.getCity())) {
        address.put("addressLocality", calendarEvent.getCity());
      }
      if (StringUtils.isNotBlank(calendarEvent.getState())) {
        address.put("addressRegion", calendarEvent.getState());
      }
      if (StringUtils.isNotBlank(calendarEvent.getPostalCode())) {
        address.put("postalCode", calendarEvent.getPostalCode());
      }
      if (StringUtils.isNotBlank(calendarEvent.getCountry())) {
        address.put("addressCountry", calendarEvent.getCountry());
      }
      place.put("address", address);
    }

    // 0.0/0.0 is the model's default for "never geocoded", not a real point in the Atlantic
    if (calendarEvent.getLatitude() != 0.0 || calendarEvent.getLongitude() != 0.0) {
      Map<String, Object> geo = new LinkedHashMap<>();
      geo.put("@type", "GeoCoordinates");
      geo.put("latitude", calendarEvent.getLatitude());
      geo.put("longitude", calendarEvent.getLongitude());
      place.put("geo", geo);
    }

    return place;
  }

  /**
   * A VideoObject for every self-hosted video the page reported, in the order they appear (issue
   * #1795).
   *
   * <p>
   * Google's required properties are name, thumbnailUrl and uploadDate; description and contentUrl
   * are recommended, and contentUrl is what lets Google fetch the video itself rather than take the
   * page's word for it. ContentVideoCommand has already dropped anything it could not describe
   * completely, so what arrives here is emittable -- the checks below are the same belt-and-braces
   * the other compute methods apply, not a second gate.
   * </p>
   *
   * <p>
   * uploadDate is the file's upload time, which is the honest answer to the property's question and
   * is emitted as a full ISO 8601 instant, matching datePublished/dateModified on the WebPage node.
   * Only videos this page actually renders are described: nothing walks the file library looking
   * for videos to claim.
   * </p>
   *
   * @return the video nodes, empty when the page shows none; never null
   */
  static List<Map<String, Object>> computeVideoSchemas(PageRenderInfo pageRenderInfo, String siteUrl) {
    List<Map<String, Object>> videoSchemaList = new ArrayList<>();
    if (pageRenderInfo == null || pageRenderInfo.getVideos() == null) {
      return videoSchemaList;
    }
    Set<String> alreadyDescribed = new HashSet<>();
    for (PageVideo pageVideo : pageRenderInfo.getVideos()) {
      if (StringUtils.isBlank(pageVideo.getName()) || StringUtils.isBlank(pageVideo.getThumbnailUrl())
          || pageVideo.getUploadDate() == null) {
        continue;
      }
      // One video shown twice on a page -- in a panel and again in the modal it opens, say -- is
      // still one video. Two nodes for it would tell Google the page has two.
      String identity = pageVideo.getContentUrl() != null
          ? pageVideo.getContentUrl()
          : pageVideo.getName() + "|" + pageVideo.getThumbnailUrl();
      if (!alreadyDescribed.add(identity)) {
        continue;
      }
      Map<String, Object> video = new LinkedHashMap<>();
      video.put("@type", "VideoObject");
      video.put("name", pageVideo.getName());
      putIfNotBlank(video, "description", pageVideo.getDescription());
      video.put("thumbnailUrl", absoluteUrl(siteUrl, pageVideo.getThumbnailUrl()));
      video.put("uploadDate", pageVideo.getUploadDate().toInstant().toString());
      putIfNotBlank(video, "contentUrl", absoluteUrl(siteUrl, pageVideo.getContentUrl()));
      putIfNotBlank(video, "encodingFormat", pageVideo.getEncodingFormat());
      videoSchemaList.add(video);
    }
    return videoSchemaList;
  }

  /**
   * Site-relative paths made absolute for the graph, the way the logo and the page image already
   * are. A value that is already absolute, or a blank one, is returned untouched -- content can
   * legitimately point at a video hosted elsewhere, and prefixing that would produce a URL that
   * resolves nowhere.
   */
  private static String absoluteUrl(String siteUrl, String url) {
    if (StringUtils.isBlank(url) || !url.startsWith("/") || StringUtils.isBlank(siteUrl)) {
      return url;
    }
    return siteUrl + url;
  }

  /**
   * Builds the BreadcrumbList itemListElement array for pages at a URL depth of two or more
   * (issue #403); shallower pages return null since a single-level trail is redundant with the
   * site nav. Each ancestor segment's name is resolved the same way the page itself would be
   * resolved (LoadWebPageCommand, including wildcard/template pages) so a breadcrumb never shows
   * a path segment that the app wouldn't actually route to; a segment with no matching page falls
   * back to a humanized version of the URL segment rather than leaving a gap in the trail.
   */
  static List<Map<String, Object>> computeBreadcrumbList(String siteUrl, String pagePath, Item item, Collection collection) {
    if (StringUtils.isBlank(siteUrl) || StringUtils.isBlank(pagePath)) {
      return null;
    }
    List<String> segments = new ArrayList<>();
    for (String segment : pagePath.split("/")) {
      if (StringUtils.isNotBlank(segment)) {
        segments.add(segment);
      }
    }
    if (segments.size() < 2) {
      return null;
    }

    List<Map<String, Object>> itemListElement = new ArrayList<>();
    itemListElement.add(breadcrumbListItem(1, "Home", siteUrl));

    StringBuilder pathSoFar = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      pathSoFar.append('/').append(segment);
      boolean isLeaf = (i == segments.size() - 1);

      String name = null;
      if (isLeaf && item != null && StringUtils.isNotBlank(item.getName())) {
        name = item.getName();
      } else if (collection != null && segment.equalsIgnoreCase(collection.getUniqueId())) {
        // The collection's own segment, whether it's the leaf (collection listing page) or an
        // ancestor of the leaf (an item detail page nested under it)
        name = collection.getName();
      }
      if (StringUtils.isBlank(name)) {
        WebPage segmentPage = LoadWebPageCommand.loadByLink(pathSoFar.toString());
        if (segmentPage != null && StringUtils.isNotBlank(segmentPage.getTitle())) {
          name = segmentPage.getTitle();
        }
      }
      if (StringUtils.isBlank(name)) {
        name = humanizeUrlSegment(segment);
      }

      itemListElement.add(breadcrumbListItem(i + 2, name, siteUrl + pathSoFar));
    }
    return itemListElement;
  }

  /**
   * Builds the BreadcrumbList trail from the navigation menu, for a page whose URL cannot supply one
   * (issue #1795).
   *
   * <p>{@link #computeBreadcrumbList} derives the trail from URL path segments and returns null below
   * two of them. That held while the menu was two levels and a flat URL usually meant a shallow page.
   * Since issue #1728 the navigation carries three, so a page can sit two levels below a tab and
   * still have a one-segment URL -- and every ordinary page on a site therefore emitted no breadcrumb
   * at all.
   *
   * <p>Position in the menu is the trail: tab, then item, then sub-item. A page that IS a tab gets
   * none, matching the existing rule that a single-level trail is redundant with the nav itself.
   *
   * <p>The first match wins. A page linked from more than one place in the menu has no one true
   * trail, and picking the first is both stable and the same answer the nav's own highlighting gives.
   */
  static List<Map<String, Object>> computeMenuBreadcrumbList(String siteUrl, String pagePath,
      List<MenuTab> menuTabList) {
    if (StringUtils.isBlank(siteUrl) || StringUtils.isBlank(pagePath) || menuTabList == null) {
      return null;
    }
    for (MenuTab menuTab : menuTabList) {
      if (menuTab.getMenuItemList() == null) {
        continue;
      }
      for (MenuItem menuItem : menuTab.getMenuItemList()) {
        if (pagePath.equals(menuItem.getLink())) {
          return menuTrail(siteUrl, menuTab, menuItem, null);
        }
        if (menuItem.getMenuItemList() == null) {
          continue;
        }
        for (MenuItem subMenuItem : menuItem.getMenuItemList()) {
          if (pagePath.equals(subMenuItem.getLink())) {
            return menuTrail(siteUrl, menuTab, menuItem, subMenuItem);
          }
        }
      }
    }
    return null;
  }

  /**
   * Home, then each ancestor that is a distinct destination, then the page. A tab whose link is the
   * same page as the item beneath it is skipped rather than repeated -- a trail that lists the same
   * URL twice is worse than a shorter one.
   */
  private static List<Map<String, Object>> menuTrail(String siteUrl, MenuTab menuTab, MenuItem menuItem,
      MenuItem subMenuItem) {
    List<Map<String, Object>> itemListElement = new ArrayList<>();
    itemListElement.add(breadcrumbListItem(1, "Home", siteUrl));
    int position = 2;
    String lastLink = null;
    if (StringUtils.isNotBlank(menuTab.getLink()) && !"/".equals(menuTab.getLink())) {
      itemListElement.add(breadcrumbListItem(position++, menuTab.getName(), siteUrl + menuTab.getLink()));
      lastLink = menuTab.getLink();
    }
    if (StringUtils.isNotBlank(menuItem.getLink()) && !menuItem.getLink().equals(lastLink)) {
      itemListElement.add(breadcrumbListItem(position++, menuItem.getName(), siteUrl + menuItem.getLink()));
      lastLink = menuItem.getLink();
    }
    if (subMenuItem != null && StringUtils.isNotBlank(subMenuItem.getLink())
        && !subMenuItem.getLink().equals(lastLink)) {
      itemListElement.add(breadcrumbListItem(position, subMenuItem.getName(), siteUrl + subMenuItem.getLink()));
    }
    // Home plus one is a single-level trail, which computeBreadcrumbList already treats as redundant
    // with the nav; emitting it here would contradict that for no gain.
    return itemListElement.size() >= 3 ? itemListElement : null;
  }

  /** Puts the value only when it has one, so an unset setting leaves the key off the node entirely. */
  private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
    if (StringUtils.isNotBlank(value)) {
      target.put(key, value);
    }
  }

  private static Map<String, Object> breadcrumbListItem(int position, String name, String url) {
    Map<String, Object> listItem = new LinkedHashMap<>();
    listItem.put("@type", "ListItem");
    listItem.put("position", position);
    listItem.put("name", name);
    listItem.put("item", url);
    return listItem;
  }

  /**
   * Builds the FAQPage schema for a page with one or more FaqWidgets (issue #416). Uses
   * FaqQuestion's pre-stripped answerText, not the widget's own rendered HTML, since Google's FAQ
   * rich result requires the acceptedAnswer text to contain no markup.
   */
  static Map<String, Object> computeFaqSchema(PageRenderInfo pageRenderInfo) {
    List<FaqQuestion> faqQuestionList = pageRenderInfo.getFaqQuestions();
    if (faqQuestionList == null || faqQuestionList.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> mainEntity = new ArrayList<>();
    for (FaqQuestion faqQuestion : faqQuestionList) {
      Map<String, Object> question = new LinkedHashMap<>();
      question.put("@type", "Question");
      question.put("name", faqQuestion.getQuestion());
      Map<String, Object> acceptedAnswer = new LinkedHashMap<>();
      acceptedAnswer.put("@type", "Answer");
      acceptedAnswer.put("text", faqQuestion.getAnswerText());
      question.put("acceptedAnswer", acceptedAnswer);
      mainEntity.add(question);
    }
    Map<String, Object> faqPage = new LinkedHashMap<>();
    faqPage.put("@type", "FAQPage");
    faqPage.put("mainEntity", mainEntity);
    return faqPage;
  }

  /**
   * Turns a URL segment like "getting-started" into "Getting Started" for use as a breadcrumb
   * label when no page title is available to describe that part of the path.
   */
  static String humanizeUrlSegment(String segment) {
    String decoded;
    try {
      decoded = java.net.URLDecoder.decode(segment, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      decoded = segment;
    }
    String[] words = decoded.replace('-', ' ').replace('_', ' ').split(" ");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append(' ');
      }
      result.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        result.append(word.substring(1));
      }
    }
    return result.length() > 0 ? result.toString() : segment;
  }

  /**
   * Jackson's JSON escaping only guarantees syntactically valid JSON (quotes, backslashes,
   * control characters) -- it has no notion of the surrounding HTML, so a value containing
   * {@code "</script>"} passes straight through. The browser's HTML parser looks for that literal
   * byte sequence regardless of JSON string context, so an unescaped {@code "</script>"} inside
   * e.g. a product name closes the tag early and lets an attacker-controlled payload execute.
   * Escaping every '<', '>' and '&' to its JSON \\uXXXX form (valid inside a JSON string, and
   * decodes back to the original character on parse) neutralizes that and any other HTML/comment
   * breakout, without changing the parsed JSON-LD content.
   */
  static String escapeForInlineScript(String json) {
    if (json == null) {
      return null;
    }
    return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
  }
}
