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
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<jsp:useBean id="contentHtml" class="java.lang.String" scope="request"/>
<h4>Wiki Has Not Been Setup</h4>
<hr />
<div class="platform-content-container">
  <div class="platform-content">
    <%-- The old text -- "The administrator needs to setup the wiki so that content can be created" --
         gave an administrator reading it nothing to act on. It appears in two different situations
         that need different things done about them, and it never said which one this was, so a site
         with a perfectly good wiki that simply was not selected looked identical to one with no wiki
         at all. --%>
    <c:choose>
      <c:when test="${wikiSetupIssue eq 'none-selected'}">
        <p>
          No wiki has been selected for this page yet. If a wiki already exists, choose it on the
          <a href="${ctx}/admin/wikis">Wikis</a> page &mdash; the setting is
          <strong><c:out value="${wikiSetupProperty}"/></strong>, at the bottom of that page.
        </p>
        <p>
          If no wiki exists yet, create one there first, then come back and select it.
        </p>
      </c:when>
      <c:when test="${wikiSetupIssue eq 'not-found'}">
        <p>
          This page is set to show the wiki <strong><c:out value="${wikiSetupUniqueId}"/></strong>,
          but no wiki with that unique id exists. It may have been renamed or deleted.
        </p>
        <p>
          Check the list on the <a href="${ctx}/admin/wikis">Wikis</a> page<c:if test="${!empty wikiSetupProperty}">
          and correct the <strong><c:out value="${wikiSetupProperty}"/></strong> setting there</c:if>.
        </p>
      </c:when>
      <c:otherwise>
        <p>
          The administrator needs to set up the wiki so that content can be created. Start on the
          <a href="${ctx}/admin/wikis">Wikis</a> page.
        </p>
      </c:otherwise>
    </c:choose>
  </div>
</div>
