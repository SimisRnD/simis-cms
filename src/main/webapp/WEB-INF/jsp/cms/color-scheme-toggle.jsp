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
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<%--
  The button carries no visible text by default, so the accessible name comes from aria-label.
  platform-theme.js rewrites that label on every change, because the name describes the action the
  button will perform ("Switch to dark theme"), not the state it is in. A label alone is not
  announced when it changes under a focused button, so the status region below carries the
  confirmation. Both icons are always in the markup; platform-tokens.css shows whichever matches
  the active scheme, and aria-hidden keeps them out of the accessibility tree either way.

  The starting aria-label assumes light, which is correct for the server-rendered attribute in
  every case except a visitor whose stored preference or OS setting is dark. platform-theme.js
  corrects it on DOMContentLoaded, before the control can be reached by keyboard.
--%>
<button type="button"
        class="platform-color-scheme-toggle"
        aria-label="Switch to dark theme"
        title="Switch to dark theme">
  <i class="fa fa-moon platform-icon-light" aria-hidden="true"></i>
  <i class="fa fa-sun platform-icon-dark" aria-hidden="true"></i>
  <c:if test="${!empty colorSchemeToggleLabel}"><span><c:out value="${colorSchemeToggleLabel}" /></span></c:if>
</button>
<div class="platform-color-scheme-status show-for-sr" role="status" aria-live="polite"></div>
