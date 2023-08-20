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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="cancelUrl" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/tinymce-6.6.2/tinymce.min.js"></script>
<script>
  tinymce.init({
    selector: '.html-field',
    branding: false,
    width: '100%',
    height: 300,
    menubar: false,
    relative_urls: false,
    convert_urls: true,
    browser_spellcheck: true,
    plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code insertdatetime media table wordcount',
    toolbar: 'link image media table | undo redo | blocks | bold italic backcolor | bullist numlist outdent indent hr | removeformat | visualblocks code',
    image_class_list: [
      {title: 'None', value: ''},
      {title: 'Image Left/Wrap Text Right', value: 'image-left'},
      {title: 'Image Right/Wrap Text left', value: 'image-right'},
      {title: 'Image Center On Line', value: 'image-center'}
    ]
    // file_picker_types: 'file image media',
    // link_default_target: '_blank',
    // file_picker_callback: function (callback, value, meta) {
    //   FileBrowser(value, meta.filetype, function (fileUrl) {
    //     callback(fileUrl);
    //   });
    // },
    <%--images_upload_url: '${ctx}/item-image-upload?widget=itemImageUpload1&token=${userSession.formToken}', // return { "location": "folder/sub-folder/new-location.png" }--%>
    // paste_data_images: true,
    // automatic_uploads: true
  });

  function FileBrowser(value, type, callback) {
    // type will be: file, image, media
    var cmsType = 'item-image';
    if (type === 'media') {
      cmsType = 'item-video';
    } else if (type === 'file') {
      cmsType = 'item-file';
    }
    var cmsURL = '${ctx}/' + cmsType + '-browser';
    const instanceApi = tinyMCE.activeEditor.windowManager.openUrl({
      title: 'Browser',
      url: cmsURL,
      width: 850,
      height: 650,
      onMessage: function(dialogApi, details) {
        callback(details.content);
        instanceApi.close();
      }
    });
    return false;
  }
</script>
<%-- Handle item image uploads --%>
<script>
  function SavePhoto(e) {
    var file = e.files[0]; // similar to: document.getElementById("file").files[0]
    var formData = new FormData();
    formData.append("file", file);
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
      if (this.readyState === 4) {
        if (this.status === 200) {
          var fileData = JSON.parse(this.responseText);
          document.getElementById("imageUrl").value = fileData.location;
          document.getElementById("imageUrlPreview").src = fileData.location;
        } else {
          document.getElementById("imageFile").value = "";
          alert('There was an error with the file. Make sure to use a .jpg or .png');
        }
      }
    };
    xhr.open("POST", '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}');
    xhr.send(formData);
  }
</script>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${item.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="${returnPage}"/>
  </c:if>
  <%-- Title and Message block --%>
  <h2><em><c:out value="${collection.name}" /></em></h2>
  <c:if test="${!empty title}">
    <p><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></p>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <%--
  These input types create a text field:
  text, date, datetime, datetime-local, email, month, number, password, search, tel, time, url, and week. --%>

    <h3 class="margin-top-30">Name and Description</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Name
            <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${item.name}"/>">
          </label>
          <label>
            Provide an optional summary for the item
            <textarea placeholder="optional description" name="summary" style="height:180px"><c:out value="${item.summary}"/></textarea>
          </label>
          <p>
            <label>
              Provide an optional formatted description for the item
              <textarea id="description" name="description" class="html-field"><c:out value="${item.description}"/></textarea>
            </label>
          </p>
        </div>
      </div>
    </div>

    <c:if test="${!empty categoryList}">
      <h3 class="margin-top-30 margin-bottom-20">Categories</h3>
      <div class="grid-container">
        <div class="grid-x grid-padding-x">
          <div class="small-12 cell">
            <span class="input-group-label">Primary Category</span>
            <select class="input-group-field" id="categoryId" name="categoryId">
              <option value="">Make a selection...</option>
              <c:forEach items="${categoryList}" var="category">
                <option value="${category.id}"<c:if test="${item.categoryId eq category.id}"> selected</c:if>><c:out value="${category.name}" /></option>
              </c:forEach>
            </select>
          </div>
        </div>
      </div>
      <div class="grid-container margin-top-20">
        <div class="grid-x grid-padding-x">
          <div class="small-12 cell">
            <div class="input-container">
              <span class="input-group-label">Additional Categories</span>
              <c:forEach items="${categoryList}" var="category">
                <%--                <c:if test="${fn:contains(item.categoryIdList, category.id)}"> checked</c:if>--%>
                <c:set var="contains" value="false" />
                <c:forEach var="thisCategoryId" items="${item.categoryIdList}">
                  <c:if test="${thisCategoryId eq category.id}">
                    <c:set var="contains" value="true" />
                  </c:if>
                </c:forEach>
                <input id="categoryId${category.id}" type="checkbox" name="categoryId${category.id}" value="${category.id}"<c:if test="${contains eq 'true'}"> checked</c:if> /><label for="categoryId${category.id}"><c:out value="${category.name}" /></label>
              </c:forEach>
            </div>
          </div>
        </div>
      </div>
    </c:if>

    <h3 class="margin-top-30">Reference</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 medium-8 cell">
          <label>URL
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-link"></i></span>
              <input class="input-group-field" type="url" placeholder="http://" name="url" value="<c:out value="${item.url}"/>">
            </div>
          </label>
        </div>
        <div class="small-6 medium-4 cell">
          <label>Link Text
            <input type="text" placeholder="link text" name="urlText" value="<c:out value="${item.urlText}"/>">
          </label>
        </div>
      </div>
    </div>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Image URL
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-link"></i></span>
              <input class="input-group-field" type="text" placeholder="http://" id="imageUrl" name="imageUrl" value="<c:out value="${item.imageUrl}"/>">
              <span class="input-group-label" style="padding: 0;"><a class="button small primary expanded no-gap" data-open="imageBrowserReveal">Browse Images</a></span>
            </div>
            <label for="imageFile" class="button">Upload Image File...</label>
            <input type="file" id="imageFile" class="show-for-sr" onchange="SavePhoto(this)">
          </label>
        </div>
        <div class="small-4 cell">
          <img id="imageUrlPreview" src="<c:out value="${item.imageUrl}"/>" style="max-height: 150px; max-width: 150px"/>
        </div>
      </div>
    </div>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Keywords
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-key"></i></span>
              <input class="input-group-field" type="text" placeholder="comma-separated keywords" name="keywords" value="<c:out value="${item.keywords}"/>">
            </div>
          </label>
        </div>
      </div>
    </div>

    <h3 class="margin-top-30">Location</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Location Name (optional)
            <input type="text" placeholder="name of location" name="location" value="<c:out value="${item.location}"/>">
          </label>
          <label>Street Address
            <input type="text" placeholder="number and street" name="street" value="<c:out value="${item.street}"/>">
          </label>
          <label>Street Address Line 2
            <input type="text" placeholder="suite or unit number" name="addressLine2" value="<c:out value="${item.addressLine2}"/>">
          </label>
          <label>Street Address Line 3
            <input type="text" name="addressLine3" value="<c:out value="${item.addressLine3}"/>">
          </label>
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="medium-6 cell">
          <label>City
            <input type="text" placeholder="city" name="city" value="<c:out value="${item.city}"/>">
          </label>
        </div>
        <div class="medium-6 cell">
          <label>State
            <select id="state" name="state">
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
          </label>
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-6 cell">
          <label>Postal Code
            <input type="text" placeholder="postal code" name="postalCode" value="<c:out value="${item.postalCode}"/>">
          </label>
        </div>
        <div class="small-6 cell">
          <label>Country
            <select id="country" name="country">
              <option value=""></option>
              <option value="United States"<c:if test="${item.country eq 'United States'}"> selected</c:if>>United States</option>
            </select>
          </label>
        </div>
      </div>
    </div>

    <h3 class="margin-top-30">GIS</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>County
            <input type="text" placeholder="county" name="county" value="<c:out value="${item.county}"/>">
          </label>
        </div>
        <div class="medium-6 cell">
          <label>Latitude
            <input type="text" placeholder="decimal format" name="latitude" value="<c:if test="${item.latitude ne 0}"><c:out value="${item.latitude}"/></c:if>">
          </label>
        </div>
        <div class="medium-6 cell">
          <label>Longitude
            <input type="text" placeholder="decimal format" name="longitude" value="<c:if test="${item.longitude ne 0}"><c:out value="${item.longitude}"/></c:if>">
          </label>
        </div>
      </div>
    </div>

    <h3 class="margin-top-30">Data</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Phone Number
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-phone"></i></span>
              <input class="input-group-field" type="tel" placeholder="(xxx) xxx-xxxx" name="phoneNumber" value="<c:out value="${item.phoneNumber}"/>">
            </div>
          </label>
        </div>
      </div>
    </div>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Barcode
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-barcode"></i></span>
              <input class="input-group-field" type="text" placeholder="barcode value" name="barcode" value="<c:out value="${item.barcode}"/>">
            </div>
          </label>
        </div>
      </div>
    </div>

    <h3 class="margin-top-30">Value</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label>Amount
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-dollar"></i></span>
              <input class="input-group-field" type="text" name="cost" value="<c:if test="${item.cost ne 0}"><c:out value="${item.cost}"/></c:if>">
            </div>
          </label>
        </div>
      </div>
    </div>

    <h3 class="margin-top-30">Dates</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="medium-6 cell">
          <label>Expected Start Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="expectedDate" name="expectedDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${item.expectedDate}" />">
            </div>
          </label>
          <script>
            $(function(){
              $('#expectedDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
        <div class="medium-6 cell">
          <label>Expiration Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="expirationDate" name="expirationDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${item.expirationDate}" />">
            </div>
          </label>
          <script>
            $(function(){
              $('#expirationDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="medium-6 cell">
          <label>Actual Start Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="startDate" name="startDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${item.startDate}" />">
            </div>
          </label>
          <script>
            $(function(){
              $('#startDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
        <div class="medium-6 cell">
          <label>Actual End Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="endDate" name="endDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${item.endDate}" />">
            </div>
          </label>
          <script>
            $(function(){
              // yyyy-MM-dd HH:mm:ss.fffffffff
              $('#endDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
      </div>
    </div>

    <%--
    <label>Assigned To
      <input type="text" placeholder="user name" name="assignedTo" value="<c:out value="${item.assignedTo}"/>">
    </label>
    --%>

    <c:if test="${!empty customFieldList}">
    <h3>Additional Fields</h3>
    <div class="grid-container">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
        <c:forEach items="${customFieldList.values()}" var="formField" varStatus="status">
          <label><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if>
            <c:choose>
              <c:when test="${!empty formField.listOfOptions}">
                <select id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>">
                  <option value="">&lt; Please Choose &gt;</option>
                  <c:forEach items="${formField.listOfOptions}" var="option">
                    <c:choose>
                      <c:when test="${option.value eq formField.value}">
                        <option value="${option.key}" selected><c:out value="${option.value}" /></option>
                      </c:when>
                      <c:otherwise>
                        <option value="${option.key}"><c:out value="${option.value}" /></option>
                      </c:otherwise>
                    </c:choose>
                  </c:forEach>
                </select>
              </c:when>
              <c:when test="${formField.type eq 'html'}">
                <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" class="html-field" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                  <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                  <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.value}"><c:out value="${formField.value}" /></c:if></textarea>
              </c:when>
              <c:when test="${formField.type eq 'textarea'}">
                <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" style="height:120px"
                  <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                  <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.value}"><c:out value="${formField.value}" /></c:if></textarea>
              </c:when>
              <c:otherwise>
                <input type="text"
                  id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                  <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                  <c:if test="${!empty formField.value}">value="<c:out value="${formField.value}" />"</c:if>
                  <c:if test="${formField.required}">required</c:if>>
              </c:otherwise>
            </c:choose>
          </label>
        </c:forEach>
        </div>
      </div>
    </div>
    </c:if>
    <div class="button-container">
      <input type="submit" class="button radius success" value="Save"/>
      <c:if test="${!empty cancelUrl}"><span class="button-gap"><a class="button radius secondary" href="${ctx}${cancelUrl}">Cancel</a></span></c:if>
    </div>
</form>
<div class="reveal large" id="imageBrowserReveal" data-reveal data-animation-in="slide-in-down fast">
  <h3>Loading...</h3>
</div>
<script>
  $('#imageBrowserReveal').on('open.zf.reveal', function () {
    $('#imageBrowserReveal').html("<h3>Loading...</h3>");
    $.ajax({
      url: '${ctx}/image-browser?inputId=imageUrl&view=reveal',
      cache: false,
      dataType: 'html'
    }).done(function (content) {
      setTimeout(function () {
        $('#imageBrowserReveal').html(content);
        $('#imageBrowserReveal').trigger('resizeme.zf.trigger');
      }, 1000);
    });
  })
</script>