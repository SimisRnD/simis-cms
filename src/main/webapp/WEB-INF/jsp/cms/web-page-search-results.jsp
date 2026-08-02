<%--
  ~ Copyright 2022 SimIS Inc.
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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="activeFilters" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4 class="margin-bottom-20"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${!empty activeFilters}">
  <div class="margin-bottom-10">
    <c:forEach items="${activeFilters}" var="activeFilter">
      <span class="label secondary" style="margin-right:5px">
        <c:out value="${activeFilter.facetLabel}"/>: <c:out value="${activeFilter.valueLabel}"/>
        <%-- clearUrl is server-built from the request path + UrlCommand.encodeUri()'d params, so it cannot carry HTML metacharacters --%>
        <a href="${activeFilter.clearUrl}" style="color:inherit" title="Remove this filter"><i class="fa fa-times"></i></a>
      </span>
    </c:forEach>
  </div>
</c:if>
<div class="grid-x grid-margin-x">
  <c:if test="${!empty dateFacets}">
    <div class="cell medium-3">
      <h6><c:out value="${dateFacetLabel}"/></h6>
      <ul class="no-bullet" style="text-indent: -11px; margin-left: 21px !important;">
        <c:forEach items="${dateFacets}" var="facet">
          <li>
            <%-- facet.url is server-built from the request path + UrlCommand.encodeUri()'d params, so it cannot carry HTML metacharacters --%>
            <a href="${facet.url}">
              <c:choose>
                <c:when test="${facet.selected}"><i class="fa fa-circle-check"></i></c:when>
                <c:otherwise><i class="fa fa-circle-o"></i></c:otherwise>
              </c:choose>
              <c:out value="${facet.label}"/>
            </a>&nbsp;<small class="subheader"><fmt:formatNumber value="${facet.count}"/></small>
          </li>
        </c:forEach>
      </ul>
    </div>
  </c:if>
  <div class="cell ${!empty dateFacets ? 'medium-9' : 'medium-12'}">
    <c:choose>
      <c:when test="${empty searchResultList}">
        <c:choose>
          <c:when test="${!empty activeFilters}">
            <p>No pages match the current filters.</p>
            <ul class="no-bullet">
              <c:forEach items="${activeFilters}" var="activeFilter">
                <li><a href="${activeFilter.clearUrl}">Remove "<c:out value="${activeFilter.valueLabel}"/>"</a></li>
              </c:forEach>
            </ul>
          </c:when>
          <c:otherwise>
            <p>No pages were found</p>
          </c:otherwise>
        </c:choose>
      </c:when>
      <c:otherwise>
        <c:forEach items="${searchResultList}" var="searchResult" varStatus="status">
          <div class="platform-content-search-result margin-top-10">
            <c:choose>
              <c:when test="${!empty searchResult.pageTitle}">
                <h5><a href="${ctx}${searchResult.link}"><c:out value="${searchResult.pageTitle}"/></a></h5>
              </c:when>
              <c:otherwise>
                <h5><a href="${ctx}${searchResult.link}"><c:out value="${searchResult.link}"/></a></h5>
              </c:otherwise>
            </c:choose>
            <c:if test="${!empty searchResult.pageDescription}">
              <p><c:out value="${searchResult.pageDescription}" /></p>
            </c:if>
            <p>${searchResult.htmlExcerpt}</p>
          </div>
        </c:forEach>
      </c:otherwise>
    </c:choose>
  </div>
</div>
