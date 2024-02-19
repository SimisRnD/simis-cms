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
<jsp:useBean id="showAuthor" class="java.lang.String" scope="request"/>
<%--<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>--%>
<jsp:useBean id="showImage" class="java.lang.String" scope="request"/>
<jsp:useBean id="showTags" class="java.lang.String" scope="request"/>
<jsp:useBean id="showSummary" class="java.lang.String" scope="request"/>
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<jsp:useBean id="readMoreText" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <div class="grid-container">
      <div class="grid-x grid-padding-x align-center small-up-<c:out value="${smallCardCount}" /> medium-up-<c:out value="${mediumCardCount}" /> large-up-<c:out value="${largeCardCount}" />">
        <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
          <div class="cell">
            <div class="card<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
              <c:if test="${showImage eq 'true' && !empty blogPost.imageUrl}">
                <div class="card-image">
                  <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}"><img alt="Blog post banner image" src="${ctx}${blogPost.imageUrl}"/></a>
                </div>
              </c:if>
              <c:if test="${showTags eq 'true' && !empty blogPost.tagsList}">
                <div class="card-section blog-tags">
                  <h5>
                    <c:forEach items="${blogPost.tagsList}" var="tag">
                      <span class="label secondary"><c:out value="${tag}" /></span>
                    </c:forEach>
                  </h5>
                </div>
              </c:if>
              <div class="card-section blog-title">
                <h3><a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a></h3>
              </div>
              <c:if test="${showAuthor eq 'true'}">
                <div class="card-section blog-author">
                  By <c:out value="${user:name(blogPost.createdBy)}" />
                </div>
              </c:if>
              <c:if test="${showSummary eq 'true'}">
                <div class="card-section blog-intro">
                  <c:choose>
                    <c:when test="${!empty blogPost.summary}">
                      <p>${html:toHtml(text:trim(html:text(blogPost.summary), 256, true))}</p>
                    </c:when>
                    <c:otherwise>
                      <p>${html:toHtml(text:trim(html:text(blogPost.body), 220, true))}</p>
                    </c:otherwise>
                  </c:choose>
                </div>
              </c:if>
              <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
                <div class="card-section blog-date">
                    <c:out value="${date:formatMonthDayYear(blogPost.startDate)}" />
                </div>
              </c:if>
              <c:if test="${showReadMore eq 'true'}">
                <div class="card-section">
                  <div class="blog-read-more">
                    <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}" class="button"><c:out value="${readMoreText}" /></a>
                  </div>
                </div>
              </c:if>
            </div>
          </div>
        </c:forEach>
      </div>
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
