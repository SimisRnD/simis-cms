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
<%@ taglib prefix="product" uri="/WEB-INF/tlds/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="product" class="com.simisinc.platform.domain.model.ecommerce.Product" scope="request"/>
<jsp:useBean id="fulfillmentOptionList" class="java.util.ArrayList" scope="request"/>
<script src="${ctx}/javascript/tinymce-6.8.2/tinymce.min.js"></script>
<script>
  tinymce.init({
    selector: 'textarea',
    branding: false,
    width: '100%',
    height: 300,
    menubar: false,
    relative_urls: false,
    convert_urls: true,
    plugins: 'advlist autolink lists link charmap preview anchor searchreplace visualblocks code insertdatetime media table wordcount',
    toolbar: 'link table | undo redo | blocks | bold italic backcolor  | bullist numlist outdent indent | removeformat | visualblocks code',
    images_upload_url: '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}',
    automatic_uploads: true
  });
</script>
<%-- Handle image uploads --%>
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
<c:choose>
  <c:when test="${product.id eq -1}"><h4>New Product</h4></c:when>
  <c:otherwise>
    <h4>Update Product</h4>
    <c:choose>
      <c:when test="${empty product.products}">
        <span class="label primary">Incomplete</span>
      </c:when>
      <c:when test="${product:isActive(product)}">
        <span class="label success"><c:out value="${product:status(product)}" /></span>
      </c:when>
      <c:when test="${product:isPending(product)}">
        <span class="label warning"><c:out value="${product:status(product)}" /></span>
      </c:when>
      <c:otherwise>
        <span class="label alert"><c:out value="${product:status(product)}" /></span>
      </c:otherwise>
    </c:choose>
  </c:otherwise>
</c:choose>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${product.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <fieldset class="fieldset">
        <legend>Product</legend>
        <label>Name <span class="required">*</span>
          <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${product.name}"/>">
        </label>
        <label>Caption
          <input type="text" placeholder="Provide an optional caption..." name="caption" value="<c:out value="${product.caption}"/>">
        </label>
        <label>Unique Id <span class="required">*</span>
          <input type="text" placeholder="Internal Reference Id..." name="uniqueId" aria-describedby="uniqueIdHelpText" value="<c:out value="${product.uniqueId}"/>">
        </label>
        <p class="help-text" id="uniqueIdHelpText">Leave blank to auto-generate; this value does not usually change! No spaces, use lowercase, a-z, 0-9, dashes</p>
        <label>Website URL
          <input type="text" placeholder="/example-product" name="productUrl" value="<c:out value="${product.productUrl}"/>">
        </label>
      </fieldset>
    </div>
    <div class="small-12 medium-6 cell">
      <fieldset class="fieldset">
        <legend>Product Image</legend>
        <div class="grid-x grid-margin-x">
          <div class="small-8 cell">
            <div class="input-group">
              <input class="input-group-field" type="text" placeholder="Local Image URL" id="imageUrl" name="imageUrl" value="<c:out value="${product.imageUrl}"/>">
              <span class="input-group-label" style="padding: 0;"><a class="button small primary expanded no-gap" data-open="imageBrowserReveal">Browse Images</a></span>
            </div>
            <label for="imageFile" class="button">Upload Image File...</label>
            <input type="file" id="imageFile" class="show-for-sr" onchange="SavePhoto(this)">
          </div>
          <div class="small-4 cell">
            <img id="imageUrlPreview" src="<c:out value="${product.imageUrl}"/>" style="max-height: 150px; max-width: 150px"/>
          </div>
        </div>
      </fieldset>
    </div>
  </div>
  <fieldset class="fieldset">
    <legend>Product Type</legend>
    <input type="radio" name="type" id="typeGood" value="good"<c:if test="${product.isGood || !product.hasType}"> checked</c:if> required/><label for="typeGood">Good</label>
    <input type="radio" name="type" id="typeService" value="service"<c:if test="${product.isService}"> checked</c:if> required/><label for="typeService">Service</label>
    <input type="radio" name="type" id="typeVirtual" value="virtual"<c:if test="${product.isVirtual}"> checked</c:if> required/><label for="typeVirtual">Virtual</label>
    <input type="radio" name="type" id="typeDownload" value="download"<c:if test="${product.isDownload}"> checked</c:if> required/><label for="typeDownload">Download</label>
    <c:if test="${!empty fulfillmentOptionList}">
      <label>Fulfillment <span class="required">*</span>
        <select name="fulfillmentId">
          <option value="-1"></option>
          <c:forEach items="${fulfillmentOptionList}" var="fulfillmentOption"><option value="${fulfillmentOption.id}"<c:if test="${fulfillmentOption.id eq product.fulfillmentId}"> selected</c:if>><c:out value="${fulfillmentOption.title}" /></option></c:forEach>
        </select>
      </label>
    </c:if>
    <label>Exclude from sale to the following states (comma-separated)
      <input type="text" placeholder="2-Letter State" name="excludeUsStates" value="<c:out value="${product.excludeUsStates}"/>">
    </label>
  </fieldset>

  <div class="callout primary">
    SKUs are required for products to appear<br />
    <small>SKUs must be unique and 5-20 characters using A-Z, 0-9, and dashes only</small>
  </div>
  <table>
    <thead>
    <tr>
      <td>SKU <span class="required">*</span></td>
      <td>UPC</td>
      <td width="160">Strike Price</td>
      <td width="160">Price <span class="required">*</span></td>
      <c:choose>
        <c:when test="${fn:length(product.attributes) eq 0}">
          <td>
            <div class="input-group">
              <input type="hidden" name="attributes[0].name" value="attribute0" />
              <input class="input-group-field" type="text" name="attributes[0].value" placeholder="Option Name" value="" />
            </div>
          </td>
          <td>
            <div class="input-group">
              <input type="hidden" name="attributes[1].name" value="attribute1" />
              <input class="input-group-field" type="text" name="attributes[1].value" placeholder="Option Name" value="" />
            </div>
          </td>
          <td>
            <div class="input-group">
              <input type="hidden" name="attributes[2].name" value="attribute2" />
              <input class="input-group-field" type="text" name="attributes[2].value" placeholder="Option Name" value="" />
            </div>
          </td>
        </c:when>
        <c:otherwise>
          <%-- These are WrapDynaBean objects --%>
          <c:forEach items="${product.nativeAttributes}" var="thisAttribute" varStatus="attributeStatus">
            <td>
              <div class="input-group">
                <input type="hidden" name="attributes[${attributeStatus.index}].name" value="attribute${attributeStatus.index}" />
                <c:choose>
                  <c:when test="${empty thisAttribute.value}">
                    <input class="input-group-field" type="text" name="attributes[${attributeStatus.index}].value" placeholder="Option Name" value="" />
                  </c:when>
                  <c:otherwise>
                    <input class="input-group-field" type="text" name="attributes[${attributeStatus.index}].value" placeholder="Option Name" value="<c:out value="${thisAttribute.value}" />" />
                  </c:otherwise>
                </c:choose>
              </div>
            </td>
          </c:forEach>
          <c:if test="${fn:length(product.attributes) lt 3}">
            <td>
              <div class="input-group">
                <input type="hidden" name="attributes[2].name" value="attribute2" />
                <input class="input-group-field" type="text" name="attributes[2].value" placeholder="Option Name" value="" />
              </div>
            </td>
          </c:if>
        </c:otherwise>
      </c:choose>
      <td width="100">Inventory</td>
      <td width="80">Low</td>
      <td width="100">Inbound</td>
      <td>Show online?</td>
      <td>Allow backorders?</td>
    </tr>
    </thead>
    <tbody>
    <c:choose>
      <c:when test="${fn:length(product.products) eq 0}">
        <c:set var="skuFormStart" scope="request" value="0"/>
        <c:set var="skuFormEnd" scope="request" value="4"/>
      </c:when>
      <c:otherwise>
        <c:set var="skuFormStart" scope="request" value="${fn:length(product.products)}"/>
        <c:set var="skuFormEnd" scope="request" value="${fn:length(product.products) + 4}"/>
        <%-- These are WrapDynaBean objects --%>
        <c:forEach items="${product.nativeProductSKUs}" var="thisProduct" varStatus="status">
          <tr>
            <td>
              <div class="input-group">
                <input type="hidden" name="products[${status.index}].id" value="<c:out value="${thisProduct.id}" />"/>
                <input class="input-group-field" type="text" name="products[${status.index}].sku" value="<c:out value="${thisProduct.sku}" />" maxlength="20" />
              </div>
            </td>
            <td>
              <div class="input-group">
                <input class="input-group-field" type="text" name="products[${status.index}].barcode" value="<c:out value="${thisProduct.barcode}" />" />
              </div>
            </td>
            <td>
              <div class="input-group">
                <span class="input-group-label"><i class="fa fa-dollar"></i></span>
                <input class="input-group-field" type="text" name="products[${status.index}].strikePrice" value="<c:if test="${thisProduct.strikePrice ne 0}"><fmt:formatNumber type="currency" currencySymbol="" value="${thisProduct.strikePrice}" /></c:if>" />
              </div>
            </td>
            <td>
              <div class="input-group">
                <span class="input-group-label"><i class="fa fa-dollar"></i></span>
                <input class="input-group-field" type="text" name="products[${status.index}].price" value="<c:if test="${thisProduct.price ne 0}"><fmt:formatNumber type="currency" currencySymbol="" value="${thisProduct.price}" /></c:if>" />
              </div>
            </td>
            <%-- These are WrapDynaBean objects --%>
            <c:forEach items="${thisProduct.attributes}" var="thisAttribute" varStatus="attributeStatus">
              <td>
                <div class="input-group">
                  <input type="hidden" name="products[${status.index}].attributes[${attributeStatus.index}].name" value="attribute${attributeStatus.index}" />
                  <input class="input-group-field" type="text" name="products[${status.index}].attributes[${attributeStatus.index}].value" placeholder="Option Value" value="<c:out value="${thisAttribute.value}" />" />
                </div>
              </td>
            </c:forEach>
            <c:if test="${fn:length(thisProduct.attributes) lt 3}">
              <td>
                <div class="input-group">
                  <input type="hidden" name="products[${status.index}].attributes[2].name" value="attribute2" />
                  <input class="input-group-field" type="text" name="products[${status.index}].attributes[2].value" placeholder="Option Name" value="" />
                </div>
              </td>
            </c:if>
            <td>
              <div class="input-group">
                <input type="hidden" name="products[${status.index}].inventoryQtyState" value="<c:out value="${thisProduct.inventoryQty}" />" />
                <input class="input-group-field" type="text" name="products[${status.index}].inventoryQty" placeholder="0" value="<c:out value="${thisProduct.inventoryQty}" />" />
              </div>
            </td>
            <td>
              <div class="input-group">
                <input class="input-group-field" type="text" name="products[${status.index}].inventoryLow" placeholder="0" value="<c:out value="${thisProduct.inventoryLow}" />" />
              </div>
            </td>
            <td>
              <div class="input-group">
                <input class="input-group-field" type="text" name="products[${status.index}].inventoryIncoming" placeholder="0" value="<c:out value="${thisProduct.inventoryIncoming}" />" />
              </div>
            </td>
            <td nowrap><input type="checkbox" id="products${status.index}enabled" name="products[${status.index}].enabled" value="true" <c:if test="${thisProduct.enabled}">checked</c:if>/><label for="products${status.index}enabled"></label></td>
            <td nowrap><input type="checkbox" id="products${status.index}backorders" name="products[${status.index}].allowBackorders" value="true" <c:if test="${thisProduct.allowBackorders}">checked</c:if>/><label for="products${status.index}backorders"></label></td>
          </tr>
        </c:forEach>
      </c:otherwise>
    </c:choose>
    <c:forEach var="i" begin="${skuFormStart}" end="${skuFormEnd}">
      <tr>
        <td>
          <div class="input-group">
            <input class="input-group-field" type="text" name="products[${i}].sku" value="" maxlength="20" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input class="input-group-field" type="text" name="products[${i}].barcode" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-dollar"></i></span>
            <input class="input-group-field" type="text" name="products[${i}].strikePrice" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-dollar"></i></span>
            <input class="input-group-field" type="text" name="products[${i}].price" value="" />
          </div>
        </td>
        <%-- Determine the number of attribute columns to show --%>
        <td>
          <div class="input-group">
            <input type="hidden" name="products[${i}].attributes[0].name" value="attribute0" />
            <input class="input-group-field" type="text" name="products[${i}].attributes[0].value" placeholder="Option Value" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input type="hidden" name="products[${i}].attributes[1].name" value="attribute1" />
            <input class="input-group-field" type="text" name="products[${i}].attributes[1].value" placeholder="Option Value" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input type="hidden" name="products[${i}].attributes[2].name" value="attribute2" />
            <input class="input-group-field" type="text" name="products[${i}].attributes[2].value" placeholder="Option Value" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input class="input-group-field" type="text" name="products[${i}].inventoryQty" placeholder="0" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input class="input-group-field" type="text" name="products[${i}].inventoryLow" placeholder="0" value="" />
          </div>
        </td>
        <td>
          <div class="input-group">
            <input class="input-group-field" type="text" name="products[${i}].inventoryIncoming" placeholder="0" value="" />
          </div>
        </td>
        <td><input type="checkbox" id="products${i}enabled" name="products[${i}].enabled" value="true" checked /><label for="products${i}enabled"></label></td>
        <td><input type="checkbox" id="products${i}backorders" name="products[${i}].allowBackorders" value="true" /><label for="products${i}backorders"></label></td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <p>
    <small>Describe it...</small>
    <textarea name="description"><c:out value="${product.description}"/></textarea>
  </p>
  <fieldset class="fieldset">
    <legend>Tax Information</legend>
    <div class="full-container">
      <div class="grid-x grid-margin-x align-middle">
        <div class="small-6 medium-3 cell">
          <input type="checkbox" id="taxable" name="taxable" value="true" <c:if test="${product.taxable}">checked</c:if>/><label for="taxable">Taxable?</label>
        </div>
        <div class="small-6 medium-3 cell">
          <label>Tax Code
            <a target="_blank" href="https://taxcode.avatax.avalara.com"><i class="fa fa-info-circle"></i></a>
            <a target="_blank" href="https://developers.taxjar.com/api/reference/#get-list-tax-categories"><i class="fa fa-info-circle"></i></a>
            <input type="text" name="taxCode" value="<c:out value="${product.taxCode}"/>" />
          </label>
        </div>
      </div>
    </div>
  </fieldset>
  <fieldset class="fieldset">
    <legend>Package Details</legend>
    <div class="full-container">
      <div class="grid-x grid-margin-x align-middle">
        <div class="small-6 medium-3 cell">
          <label>Length (inches)
            <input type="text" placeholder="0" name="packageLength" value="<c:out value="${product.packageLength}"/>" />
          </label>
          <label>Width (inches)
            <input type="text" placeholder="0" name="packageWidth" value="<c:out value="${product.packageWidth}"/>" />
          </label>
          <label>Height (inches)
            <input type="text" placeholder="0" name="packageHeight" value="<c:out value="${product.packageHeight}"/>" />
          </label>
        </div>
        <div class="small-6 medium-3 cell">
          <img src="${ctx}/images/ecommerce/package-dimensions.png" />
        </div>
        <div class="small-6 medium-3 cell">
          <label>Pounds
            <input type="text" placeholder="0" name="packageWeightPounds" value="<c:out value="${product.packageWeightPounds}"/>" />
          </label>
          <label>Ounces
            <input type="text" placeholder="0" name="packageWeightOunces" value="<c:out value="${product.packageWeightOunces}"/>" />
          </label>
        </div>
      </div>
    </div>
  </fieldset>
  <%--<label>Show the product online?--%>
    <%--<input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${product.id == -1 || product.enabled}">checked</c:if>/>--%>
  <%--</label>--%>
  <div class="full-container">
    <div class="grid-x grid-margin-x">
      <div class="medium-6 cell">
        <label>Display starting at a specific date/time?
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-calendar"></i></span>
            <input class="input-group-field" type="text" placeholder="Display right away, or choose a specific date and time..." id="activeDate" name="activeDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${product.activeDate}" />">
          </div>
        </label>
        <script>
          $(function () {
            $('#activeDate').fdatepicker({
              format: 'mm-dd-yyyy hh:ii',
              disableDblClickSelection: true,
              pickTime: true
            });
          });
        </script>
      </div>
      <div class="medium-6 cell">
        <label>Hide on a specific date/time?
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-calendar"></i></span>
            <input class="input-group-field" type="text" placeholder="" id="deactivateOnDate" name="deactivateOnDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${product.deactivateOnDate}" />">
          </div>
        </label>
        <script>
          $(function () {
            // yyyy-MM-dd HH:mm:ss.fffffffff
            $('#deactivateOnDate').fdatepicker({
              format: 'mm-dd-yyyy hh:ii',
              disableDblClickSelection: true,
              pickTime: true
            });
          });
        </script>
      </div>
    </div>
  </div>
  <div class="button-container">
    <c:choose>
      <c:when test="${!empty returnPage}">
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" value="Save" />
      </c:otherwise>
    </c:choose>
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