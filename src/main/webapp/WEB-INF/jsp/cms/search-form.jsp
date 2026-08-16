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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
    /* Collapsed state: only the trigger icon shows, inline in the header's normal flow -- plain
       icon, no button chrome (matches every other icon-only header control, e.g. colorSchemeToggle).
       .close-group (the wrapper, not just .search-close inside it) has to be display:none here --
       a hidden button still leaves its wrapping .input-group-button occupying height in the flex
       row otherwise, which was throwing off the search icon's own vertical centering next to it. */
    #group${widgetContext.uniqueId} input[type=search],
    #group${widgetContext.uniqueId} .close-group {
        display: none;
    }
    #group${widgetContext.uniqueId} .button.search {
        height: 24px;
        margin: 5px 0 0 0;
        background-color: transparent;
        padding: 2px;
        border: none;
    }
    /* Expanded state: the whole input group becomes a full-width bar anchored just below the
       header (see positionSearchOverlay${widgetContext.uniqueId}() -- the header's own height
       varies by header layout and by whether Foundation's sticky nav has pinned it to the
       viewport, so the offset is measured at reveal time rather than a fixed guess). */
    #group${widgetContext.uniqueId}.isExpanded {
        display: flex;
        align-items: center;
        position: fixed;
        left: 0;
        width: 100%;
        margin: 0;
        padding: 16px 24px;
        background-color: #ffffff;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
        z-index: 10000;
    }
    #group${widgetContext.uniqueId}.isExpanded input[type=search] {
        display: block;
        flex: 1 1 auto;
        height: 45px;
        font-size: 1.1rem;
        border: none;
        box-shadow: none;
        background: transparent;
        /* The theme's own `.button { color: var(--sc-button-text-color) }` token rule (usually
           white, for buttons on the dark nav) otherwise wins here despite this selector's higher
           specificity -- rule order across the page's many <style> blocks isn't guaranteed, so
           this narrowly-scoped override forces it rather than depending on where in the page the
           token rule happens to load relative to this one. */
        color: #0a0a0a !important;
        padding: 0 16px;
        margin: 0;
    }
    #group${widgetContext.uniqueId}.isExpanded input[type=search]:focus {
        outline: none;
        box-shadow: none;
    }
    /* Chrome/Safari render their own clear-x inside type=search once it has text, which would sit
       right next to .search-close below and read as a confusing double close control. */
    #group${widgetContext.uniqueId}.isExpanded input[type=search]::-webkit-search-cancel-button {
        -webkit-appearance: none;
        appearance: none;
    }
    #group${widgetContext.uniqueId}.isExpanded .input-group-button {
        flex: 0 0 auto;
    }
    #group${widgetContext.uniqueId}.isExpanded .close-group {
        display: flex;
    }
    #group${widgetContext.uniqueId}.isExpanded .button {
        height: 32px;
        width: 32px;
        margin: 0 0 0 8px;
        background-color: transparent;
        color: #0a0a0a !important;
        padding: 0;
        border: none;
        font-size: 1.1rem;
    }
    /* #platform-menu button.button i.fa (theme.css) forces every header icon-button's glyph white
       to read against the dark nav -- right everywhere else, wrong here now that these two buttons
       sit on this bar's own white background instead. A child's own color rule doesn't inherit
       from its parent regardless of the parent's color, so overriding .button above never reached
       the icon itself; this has to target i.fa directly, matching that rule's own specificity. */
    #group${widgetContext.uniqueId}.isExpanded .button i.fa {
        color: #0a0a0a !important;
    }
</style>
</c:if>
<form id="form${widgetContext.uniqueId}" method="get" action="${ctx}/search?widget=results1">
  <div id="group${widgetContext.uniqueId}" class="input-group no-gap">
    <input id="input${widgetContext.uniqueId}" class="input-group-field" type="search"<c:if test="${expand ne 'true'}"> placeholder="<c:out value="${placeholder}" />"</c:if> name="query">
    <c:if test="${expand eq 'true'}">
    <div class="input-group-button close-group">
      <button id="close${widgetContext.uniqueId}" type="button" class="button search-close" aria-label="Close search"><i class="fa fa-times"></i></button>
    </div>
    </c:if>
    <div class="input-group-button">
      <button id="button${widgetContext.uniqueId}" type="submit" class="button search"><i id="icon${widgetContext.uniqueId}" class="fa fa-search"></i><c:out value="${linkText}" /></button>
    </div>
  </div>
</form>
<c:if test="${expand eq 'true'}">
<script nonce="${cspNonce}">
    $(document).ready(function () {
        let form = $('#form${widgetContext.uniqueId}');
        let group = $('#group${widgetContext.uniqueId}');
        let button = $('#button${widgetContext.uniqueId}');
        let close = $('#close${widgetContext.uniqueId}');
        let input = $('#input${widgetContext.uniqueId}');
        function positionSearchOverlay${widgetContext.uniqueId}() {
            // #platform-menu wraps every header layout (layout-header-renderer.jspf) and is what
            // Foundation's sticky nav pins to the viewport on scroll -- getBoundingClientRect() is
            // viewport-relative either way, matching this overlay's own position:fixed, so the bar
            // lands directly under the header whether or not it's currently stuck.
            let header = document.querySelector('#platform-menu');
            let top = header ? header.getBoundingClientRect().bottom : 0;
            group.css('top', top + 'px');
        }
        function showSearchForm${widgetContext.uniqueId}() {
            positionSearchOverlay${widgetContext.uniqueId}();
            group.addClass('isExpanded');
            input.attr("placeholder", "${js:escape(placeholder)}");
            input.focus();
        }
        function hideSearchForm${widgetContext.uniqueId}() {
            group.removeClass('isExpanded');
            input.attr("placeholder", "");
        }
        button.click(function (event) {
            if (!group.hasClass('isExpanded')) {
                showSearchForm${widgetContext.uniqueId}();
            } else if (!input.val()) {
                // Nothing typed yet -- treat the icon as a close, matching the pre-overlay behavior
                // (blocked by the submit handler below anyway, but this skips the round trip).
                hideSearchForm${widgetContext.uniqueId}();
            }
        });
        close.click(function (event) {
            hideSearchForm${widgetContext.uniqueId}();
        });
        input.focusout(function () {
            setTimeout(function () {
                hideSearchForm${widgetContext.uniqueId}();
            }, 150);
        });
        $(window).on('resize', function () {
            if (group.hasClass('isExpanded')) {
                positionSearchOverlay${widgetContext.uniqueId}();
            }
        });
        form.submit(function(e){
            if (!input.val()) {
                e.preventDefault(e);
            }
        });
    });
</script>
</c:if>
