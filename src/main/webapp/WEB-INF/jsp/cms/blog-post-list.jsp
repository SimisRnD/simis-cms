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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPostList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<jsp:useBean id="sortBy" class="java.lang.String" scope="request"/>
<jsp:useBean id="sortOrder" class="java.lang.String" scope="request"/>
<jsp:useBean id="showAuthor" class="java.lang.String" scope="request"/>
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<jsp:useBean id="addDateToTitle" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <div class="platform-blog-list-container">
      <c:if test="${showSort eq 'true'}">
        <div class="platform-sort-options">
          <ul class="dropdown menu" data-dropdown-menu>
            <li class="has-submenu">
              <a href="#1">Sort by Date</a>
              <ul class="submenu menu vertical" data-submenu>
                <c:choose>
                  <c:when test="${sortBy eq 'date' && sortOrder eq 'newest'}">
                    <li><a href="?sortBy=date&sortOrder=newest"><i class="${font:fal()} fa-fw fa-check"></i> Newest</a></li>
                    <li><a href="?sortBy=date&sortOrder=oldest"><i class="${font:fal()} fa-fw"></i> Oldest</a></li>
                  </c:when>
                  <c:when test="${sortBy eq 'date' && sortOrder eq 'oldest'}">
                    <li><a href="?sortBy=date&sortOrder=newest"><i class="${font:fal()} fa-fw"></i> Newest</a></li>
                    <li><a href="?sortBy=date&sortOrder=oldest"><i class="${font:fal()} fa-fw fa-check"></i> Oldest</a></li>
                  </c:when>
                  <c:otherwise>
                    <li><a href="?sortBy=date&sortOrder=newest">Newest</a></li>
                    <li><a href="?sortBy=date&sortOrder=oldest">Oldest</a></li>
                  </c:otherwise>
                </c:choose>
              </ul>
            </li>
          </ul>
        </div>
      </c:if>
      <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
        <div class="platform-blog-list-item">
          <div class="platform-blog-title">
            <h3>
              <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a>
              <c:if test="${!empty blogPost.city}">
                <span class="platform-blog-city"><c:out value="${blogPost.city}"/></span>
              </c:if>
              <c:if test="${addDateToTitle eq 'true' && !empty blogPost.startDate}">
                <span class="platform-blog-date"><c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/></span>
              </c:if>
              <c:if test="${empty blogPost.published}"><span class="label warning">not published</span></c:if>
              <c:if test="${date:isAfterNow(blogPost.startDate)}"><span class="label success">Set to display <c:out value="${date:relative(blogPost.startDate)}" /></span></c:if>
            </h3>
          </div>
          <c:if test="${!empty blogPost.imageUrl}">
            <div class="platform-blog-image">
              <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}"><img alt="Blog post banner image" src="${ctx}${blogPost.imageUrl}"/></a>
            </div>
          </c:if>
          <c:if test="${showAuthor eq 'true' || (showDate eq 'true' && !empty blogPost.startDate) || !empty blogPost.tagsList}">
            <div class="platform-blog-byline">
              <div class="grid-x grid-margin-x">
                <c:if test="${showAuthor eq 'true'}">
                  <div class="cell shrink">
                    <small>
                      <i class="fa fa-pencil"></i>
                      <c:out value="${user:name(blogPost.createdBy)}"/>
                    </small>
                  </div>
                </c:if>
                <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
                  <div class="cell shrink">
                    <small>
                      <i class="fa fa-clock-o"></i>
                      <c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/>
                    </small>
                  </div>
                </c:if>
                <c:if test="${!empty blogPost.tagsList}">
                  <div class="cell auto">
                    <small>
                      <i class="fa fa-tag"></i>
                      <c:forEach items="${blogPost.tagsList}" var="tag">
                        <span class="label secondary"><c:out value="${tag}"/></span>
                      </c:forEach>
                    </small>
                  </div>
                </c:if>
              </div>
            </div>
          </c:if>
          <c:if test="${showReadMore eq 'true'}">
            <div class="platform-blog-body">
              <div class="grid-x grid-margin-x">
                <div class="small-12 cell">
                  ${html:toHtml(text:trim(html:text(blogPost.body), 220, true))}
                  <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}" class="read-more"><c:out value="${readMoreText}" /></a>
                </div>
              </div>
            </div>
          </c:if>
        </div>
        <c:if test="${!status.last}"><hr /></c:if>
      </c:forEach>
    </div>
    <%-- Paging Control --%>
    <%@include file="../paging_control.jspf" %>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No posts were found
    </p>
  </c:otherwise>
</c:choose>
