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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="placeholder" class="java.lang.String" scope="request"/>
<jsp:useBean id="linkText" class="java.lang.String" scope="request"/>
<jsp:useBean id="expand" class="java.lang.String" scope="request"/>
<c:if test="${expand eq 'true'}">
<style>
    #form${widgetContext.uniqueId} input[type=search] {
        display: none;
        position: absolute;
        top: 75px;
        left: 50%;
        -webkit-transform: translateX(-50%);
        -ms-transform: translateX(-50%);
        transform: translateX(-50%);
        height: 45px;
        max-width: 500px;
        width: 100%;
        font-size: large;
        border-radius: 12px;
        padding: 2px 12px 2px 10px;
        z-index:10000;
    }
    #form${widgetContext.uniqueId} input[type=search].isExpanded {
        display: unset;
    }
    #form${widgetContext.uniqueId} .button.search {
        height: 24px;
        margin: 5px 0 0 0;
        background-color: transparent;
        padding: 2px;
        border: none;
    }
</style>
</c:if>
<form id="form${widgetContext.uniqueId}" method="get" action="${ctx}/search?widget=results1">
  <div class="input-group no-gap">
    <input id="input${widgetContext.uniqueId}" class="input-group-field" type="search"<c:if test="${expand ne 'true'}"> placeholder="<c:out value="${placeholder}" />"</c:if> name="query">
    <div class="input-group-button">
      <button id="button${widgetContext.uniqueId}" type="submit" class="button search"><i id="icon${widgetContext.uniqueId}" class="fa fa-search"></i><c:out value="${linkText}" /></button>
    </div>
  </div>
</form>
<c:if test="${expand eq 'true'}">
<script>
    $(document).ready(function () {
        let form = $('#form${widgetContext.uniqueId}');
        let button = $('#button${widgetContext.uniqueId}');
        let input = $('#input${widgetContext.uniqueId}');
        let icon = $('#icon${widgetContext.uniqueId}');
        function showSearchForm${widgetContext.uniqueId}() {
            input.addClass('isExpanded');
            input.attr("placeholder", "${js:escape(placeholder)}");
            input.focus();
        }
        function hideSearchForm${widgetContext.uniqueId}() {
            input.removeClass('isExpanded');
            input.attr("placeholder", "");
        }
        button.click(function (event) {
            if (!input.hasClass('isExpanded')) {
                showSearchForm${widgetContext.uniqueId}();
            } else {
                hideSearchForm${widgetContext.uniqueId}();
            }
        });
        input.focusout(function () {
            setTimeout(function () {
                hideSearchForm${widgetContext.uniqueId}();
            }, 150);
        });
        form.submit(function(e){
            if (!input.val()) {
                e.preventDefault(e);
            }
        });
    });
</script>
</c:if>