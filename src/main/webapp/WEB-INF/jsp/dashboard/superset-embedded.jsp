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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="dashboardValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="hideChartControls" class="java.lang.String" scope="request"/>
<jsp:useBean id="hideChartTitle" class="java.lang.String" scope="request"/>
<style>
    #superset-container${widgetContext.uniqueId},
    #superset-container${widgetContext.uniqueId} iframe {
        min-height: <c:out value="${height}" />;
    }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<div id="superset-container${widgetContext.uniqueId}" class="superset-dashboard-container">
</div>
<script src="${ctx}/javascript/superset-embedded-sdk-0.1.0-alpha.7/index.js"></script>
<script>
  function fetchGuestTokenFromBackend${widgetContext.uniqueId}() {
    return new Promise(function (resolve, reject) {
      $.ajax({
        url: '/json/supersetGuestToken?widgetUniqueId=${widgetContext.uniqueId}&dashboardId=<c:out value="${dashboardValue}" />',
        success: function (data) {
          resolve(data.guestToken);
        }
      });
    });
  }
  supersetEmbeddedSdk.embedDashboard({
    id: "<c:out value="${dashboardEmbeddedId}" />",
    supersetDomain: "<c:out value="${supersetDomain}" />",
    mountPoint: document.getElementById("superset-container${widgetContext.uniqueId}"),
    fetchGuestToken: () => fetchGuestTokenFromBackend${widgetContext.uniqueId}(),
    dashboardUiConfig: { hideTitle: ${hideChartTitle}, hideChartControls: ${hideChartControls} }
  });
</script>
