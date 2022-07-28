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
<%@ page import="java.util.TimeZone" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/geoip-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="userLogin" class="com.simisinc.platform.domain.model.login.UserLogin" scope="request"/>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Fixed header --%>
  <div id="sticky-container" data-sticky-container>
    <div id="sticky-item" data-sticky style="width:100%" data-top-anchor="1" data-sticky-on="small">
      <div style="padding-top:16px;background-color:<c:out value="${themePropertyMap['theme.body.backgroundColor']}" />;">
        <div class="button-container float-right">
            <input type="submit" class="button small radius success" value="Save"/>
            <a class="button small radius secondary" href="${ctx}/admin/user-details?userId=${user.id}">Cancel</a>
        </div>
        <h3><c:out value="${user.fullName}" /></h3>
        <c:if test="${!empty user.title || !empty user.city || !empty user.state}">
          <p>
            <c:if test="${!empty user.title}">
              <c:out value="${user.title}" /><br />
            </c:if>
            <c:if test="${!empty user.city || !empty state}">
              <small><i class="fa fa-map-marker"></i>
                <c:if test="${!empty user.city}"><c:out value="${user.city}" /></c:if>
                <c:if test="${!empty user.state}"><c:out value="${user.state}" /></c:if>
              </small>
            </c:if>
          </p>
        </c:if>
        <hr>
      </div>
    </div>
  </div>
  <%@include file="../page_messages.jspf" %>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${user.id}"/>
    <div class="grid-container">
  <div class="grid-x grid-padding-x">
    <div class="small-12 medium-7 cell">
      <fieldset>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="firstName" class="text-right middle">First Name <span class="required">*</span></label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="firstName" name="firstName" value="<c:out value="${user.firstName}" />" required />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="lastName" class="text-right middle">Last Name <span class="required">*</span></label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="lastName" name="lastName" value="<c:out value="${user.lastName}" />" required />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="title" class="text-right middle">Title</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="title" name="title" value="<c:out value="${user.title}" />" />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="organization" class="text-right middle">Organization</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="organization" name="organization" value="<c:out value="${user.organization}" />" />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="department" class="text-right middle">Department</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="department" name="department" value="<c:out value="${user.department}" />" />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="nickname" class="text-right middle">Community Nickname</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="nickname" name="nickname" value="<c:out value="${user.nickname}" />" />
          </div>
        </div>

        <c:if test="${!empty user.username && user.email ne user.username}">
          <div class="grid-x grid-padding-x">
            <div class="small-4 cell">
              <label class="text-right middle">Username</label>
            </div>
            <div class="small-8 cell">
              <div class="middle"><c:out value="${user.username}" /></div>
              <input type="hidden" id="username" name="username" value="<c:out value="${user.username}" />" />
            </div>
          </div>
        </c:if>

        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="email" class="text-right middle">Email <span class="required">*</span></label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="email" name="email" value="<c:out value="${user.email}" />" required />
          </div>
        </div>
      </fieldset>

      <fieldset>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="city" class="text-right middle">City</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="city" name="city" value="<c:out value="${user.city}" />" />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="state" class="text-right middle">State/Province</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <%--<input type="text" id="state" name="state" value="<c:out value="${user.state}" />" />--%>
            <select id="state" name="state">
              <option value=""></option>
              <option value="AL"<c:if test="${user.state eq 'AL'}"> selected</c:if>>Alabama (AL)</option>
              <option value="AK"<c:if test="${user.state eq 'AK'}"> selected</c:if>>Alaska (AK)</option>
              <option value="AZ"<c:if test="${user.state eq 'AZ'}"> selected</c:if>>Arizona (AZ)</option>
              <option value="AR"<c:if test="${user.state eq 'AR'}"> selected</c:if>>Arkansas (AR)</option>
              <option value="CA"<c:if test="${user.state eq 'CA'}"> selected</c:if>>California (CA)</option>
              <option value="CO"<c:if test="${user.state eq 'CO'}"> selected</c:if>>Colorado (CO)</option>
              <option value="CT"<c:if test="${user.state eq 'CT'}"> selected</c:if>>Connecticut (CT)</option>
              <option value="DE"<c:if test="${user.state eq 'DE'}"> selected</c:if>>Delaware (DE)</option>
              <option value="DC"<c:if test="${user.state eq 'DC'}"> selected</c:if>>District Of Columbia (DC)</option>
              <option value="FL"<c:if test="${user.state eq 'FL'}"> selected</c:if>>Florida (FL)</option>
              <option value="GA"<c:if test="${user.state eq 'GA'}"> selected</c:if>>Georgia (GA)</option>
              <option value="HI"<c:if test="${user.state eq 'HI'}"> selected</c:if>>Hawaii (HI)</option>
              <option value="ID"<c:if test="${user.state eq 'ID'}"> selected</c:if>>Idaho (ID)</option>
              <option value="IL"<c:if test="${user.state eq 'IL'}"> selected</c:if>>Illinois (IL)</option>
              <option value="IN"<c:if test="${user.state eq 'IN'}"> selected</c:if>>Indiana (IN)</option>
              <option value="IA"<c:if test="${user.state eq 'IA'}"> selected</c:if>>Iowa (IA)</option>
              <option value="KS"<c:if test="${user.state eq 'KS'}"> selected</c:if>>Kansas (KS)</option>
              <option value="KY"<c:if test="${user.state eq 'KY'}"> selected</c:if>>Kentucky (KY)</option>
              <option value="LA"<c:if test="${user.state eq 'LA'}"> selected</c:if>>Louisiana (LA)</option>
              <option value="ME"<c:if test="${user.state eq 'ME'}"> selected</c:if>>Maine (ME)</option>
              <option value="MD"<c:if test="${user.state eq 'MD'}"> selected</c:if>>Maryland (MD)</option>
              <option value="MA"<c:if test="${user.state eq 'MA'}"> selected</c:if>>Massachusetts (MA)</option>
              <option value="MI"<c:if test="${user.state eq 'MI'}"> selected</c:if>>Michigan (MI)</option>
              <option value="MN"<c:if test="${user.state eq 'MN'}"> selected</c:if>>Minnesota (MN)</option>
              <option value="MS"<c:if test="${user.state eq 'MS'}"> selected</c:if>>Mississippi (MS)</option>
              <option value="MO"<c:if test="${user.state eq 'MO'}"> selected</c:if>>Missouri (MO)</option>
              <option value="MT"<c:if test="${user.state eq 'MT'}"> selected</c:if>>Montana (MT)</option>
              <option value="NE"<c:if test="${user.state eq 'NE'}"> selected</c:if>>Nebraska (NE)</option>
              <option value="NV"<c:if test="${user.state eq 'NV'}"> selected</c:if>>Nevada (NV)</option>
              <option value="NH"<c:if test="${user.state eq 'NH'}"> selected</c:if>>New Hampshire (NH)</option>
              <option value="NJ"<c:if test="${user.state eq 'NJ'}"> selected</c:if>>New Jersey (NJ)</option>
              <option value="NM"<c:if test="${user.state eq 'NM'}"> selected</c:if>>New Mexico (NM)</option>
              <option value="NY"<c:if test="${user.state eq 'NY'}"> selected</c:if>>New York (NY)</option>
              <option value="NC"<c:if test="${user.state eq 'NC'}"> selected</c:if>>North Carolina (NC)</option>
              <option value="ND"<c:if test="${user.state eq 'ND'}"> selected</c:if>>North Dakota (ND)</option>
              <option value="OH"<c:if test="${user.state eq 'OH'}"> selected</c:if>>Ohio (OH)</option>
              <option value="OK"<c:if test="${user.state eq 'OK'}"> selected</c:if>>Oklahoma (OK)</option>
              <option value="OR"<c:if test="${user.state eq 'OR'}"> selected</c:if>>Oregon (OR)</option>
              <option value="PA"<c:if test="${user.state eq 'PA'}"> selected</c:if>>Pennsylvania (PA)</option>
              <option value="RI"<c:if test="${user.state eq 'RI'}"> selected</c:if>>Rhode Island (RI)</option>
              <option value="SC"<c:if test="${user.state eq 'SC'}"> selected</c:if>>South Carolina (SC)</option>
              <option value="SD"<c:if test="${user.state eq 'SD'}"> selected</c:if>>South Dakota (SD)</option>
              <option value="TN"<c:if test="${user.state eq 'TN'}"> selected</c:if>>Tennessee (TN)</option>
              <option value="TX"<c:if test="${user.state eq 'TX'}"> selected</c:if>>Texas (TX)</option>
              <option value="UT"<c:if test="${user.state eq 'UT'}"> selected</c:if>>Utah (UT)</option>
              <option value="VT"<c:if test="${user.state eq 'VT'}"> selected</c:if>>Vermont (VT)</option>
              <option value="VA"<c:if test="${user.state eq 'VA'}"> selected</c:if>>Virginia (VA)</option>
              <option value="WA"<c:if test="${user.state eq 'WA'}"> selected</c:if>>Washington (WA)</option>
              <option value="WV"<c:if test="${user.state eq 'WV'}"> selected</c:if>>West Virginia (WV)</option>
              <option value="WI"<c:if test="${user.state eq 'WI'}"> selected</c:if>>Wisconsin (WI)</option>
              <option value="WY"<c:if test="${user.state eq 'WY'}"> selected</c:if>>Wyoming (WY)</option>
              <option value="AS"<c:if test="${user.state eq 'AS'}"> selected</c:if>>American Samoa (AS)</option>
              <option value="GU"<c:if test="${user.state eq 'GU'}"> selected</c:if>>Guam (GU)</option>
              <option value="MP"<c:if test="${user.state eq 'MP'}"> selected</c:if>>Northern Mariana Islands (MP)</option>
              <option value="PR"<c:if test="${user.state eq 'PR'}"> selected</c:if>>Puerto Rico (PR)</option>
              <option value="UM"<c:if test="${user.state eq 'UM'}"> selected</c:if>>United States Minor Outlying Islands (UM)</option>
              <option value="VI"<c:if test="${user.state eq 'VI'}"> selected</c:if>>Virgin Islands (VI)</option>
              <option value="AA"<c:if test="${user.state eq 'AA'}"> selected</c:if>>Armed Forces Americas (AA)</option>
              <option value="AP"<c:if test="${user.state eq 'AP'}"> selected</c:if>>Armed Forces Pacific (AP)</option>
              <option value="AE"<c:if test="${user.state eq 'AE'}"> selected</c:if>>Armed Forces Others (AE)</option>
            </select>
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="country" class="text-right middle">Country</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <select id="country" name="country">
              <option value=""></option>
              <option value="United States"<c:if test="${user.country eq 'United States'}"> selected</c:if>>United States</option>
              <c:if test="${!empty user.country && user.country ne 'United States'}">
                <option value="<c:out value="${user.country}" />" selected><c:out value="${user.country}" /></option>
              </c:if>
            </select>
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="postalCode" class="text-right middle">Postal Code</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <input type="text" id="postalCode" name="postalCode" value="<c:out value="${user.postalCode}" />" />
          </div>
        </div>
        <div class="grid-x grid-padding-x">
          <div class="small-4 cell">
            <label for="timeZone" class="text-right middle">Time Zone</label>
          </div>
          <div class="small-8 align-self-middle cell">
            <select id="timeZone" name="timeZone">
              <option value="">System Default</option>
              <c:forEach items="<%= TimeZone.getAvailableIDs() %>" var="timezone">
                <option value="${timezone}"<c:if test="${user.timeZone eq timezone}"> selected</c:if>><c:out value="${timezone}" /></option>
              </c:forEach>
            </select>
          </div>
        </div>
      </fieldset>
    </div>
    <div class="small-12 medium-4 cell">
      <fieldset>
        <div class="grid-x grid-padding-x callout secondary">
          <div class="small-3 text-right cell">
            <label>Roles</label>
          </div>
          <div class="small-9 cell">
            <c:forEach items="${roleList}" var="role">
              <c:choose>
                <c:when test="${role.code eq 'admin' && !userSession.hasRole('admin')}"><%-- --%></c:when>
                <c:otherwise>
                  <input id="roleId${role.id}" type="checkbox" name="roleId${role.id}" value="${role.id}" <c:if test="${user.hasRole(role.code)}">checked</c:if>/><label for="roleId${role.id}"><c:out value="${role.title}" /></label><br />
                </c:otherwise>
              </c:choose>
            </c:forEach>
          </div>
        </div>
      </fieldset>
      <fieldset>
        <div class="grid-x grid-padding-x callout secondary">
          <div class="small-3 text-right cell">
            <label>Groups</label>
          </div>
          <div class="small-9 cell">
            <c:forEach items="${groupList}" var="group">
              <input id="groupId${group.id}" type="checkbox" name="groupId${group.id}" value="${group.id}" <c:if test="${user.hasGroup(group.uniqueId)}">checked</c:if>/><label for="groupId${group.id}"><c:out value="${group.name}" /></label><br />
            </c:forEach>
          </div>
        </div>
      </fieldset>
    </div>
  </div>
  </div>
</form>
<script>
  $(document).ready(function () {
    // Dynamically set the position of the header
    var container = document.getElementById('sticky-container');
    var stickyItem = document.getElementById('sticky-item');
    var rect = container.getBoundingClientRect();
    var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
    var newTop = (Math.round(rect.top + scrollTop) - 10) + 'px';

    $('#sticky-item').on('sticky.zf.stuckto:top', function() {
      stickyItem.style.marginTop = newTop;
      $('#sticky-item').data('zfPlugin').topPoint = newTop;
    });
    stickyItem.style.marginTop = newTop;
    $('#sticky-item').data('zfPlugin').topPoint = newTop;
  });
</script>