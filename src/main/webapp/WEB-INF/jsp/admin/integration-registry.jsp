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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="integrationCardList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<p class="help-text page-help">Browse and install third-party integrations. Installing saves the
  credential(s) below and, for a webhook-based integration, creates a subscription so it starts
  receiving the events you select. <a href="${ctx}/admin/integrations/secrets">View secret
  rotation &amp; expiry audit &rarr;</a></p>
<%@include file="../page_messages.jspf" %>

<div class="grid-x grid-margin-x">
  <c:forEach items="${integrationCardList}" var="card">
    <div class="small-12 medium-6 large-4 cell">
      <div class="callout radius" style="height: 100%;">
        <h5><i class="fa ${fn:escapeXml(card.definition.iconClass)}"></i> <c:out value="${card.definition.name}" />
          <c:choose>
            <c:when test="${card.installed}"><span class="label success">Installed</span></c:when>
            <c:otherwise><span class="label secondary">Not installed</span></c:otherwise>
          </c:choose>
        </h5>
        <p><c:out value="${card.definition.description}" /></p>
        <p class="help-text">
          <a href="${fn:escapeXml(card.definition.websiteUrl)}" target="_blank" rel="noopener noreferrer">Website</a>
          <c:if test="${!empty card.definition.docsUrl}">
            &middot; <a href="${fn:escapeXml(card.definition.docsUrl)}" target="_blank" rel="noopener noreferrer">Setup docs</a>
          </c:if>
        </p>

        <c:choose>
          <c:when test="${card.installed}">
            <p>
              <c:if test="${!empty card.manageUrl}">
                <a class="button radius secondary" href="${ctx}<c:out value="${card.manageUrl}"/>">Manage</a>
              </c:if>
              <a href="#" class="button radius alert"
                 data-confirm-post="Uninstall ${fn:escapeXml(card.definition.name)}? This removes its saved credential and any webhook subscription it created." data-post-url="${widgetContext.uri}?action=uninstall&amp;widget=${widgetContext.uniqueId}&amp;token=${userSession.formToken}&amp;integrationId=${fn:escapeXml(card.definition.id)}">Uninstall</a>
            </p>
          </c:when>
          <c:when test="${installingId eq card.definition.id}">
            <form method="post" autocomplete="off">
              <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
              <input type="hidden" name="token" value="${userSession.formToken}"/>
              <input type="hidden" name="integrationId" value="<c:out value="${card.definition.id}"/>"/>
              <c:forEach items="${card.definition.credentialFields}" var="field">
                <label for="cred_<c:out value="${field.name}"/>_<c:out value="${card.definition.id}"/>"><c:out value="${field.label}" /> <span class="required">*</span>
                  <input type="${field.secret ? 'password' : 'text'}" id="cred_<c:out value="${field.name}"/>_<c:out value="${card.definition.id}"/>"
                         name="cred_<c:out value="${field.name}"/>" autocomplete="off" required>
                </label>
                <c:if test="${!empty field.helpText}"><p class="help-text"><c:out value="${field.helpText}" /></p></c:if>
              </c:forEach>
              <c:if test="${!empty card.definition.supportedEventTypeIds}">
                <fieldset>
                  <legend>Notify on <span class="required">*</span></legend>
                  <c:forEach items="${card.definition.supportedEventTypeIds}" var="eventTypeId">
                    <label>
                      <input type="checkbox" name="eventType" value="${eventTypeId}"
                             <c:if test="${card.definition.defaultEventTypeIds.contains(eventTypeId)}">checked</c:if>>
                      <c:out value="${eventTypeId}" />
                    </label>
                  </c:forEach>
                </fieldset>
              </c:if>
              <p>
                <input type="submit" class="button radius success" value="Install"/>
                <a class="button radius secondary" href="${ctx}/admin/integrations">Cancel</a>
              </p>
            </form>
          </c:when>
          <c:otherwise>
            <p><a class="button radius" href="${ctx}/admin/integrations?installing=<c:out value="${card.definition.id}"/>">Install</a></p>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </c:forEach>
  <c:if test="${empty integrationCardList}">
    <div class="small-12 cell">No integrations are registered</div>
  </c:if>
</div>
