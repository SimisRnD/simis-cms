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
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<jsp:useBean id="showImage" class="java.lang.String" scope="request"/>
<jsp:useBean id="showTags" class="java.lang.String" scope="request"/>
<jsp:useBean id="readMoreText" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/masonry-4.2.2/masonry.pkgd.min.js"></script>
<script src="${ctx}/javascript/imagesloaded-5.0.0/imagesloaded.pkgd.min.js"></script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <div class="grid-container platform-blog-cards-container">
      <div class="grid-x grid-margin-x small-up-1 medium-up-2 large-up-3" id="masonry-container${widgetContext.uniqueId}">
        <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
          <div class="cell">
            <div class="card">
              <div class="card-section blog-title">
                <h2><a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a></h2>
              </div>
              <c:if test="${showAuthor eq 'true' || showTags eq 'true'}">
                <div class="card-section blog-info">
                  <%--
                  <c:if test="${showTags eq 'true'}">
                    <div class="float-right blog-tags">
                      Category
                    </div>
                  </c:if>
                  --%>
                  <c:if test="${showAuthor eq 'true'}">
                    <div class="blog-author">
                      By <c:out value="${user:name(blogPost.createdBy)}"/>
                    </div>
                  </c:if>
                </div>
              </c:if>
              <c:if test="${showImage eq 'true' && !empty blogPost.imageUrl}">
                <div class="card-image">
                  <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}"><img alt="Blog post banner image" src="${ctx}${blogPost.imageUrl}"/></a>
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
              <div class="card-section-bleed">
                <div class="float-right blog-read-more">
                  <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}" class="read-more"><c:out value="${readMoreText}" /></a>
                </div>
                <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
                  <div class="blog-date">
                    <fmt:formatDate pattern="MM/dd/yy" value="${blogPost.startDate}" />
                  </div>
                </c:if>
              </div>
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
<script>
  var $grid = $('#masonry-container${widgetContext.uniqueId}').imagesLoaded( function() {
    $grid.masonry({
      itemSelector: '.cell'
    });
  });
</script>
