<%--
  ~ Copyright 2026 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<p>
  This site includes several features that help search engines and AI answer engines (ChatGPT,
  Claude, Perplexity, and similar) find, understand, and correctly cite this site's content. Some
  of these work automatically; others can be configured. This page is an index of both -- each
  setting lives on the admin page that already owns it, not here.
</p>

<h5>Works automatically, nothing to configure</h5>
<ul>
  <li>
    <strong>Canonical URLs</strong> &mdash; every public page declares its own canonical address,
    so search engines don't split ranking credit across duplicate ways of reaching the same page
    (a trailing slash, an alias, etc.).
  </li>
  <li>
    <strong>Open Graph tags</strong> &mdash; pages get the meta tags that control how links look
    when shared on social media and in chat apps (title, description, preview image).
  </li>
  <li>
    <strong>Structured data (JSON-LD)</strong> &mdash; pages include machine-readable data that
    helps answer engines cite this site accurately: an Organization profile on every page, a
    WebPage entry with real last-modified/published dates, a breadcrumb trail on nested pages,
    Article/Author data on blog posts, and Product data (price, availability) on catalog pages.
    Some of this data is pulled from other settings -- see Social Media below.
  </li>
</ul>

<h5>Configurable, from their own admin pages</h5>
<ul>
  <li>
    <strong><a href="${ctx}/admin/robots-properties">Robots &amp; Crawlers</a></strong> &mdash;
    control which automated crawlers may access the site, including separately allowing or
    blocking each AI vendor's <em>training</em> crawler (e.g. GPTBot) independently from its
    <em>citation/answer</em> crawler (e.g. OAI-SearchBot) -- these are different bots with
    different jobs, even from the same vendor.
  </li>
  <li>
    <strong><a href="${ctx}/admin/social-media-settings">Social Media</a></strong> &mdash; the
    profile links entered here also feed the Organization structured-data block above, so answer
    engines can connect this site to its official social accounts.
  </li>
  <li>
    <strong><a href="${ctx}/admin/web-pages">Pages</a></strong> &mdash; each page's own edit form
    has a "Show in Sitemap.xml?" toggle plus a change-frequency and priority hint, controlling
    whether and how that page is listed in sitemap.xml. The
    <a href="${ctx}/admin/seo-sitemap">SEO Sitemap</a> page manages that same setting in bulk,
    across every page at once, and links to a live preview of the generated sitemap.xml.
  </li>
  <li>
    <strong>FAQ content</strong> &mdash; a page built with the FAQ widget automatically gets
    FAQPage structured data for its questions and answers, a format several answer engines use
    directly when responding to how-to and Q&amp;A style queries.
  </li>
  <li>
    <strong><a href="${ctx}/admin">Dashboard search analytics</a></strong> &mdash; the site tracks
    what visitors search for, including zero-result queries (content gaps worth filling) and
    trending terms, shown as tiles on the main admin dashboard.
  </li>
</ul>
