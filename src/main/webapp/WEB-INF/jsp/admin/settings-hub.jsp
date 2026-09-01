<%--
  ~ Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<jsp:useBean id="settingsGroupList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<p class="help-text page-help">Every settings screen, grouped, with a line on what each one holds.
  Settings for an optional module stay listed here even when the module is switched off -- each
  module's own settings page is where it gets switched back on.</p>
<c:forEach items="${settingsGroupList}" var="settingsGroup">
  <div class="platform-settings-group">
    <h3 class="platform-settings-group-title"><c:out value="${settingsGroup.title}"/></h3>
    <c:if test="${!empty settingsGroup.description}">
      <p class="help-text platform-settings-group-description"><c:out value="${settingsGroup.description}"/></p>
    </c:if>
    <div class="grid-x grid-margin-x grid-margin-y small-up-1 medium-up-2 large-up-3">
      <c:forEach items="${settingsGroup.entryList}" var="settingsEntry">
        <div class="cell">
          <a class="platform-settings-card" href="${ctx}<c:out value="${settingsEntry.link}"/>">
            <span class="platform-settings-card-heading">
              <i class="${font:far()} <c:out value="${settingsEntry.icon}"/> fa-fw" aria-hidden="true"></i>
              <span class="platform-settings-card-label"><c:out value="${settingsEntry.label}"/></span>
              <%-- Says why the menu row is gone, rather than leaving the admin to wonder (issue #1763) --%>
              <c:if test="${settingsEntry.belongsToModule && !settingsEntry.moduleEnabled}">
                <span class="label secondary platform-settings-card-state">Module off</span>
              </c:if>
            </span>
            <span class="platform-settings-card-description"><c:out value="${settingsEntry.description}"/></span>
          </a>
        </div>
      </c:forEach>
    </div>
  </div>
</c:forEach>
