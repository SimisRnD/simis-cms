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
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPostList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showAuthor" class="java.lang.String" scope="request"/>
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<jsp:useBean id="showTags" class="java.lang.String" scope="request"/>
<jsp:useBean id="showSummary" class="java.lang.String" scope="request"/>
<jsp:useBean id="viewAllUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="viewAllText" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <ul class="no-bullet platform-blog-panel">
      <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
        <li class="platform-blog-panel-item">
          <h5 class="platform-blog-panel-title">
            <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a>
          </h5>
          <c:if test="${showAuthor eq 'true' || (showDate eq 'true' && !empty blogPost.startDate)}">
            <p class="platform-blog-panel-byline">
              <c:if test="${showAuthor eq 'true'}">
                <small><i class="fa fa-pencil"></i> <c:out value="${user:name(blogPost.createdBy)}"/></small>
              </c:if>
              <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
                <small><i class="fa fa-clock-o"></i> <c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/></small>
              </c:if>
            </p>
          </c:if>
          <c:if test="${showSummary eq 'true'}">
            <p class="platform-blog-panel-summary">
              <c:choose>
                <c:when test="${!empty blogPost.summary}">
                  <c:out value="${text:trim(blogPost.summary, 140, true)}"/>
                </c:when>
                <c:otherwise>
                  ${html:toHtml(text:trim(html:text(blogPost.body), 140, true))}
                </c:otherwise>
              </c:choose>
            </p>
          </c:if>
          <c:if test="${showTags eq 'true' && !empty blogPost.tagsList}">
            <p class="platform-blog-panel-tags">
              <c:forEach items="${blogPost.tagsList}" var="tag">
                <span class="label secondary"><c:out value="${tag}"/></span>
              </c:forEach>
            </p>
          </c:if>
        </li>
      </c:forEach>
    </ul>
    <c:if test="${!empty viewAllUrl}">
      <a href="${fn:escapeXml(viewAllUrl)}" class="platform-blog-panel-view-all"><c:out value="${viewAllText}"/> &rarr;</a>
    </c:if>
  </c:when>
  <c:otherwise>
    <p class="subheader">No posts were found</p>
  </c:otherwise>
</c:choose>
