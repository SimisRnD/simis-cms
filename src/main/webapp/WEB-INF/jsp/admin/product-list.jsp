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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="product" uri="/WEB-INF/product-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="productList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<a class="button small radius primary float-left" href="${ctx}/admin/product?returnPage=/admin/products">Add a Product <i class="fa fa-arrow-circle-right"></i></a>
<form method="post" action="${ctx}/admin/products">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="syncProducts" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-sync-alt"></i> Sync Products</button>
</form>
<form method="post" action="${ctx}/admin/products">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<table class="stack">
  <thead>
    <tr>
      <th>Image</th>
      <th>Name</th>
      <th>Price</th>
      <th width="100">Type</th>
      <th width="170">Inventory</th>
      <th>Status</th>
      <th class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${productList}" var="product">
      <tr>
        <td>
          <c:if test="${!empty product.imageUrl}"><img src="<c:out value="${product.imageUrl}"/>" style="max-height: 100px; max-width: 100px"/></c:if>
        </td>
        <td>
          <a href="${ctx}/admin/product?productId=${product.id}&returnPage=/admin/products"><c:out value="${product.nameWithCaption}" /></a>
          <c:if test="${!product.enabled}"><span class="label warning">archived</span></c:if>
          <span class="subheader">(<c:out value="${product.uniqueId}" />)</span><br />
          <c:choose>
            <c:when test="${empty product.products}">
              <span class="subheader">none</span>
            </c:when>
            <c:otherwise>
              <c:forEach items="${product.nativeProductSKUs}" var="thisProduct" varStatus="status">
                <c:if test="${!thisProduct.enabled}"><i class="fa fa-times-rectangle"></i></c:if>&nbsp;<c:out value="${thisProduct.sku}" /><c:if test="${!status.last}">,</c:if>
              </c:forEach>
            </c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:out value="${product:priceRange(product)}" />
        </td>
        <td>
          <c:choose>
            <c:when test="${product.isGood}"><span class="label round secondary">Good</span></c:when>
            <c:when test="${product.isService}"><span class="label round secondary">Service</span></c:when>
            <c:when test="${product.isVirtual}"><span class="label round secondary">Virtual</span></c:when>
            <c:when test="${product.isDownload}"><span class="label round secondary">Download</span></c:when>
          </c:choose>
        </td>
        <td>
          <c:out value="${product:inventorySummary(product)}" />
        </td>
        <td>
          <c:choose>
            <c:when test="${product:isActive(product)}">
              <span class="label success"><c:out value="${product:status(product)}" /></span>
              <c:if test="${!empty product.deactivateOnDate}">
                <small><br />until <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${product.deactivateOnDate}" /></small>
              </c:if>
            </c:when>
            <c:otherwise>
              <c:choose>
                <c:when test="${empty product.products}">
                  <span class="label primary">Incomplete</span>
                </c:when>
                <c:when test="${product:isPending(product)}">
                  <span class="label warning"><c:out value="${product:status(product)}" /></span>
                </c:when>
                <c:otherwise>
                  <span class="label alert"><c:out value="${product:status(product)}" /></span>
                </c:otherwise>
              </c:choose>
              <c:if test="${!empty product.activeDate}">
                <small><br />starts <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${product.activeDate}" /></small>
              </c:if>
              <c:if test="${!empty product.deactivateOnDate}">
                <small><br />ends <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${product.deactivateOnDate}" /></small>
              </c:if>
            </c:otherwise>
          </c:choose>
          <c:choose>
            <c:when test="${!empty product.squareCatalogId}">
              <span class="label success">Sync'd</span>
            </c:when>
            <c:otherwise>
              <span class="label warning" style="white-space: nowrap">Not Sync'd</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td class="text-center" nowrap>
          <c:if test="${!empty product.productUrl}">
            <a href="${ctx}<c:out value="${product.productUrl}" />"><i class="fa fa-link"></i></a>
          </c:if>
          <a href="${ctx}/admin/product?productId=${product.id}&returnPage=/admin/products"><i class="fa fa-edit"></i></a>
          <c:if test="${product.orderCount eq 0}">
            <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&productId=${product.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(product.name)}" />?');"><i class="fa fa-remove"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty productList}">
      <tr>
        <td colspan="7">No products were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
