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
<h4>Roles already MFA-enrolled</h4>
<p class="help-text">Roles with at least one member who has already enrolled MFA -- a role with some but not all members enrolled still appears here, since enabling enforcement is per-role, not per-user.</p>
<c:choose>
  <c:when test="${empty roleList}">
    <p><em>None</em></p>
  </c:when>
  <c:otherwise>
    <ul>
      <c:forEach var="role" items="${roleList}">
        <li><c:out value="${role.title}" /> <code><c:out value="${role.code}" /></code></li>
      </c:forEach>
    </ul>
  </c:otherwise>
</c:choose>
