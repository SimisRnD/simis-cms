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
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="collectionTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="collectionRoleList" class="java.util.ArrayList" scope="request"/>
<form class="table-of-contents-editor" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <input type="hidden" name="collectionId" value="${collection.id}"/>
  <input type="hidden" name="returnPage" value="${returnPage}"/>
  <table>
    <thead>
    <tr>
      <th width="100">Enabled</th>
      <th width="80">Order</th>
      <th width="140">Tab Name</th>
      <th width="140">Profile Tab Link</th>
      <%--
      <th width="140">Module</th>
      <th>
        <c:forEach items="${collectionRoleList}" var="role" varStatus="status">
          <span class="text-no-wrap no-gap-all"><input type="checkbox" id="role${role.id}of${collectionTabList.size() + i}" name="role${role.id}of${collectionTabList.size() + i}" value="true" checked /><label for="role${role.id}of${collectionTabList.size() + i}"><c:out value="${role.title}"/></label></span>
        </c:forEach>
      </th>
      --%>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${collectionTabList}" var="tab" varStatus="status">
      <tr>
        <td>
          <div class="switch large no-gap">
            <input class="switch-input" id="enabled${status.count}-yes-no" type="checkbox" name="enabled${status.count}" value="true"<c:if test="${tab.enabled}"> checked</c:if>>
            <label class="switch-paddle" for="enabled${status.count}-yes-no">
              <span class="switch-active" aria-hidden="true">Yes</span>
              <span class="switch-inactive" aria-hidden="true">No</span>
            </label>
          </div>
        </td>
        <td>
          <input type="hidden" name="tab${status.count}" value="${tab.id}" />
          <input type="text" name="order${status.count}" value="${status.count}" class="no-gap" />
        </td>
        <td>
          <input type="text" name="name${status.count}" value="${tab.name}" class="no-gap" />
        </td>
        <td>
          <input type="text" name="link${status.count}" value="${tab.link}" class="no-gap" />
        </td>
        <%--
        <td>
          &nbsp;
        </td>
        <td>
          <c:forEach items="${collectionRoleList}" var="role" varStatus="status">
            <span class="text-no-wrap no-gap-all"><input type="checkbox" id="role${role.id}of${status.count}" name="role${role.id}of${status.count}" value="true" checked /><label for="role${role.id}of${status.count}"><c:out value="${role.title}"/></label></span>
          </c:forEach>
        </td>
        --%>
      </tr>
    </c:forEach>
    <%-- Extra rows --%>
    <c:forEach var="i" begin="1" end="10">
      <tr>
        <td>
          <div class="switch large no-gap">
            <input class="switch-input" id="enabled${collectionTabList.size() + i}-yes-no" type="checkbox" name="enabled${collectionTabList.size() + i}" value="true" checked>
            <label class="switch-paddle" for="enabled${collectionTabList.size() + i}-yes-no">
              <span class="switch-active" aria-hidden="true">Yes</span>
              <span class="switch-inactive" aria-hidden="true">No</span>
            </label>
          </div>
        </td>
        <td>
          <input type="text" name="order${collectionTabList.size() + i}" value="${collectionTabList.size() + i}" class="no-gap" />
        </td>
        <td>
          <input type="text" name="name${collectionTabList.size() + i}" value="" class="no-gap" />
        </td>
        <td>
          <input type="text" name="link${collectionTabList.size() + i}" value="" class="no-gap" />
        </td>
        <%--
        <td>
          &nbsp;
        </td>
        <td>
          <c:forEach items="${collectionRoleList}" var="role" varStatus="status">
            <span class="text-no-wrap no-gap-all"><input type="checkbox" id="role${role.id}of${collectionTabList.size() + i}" name="role${role.id}of${collectionTabList.size() + i}" value="true" checked /><label for="role${role.id}of${collectionTabList.size() + i}"><c:out value="${role.title}"/></label></span>
          </c:forEach>
        </td>
        --%>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <p>
    <input type="submit" class="button radius success" value="Save"/>
    <a href="${returnPage}" class="button radius secondary">Cancel</a>
  </p>
</form>
