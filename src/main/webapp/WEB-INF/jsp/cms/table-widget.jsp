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
<%-- tableData is a Map<String,Object> set by TableWidget in request scope: "headers" is a
     List<String>, "rows" is a List<List<String>> -- plain Java collections, not the raw Jackson
     JsonNode, because JSTL's <c:forEach> cannot iterate a JsonNode/ArrayNode. --%>

<div class="table-widget-container">
  <table class="data-table" role="table">
    <c:if test="${not empty tableData.headers}">
      <thead>
        <tr role="row">
          <%-- The loop variable is deliberately NOT named "header": JSP EL reserves that identifier
               for the implicit request-header Map (JSP.2.9), so a same-named <c:forEach> variable is
               silently ignored -- ${header} always resolves to the implicit object, never the loop
               value, regardless of scope. That previously rendered the viewer's own request headers
               (including the JSESSIONID/userToken session cookies) into every <th>, changing per
               request, instead of the actual column header text. --%>
          <c:forEach items="${tableData.headers}" var="headerCell" varStatus="headerStatus">
            <th role="columnheader" scope="col">
              <c:out value="${headerCell}"/>
            </th>
          </c:forEach>
        </tr>
      </thead>
    </c:if>
    <tbody>
      <c:if test="${not empty tableData.rows}">
        <c:forEach items="${tableData.rows}" var="row" varStatus="rowStatus">
          <tr role="row">
            <c:forEach items="${row}" var="cell">
              <td role="cell">
                <c:out value="${cell}"/>
              </td>
            </c:forEach>
          </tr>
        </c:forEach>
      </c:if>
      <c:if test="${empty tableData.rows}">
        <tr>
          <td colspan="100" style="text-align: center; padding: 20px; color: #999;">
            No data
          </td>
        </tr>
      </c:if>
    </tbody>
  </table>
</div>

<style nonce="${cspNonce}">
  .table-widget-container {
    overflow-x: auto;
    margin: 20px 0;
  }

  .data-table {
    width: 100%;
    border-collapse: collapse;
    border: 1px solid #ddd;
  }

  .data-table thead {
    background-color: #f9f9f9;
  }

  .data-table th {
    padding: 12px;
    text-align: left;
    font-weight: 600;
    border-bottom: 2px solid #ddd;
  }

  .data-table td {
    padding: 12px;
    border-bottom: 1px solid #eee;
  }

  .data-table tbody tr:hover {
    background-color: #fafafa;
  }

  .data-table tbody tr:last-child td {
    border-bottom: 1px solid #ddd;
  }
</style>
