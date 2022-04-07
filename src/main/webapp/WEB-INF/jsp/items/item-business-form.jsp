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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cancelUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
  <script src='https://www.google.com/recaptcha/api.js'></script>
  <script>
    function onSubmit(token) {
      // Submit the form
      document.getElementById("form${widgetContext.uniqueId}").submit();
    }
  </script>
</c:if>
<form id="form${widgetContext.uniqueId}" method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${item.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="${returnPage}"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div id="item-form-info-1" class="small-12 medium-7 cell">
      <div class="input-group">
        <span class="input-group-label">Company Name  <span class="required">*</span></span>
        <input class="input-group-field" type="text" placeholder="" name="name" value="<c:out value="${item.name}"/>" autocomplete="off" required="true">
      </div>
      <div class="input-group">
        <span class="input-group-label">Street Address  <span class="required">*</span></span>
        <input class="input-group-field" type="text" placeholder="" name="street" value="<c:out value="${item.street}"/>" autocomplete="off" required="true">
      </div>
      <div class="input-group">
        <span class="input-group-label">Suite/Apt/Other</span>
        <input class="input-group-field" type="text" placeholder="" name="street" value="<c:out value="${item.street}"/>" autocomplete="off">
      </div>
      <div class="grid-x grid-margin-x">
        <div id="item-form-info-2" class="small-12 large-4 cell">
          <div class="input-group">
            <span class="input-group-label">City  <span class="required">*</span></span>
            <input class="input-group-field" type="text" placeholder="" name="city" value="<c:out value="${item.city}"/>" autocomplete="off">
          </div>
        </div>
        <div id="item-form-info-3" class="small-12 large-4 cell">
          <div class="input-group">
            <span class="input-group-label">State  <span class="required">*</span></span>
            <select class="input-group-field" id="state" name="state">
              <option value=""></option>
              <option value="AL"<c:if test="${item.state eq 'AL'}"> selected</c:if>>Alabama (AL)</option>
              <option value="AK"<c:if test="${item.state eq 'AK'}"> selected</c:if>>Alaska (AK)</option>
              <option value="AZ"<c:if test="${item.state eq 'AZ'}"> selected</c:if>>Arizona (AZ)</option>
              <option value="AR"<c:if test="${item.state eq 'AR'}"> selected</c:if>>Arkansas (AR)</option>
              <option value="CA"<c:if test="${item.state eq 'CA'}"> selected</c:if>>California (CA)</option>
              <option value="CO"<c:if test="${item.state eq 'CO'}"> selected</c:if>>Colorado (CO)</option>
              <option value="CT"<c:if test="${item.state eq 'CT'}"> selected</c:if>>Connecticut (CT)</option>
              <option value="DE"<c:if test="${item.state eq 'DE'}"> selected</c:if>>Delaware (DE)</option>
              <option value="DC"<c:if test="${item.state eq 'DC'}"> selected</c:if>>District Of Columbia (DC)</option>
              <option value="FL"<c:if test="${item.state eq 'FL'}"> selected</c:if>>Florida (FL)</option>
              <option value="GA"<c:if test="${item.state eq 'GA'}"> selected</c:if>>Georgia (GA)</option>
              <option value="HI"<c:if test="${item.state eq 'HI'}"> selected</c:if>>Hawaii (HI)</option>
              <option value="ID"<c:if test="${item.state eq 'ID'}"> selected</c:if>>Idaho (ID)</option>
              <option value="IL"<c:if test="${item.state eq 'IL'}"> selected</c:if>>Illinois (IL)</option>
              <option value="IN"<c:if test="${item.state eq 'IN'}"> selected</c:if>>Indiana (IN)</option>
              <option value="IA"<c:if test="${item.state eq 'IA'}"> selected</c:if>>Iowa (IA)</option>
              <option value="KS"<c:if test="${item.state eq 'KS'}"> selected</c:if>>Kansas (KS)</option>
              <option value="KY"<c:if test="${item.state eq 'KY'}"> selected</c:if>>Kentucky (KY)</option>
              <option value="LA"<c:if test="${item.state eq 'LA'}"> selected</c:if>>Louisiana (LA)</option>
              <option value="ME"<c:if test="${item.state eq 'ME'}"> selected</c:if>>Maine (ME)</option>
              <option value="MD"<c:if test="${item.state eq 'MD'}"> selected</c:if>>Maryland (MD)</option>
              <option value="MA"<c:if test="${item.state eq 'MA'}"> selected</c:if>>Massachusetts (MA)</option>
              <option value="MI"<c:if test="${item.state eq 'MI'}"> selected</c:if>>Michigan (MI)</option>
              <option value="MN"<c:if test="${item.state eq 'MN'}"> selected</c:if>>Minnesota (MN)</option>
              <option value="MS"<c:if test="${item.state eq 'MS'}"> selected</c:if>>Mississippi (MS)</option>
              <option value="MO"<c:if test="${item.state eq 'MO'}"> selected</c:if>>Missouri (MO)</option>
              <option value="MT"<c:if test="${item.state eq 'MT'}"> selected</c:if>>Montana (MT)</option>
              <option value="NE"<c:if test="${item.state eq 'NE'}"> selected</c:if>>Nebraska (NE)</option>
              <option value="NV"<c:if test="${item.state eq 'NV'}"> selected</c:if>>Nevada (NV)</option>
              <option value="NH"<c:if test="${item.state eq 'NH'}"> selected</c:if>>New Hampshire (NH)</option>
              <option value="NJ"<c:if test="${item.state eq 'NJ'}"> selected</c:if>>New Jersey (NJ)</option>
              <option value="NM"<c:if test="${item.state eq 'NM'}"> selected</c:if>>New Mexico (NM)</option>
              <option value="NY"<c:if test="${item.state eq 'NY'}"> selected</c:if>>New York (NY)</option>
              <option value="NC"<c:if test="${item.state eq 'NC'}"> selected</c:if>>North Carolina (NC)</option>
              <option value="ND"<c:if test="${item.state eq 'ND'}"> selected</c:if>>North Dakota (ND)</option>
              <option value="OH"<c:if test="${item.state eq 'OH'}"> selected</c:if>>Ohio (OH)</option>
              <option value="OK"<c:if test="${item.state eq 'OK'}"> selected</c:if>>Oklahoma (OK)</option>
              <option value="OR"<c:if test="${item.state eq 'OR'}"> selected</c:if>>Oregon (OR)</option>
              <option value="PA"<c:if test="${item.state eq 'PA'}"> selected</c:if>>Pennsylvania (PA)</option>
              <option value="RI"<c:if test="${item.state eq 'RI'}"> selected</c:if>>Rhode Island (RI)</option>
              <option value="SC"<c:if test="${item.state eq 'SC'}"> selected</c:if>>South Carolina (SC)</option>
              <option value="SD"<c:if test="${item.state eq 'SD'}"> selected</c:if>>South Dakota (SD)</option>
              <option value="TN"<c:if test="${item.state eq 'TN'}"> selected</c:if>>Tennessee (TN)</option>
              <option value="TX"<c:if test="${item.state eq 'TX'}"> selected</c:if>>Texas (TX)</option>
              <option value="UT"<c:if test="${item.state eq 'UT'}"> selected</c:if>>Utah (UT)</option>
              <option value="VT"<c:if test="${item.state eq 'VT'}"> selected</c:if>>Vermont (VT)</option>
              <option value="VA"<c:if test="${item.state eq 'VA'}"> selected</c:if>>Virginia (VA)</option>
              <option value="WA"<c:if test="${item.state eq 'WA'}"> selected</c:if>>Washington (WA)</option>
              <option value="WV"<c:if test="${item.state eq 'WV'}"> selected</c:if>>West Virginia (WV)</option>
              <option value="WI"<c:if test="${item.state eq 'WI'}"> selected</c:if>>Wisconsin (WI)</option>
              <option value="WY"<c:if test="${item.state eq 'WY'}"> selected</c:if>>Wyoming (WY)</option>
              <option value="AS"<c:if test="${item.state eq 'AS'}"> selected</c:if>>American Samoa (AS)</option>
              <option value="GU"<c:if test="${item.state eq 'GU'}"> selected</c:if>>Guam (GU)</option>
              <option value="MP"<c:if test="${item.state eq 'MP'}"> selected</c:if>>Northern Mariana Islands (MP)</option>
              <option value="PR"<c:if test="${item.state eq 'PR'}"> selected</c:if>>Puerto Rico (PR)</option>
              <option value="UM"<c:if test="${item.state eq 'UM'}"> selected</c:if>>United States Minor Outlying Islands (UM)</option>
              <option value="VI"<c:if test="${item.state eq 'VI'}"> selected</c:if>>Virgin Islands (VI)</option>
              <option value="AA"<c:if test="${item.state eq 'AA'}"> selected</c:if>>Armed Forces Americas (AA)</option>
              <option value="AP"<c:if test="${item.state eq 'AP'}"> selected</c:if>>Armed Forces Pacific (AP)</option>
              <option value="AE"<c:if test="${item.state eq 'AE'}"> selected</c:if>>Armed Forces Others (AE)</option>
            </select>
          </div>
        </div>
        <div id="item-form-info-4" class="small-12 large-4 cell">
          <div class="input-group">
            <span class="input-group-label">Zip  <span class="required">*</span></span>
            <input class="input-group-field" type="text" placeholder="" name="postalCode" value="<c:out value="${item.postalCode}"/>" autocomplete="off">
          </div>
        </div>
      </div>
    </div>
    <div id="item-form-info-5" class="small-12 medium-5 cell">
      <div class="input-group">
        <span class="input-group-label">Website  <span class="required">*</span></span>
        <input class="input-group-field" type="text" placeholder="http://" name="url" value="<c:out value="${item.url}"/>" required="true">
      </div>
      <div class="input-group">
        <span class="input-group-label">Phone  <span class="required">*</span></span>
        <input class="input-group-field" type="tel" placeholder="(xxx) xxx-xxxx" name="phoneNumber" value="<c:out value="${item.phoneNumber}"/>" required="true">
      </div>
      <div class="input-group">
        <span class="input-group-label"># of employees</span>
        <input class="input-group-field" type="text" placeholder="" name="numberOfEmployees" value="">
      </div>
      <div class="input-group">
        <span class="input-group-label"># of years in business</span>
        <input class="input-group-field" type="text" placeholder="" name="numberOfYearsInBusiness" value="">
      </div>
    </div>
  </div>

  <div class="grid-x grid-margin-x">
    <c:if test="${!empty categoryList1}">
      <div id="item-form-info-6" class="small-12 medium-7 cell">
        <div class="input-container">
          <label>Primary Industry  <span class="required">*</span></label>
          <div class="grid-x grid-padding-x">
            <div class="small-12 medium-6 cell">
              <c:forEach items="${categoryList1}" var="category" varStatus="categoryStatus">
                <input type="radio" name="categoryId" value="${category.id}" id="category1${categoryStatus.index}"><label for="category1${categoryStatus.index}"><c:out value="${category.name}" /></label><c:if test="${!categoryStatus.last}"><br /></c:if>
              </c:forEach>
            </div>
            <c:if test="${!empty categoryList2}">
              <div class="small-12 medium-6 cell">
                <c:forEach items="${categoryList2}" var="category" varStatus="categoryStatus">
                  <input type="radio" name="categoryId" value="${category.id}" id="category2${categoryStatus.index}"><label for="category2${categoryStatus.index}"><c:out value="${category.name}" /></label><c:if test="${!categoryStatus.last}"><br /></c:if>
                </c:forEach>
              </div>
            </c:if>
          </div>
        </div>
        <div class="input-container">
          <label>Minority/Women/Disadvantaged/Veteran-Business Enterprise</label>
          <div class="grid-x grid-padding-x">
            <div class="small-12 cell">
              <input class="input-group-field" type="checkbox" name="mwbeSWAM" value="mwbeSWAM" id="mwbeSWAM"><label for="mwbeSWAM">Yes</label>
            </div>
          </div>
        </div>
      </div>
    </c:if>
    <div id="item-form-info-7" class="small-12 medium-5 cell">
      <div class="input-container">
        <label>Description  <span class="required">*</span>
          <textarea placeholder="" name="summary" required="true"><c:out value="${item.summary}"/></textarea>
        </label>
      </div>
    </div>
  </div>

  <div class="grid-x grid-margin-x">
    <div id="item-form-info-8" class="small-12 medium-7 cell">
      <div class="input-group">
        <span class="input-group-label">Contact Name  <span class="required">*</span></span>
        <input class="input-group-field" type="text" placeholder="" name="contactName" value="" required="true">
      </div>

      <div class="input-container">
        <div class="input-group">
          <span class="input-group-label">Phone</span>
          <input class="input-group-field" type="tel" placeholder="(xxx) xxx-xxxx" name="contactPhoneNumber" value="">

          <span class="input-group-label">Email  <span class="required">*</span></span>
          <input class="input-group-field" type="email" placeholder="" name="contactEmail" value="" required="true">
        </div>
      </div>

    </div>
    <div class="small-12 medium-5 cell">
      <c:choose>
        <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
          <button
              class="g-recaptcha button radius success"
              data-sitekey="<c:out value="${googleSiteKey}" />"
              data-callback="onSubmit">
            Submit
          </button>
        </c:when>
        <c:when test="${useCaptcha eq 'true'}">
          Please enter the text value you see in the image:<br />
          <img src="/assets/captcha" class="margin-bottom-10" /><br />
          <input type="text" name="captcha" value="" required/>
          <input type="submit" class="button radius success" value="Submit"/>
        </c:when>
        <c:otherwise>
          <input type="submit" class="button radius success" value="Submit"/>
        </c:otherwise>
      </c:choose>
      <c:if test="${!empty cancelUrl}"><a class="button radius secondary" href="${cancelUrl}">Cancel</a></c:if>
    </div>
  </div>
</form>
