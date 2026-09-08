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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="statisticsDataList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<jsp:useBean id="value" class="java.lang.String" scope="request"/>
<jsp:useBean id="optionsList" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="currentValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="asOfDate" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty optionsList}">
  <ul class="tabs" id="tabs${widgetContext.uniqueId}">
    <c:forEach items="${optionsList}" var="option" varStatus="status">
      <li id="val<c:out value="${option.value}"/>-${widgetContext.uniqueId}" class="tabs-title<c:if test="${(empty currentValue and status.first) or option.value eq currentValue}"> is-active</c:if>"><a href="#" class="js-updateStats${widgetContext.uniqueId}" data-value="<c:out value="${option.value}"/>"><c:out value="${option.key}"/></a></li>
    </c:forEach>
  </ul>
</c:if>
<div id="stats${widgetContext.uniqueId}">
<table class="unstriped" id="table${widgetContext.uniqueId}">
  <thead>
    <tr>
      <th><c:out value="${label}" /></th>
      <th class="text-center"><c:out value="${value}" /></th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${statisticsDataList}" var="data">
    <tr>
      <td><c:out value="${data.label}" /></td>
      <td class="text-center">
        <c:choose>
          <%-- Most reports put a plain number here (Hits, Submissions, Searches...), but a few
               (avg-time-on-page, high/low-traffic-engagement) put pre-formatted display text like
               "33.8s" or "185 hits, 33.8s avg" -- fmt:formatNumber throws on that and, uncaught,
               takes down this entire page's render, not just this cell. --%>
          <c:when test="${text:isNumeric(data.value)}"><fmt:formatNumber value="${data.value}" /></c:when>
          <c:otherwise><c:out value="${data.value}" /></c:otherwise>
        </c:choose>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty statisticsDataList}">
      <tr>
        <td colspan="2">Data was not found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<c:if test="${!empty asOfDate}">
  <p class="text-right"><small>As of <c:out value="${asOfDate}" /></small></p>
</c:if>
<c:if test="${!empty optionsList}">
  <%-- Revealed by the script below once auto-refresh gives up. Rendered hidden rather than built in
       JavaScript so the text is in the accessibility tree before role="alert" fires. --%>
  <div class="callout radius warning" id="stopped${widgetContext.uniqueId}" role="alert" hidden>
    <p class="text-center" style="margin-bottom:0">Live updates stopped. Your session may have expired &mdash; reload the page to resume.</p>
  </div>
</c:if>
</div>
<c:if test="${!empty optionsList}">
<script nonce="${cspNonce}">
  // Interval to update the highlighted tab data.
  //
  // Everything declared here carries the widget's unique id for the same reason the tab ids do, a
  // few lines above: an admin page renders several of these tables side by side. Four names were
  // left unsuffixed and were therefore genuine page globals, redeclared by every tabbed instance
  // so the last one rendered won (issue #1922). On /admin/content/analytics that meant Top Pages
  // never auto-refreshed at all -- its interval handle and its interval function were both replaced
  // by Top Downloads' before either could fire, and the access logs showed only Top Downloads ever
  // polling. The two also shared one currentValue, so an interval fired for one could query the
  // range last chosen on the other, and clearInterval in either one's query() cancelled the other's
  // pending refresh.
  //
  // The variables that must stay per-widget are exactly the ones that outlive a single call:
  // currentValue (the selected range), updateIntervalFunction and updateInterval (the timer), and
  // buildItemRow (a row formatter, which was harmless in practice only because every instance
  // defined it identically). Names declared inside a function -- div, items, el -- are already
  // function-scoped and do not collide.
  var currentValue${widgetContext.uniqueId} = '<c:out value="${empty currentValue ? optionsList.entrySet().toArray()[0].value : currentValue}"/>';
  var updateIntervalFunction${widgetContext.uniqueId} = function() {
    query${widgetContext.uniqueId}(currentValue${widgetContext.uniqueId});
  };
  // Declared without a timer: polling starts only once the first query succeeds, so a widget whose
  // very first request fails never schedules one. The commented-out "start immediately" line that
  // used to sit here was removed rather than left as documentation -- it carried its own copy of the
  // interval, which would quietly go stale the next time the real one below changes.
  var updateInterval${widgetContext.uniqueId};

  // Auto-refresh gives up after this many consecutive failures instead of retrying forever.
  // A failure here is usually permanent, not transient: the form token baked into the URL below is
  // this session's, and several admin widgets renew it as they render, so opening any of those pages
  // leaves this tab holding a token PageServlet will refuse for the rest of the tab's life. Retrying
  // that on a timer is not a retry -- it is one request every 30 seconds, forever, invisible to
  // whoever left the tab open (issue #1920).
  var maxFailures${widgetContext.uniqueId} = 3;
  var failures${widgetContext.uniqueId} = 0;

  // Toggle the "live updates stopped" callout rendered above
  function showStopped${widgetContext.uniqueId}(stopped) {
    var el = document.getElementById("stopped${widgetContext.uniqueId}");
    if (el) {
      el.hidden = !stopped;
    }
  }

  // Escape a label for safe insertion into the table markup (labels can be user-provided, e.g. search
  // terms or referrers)
  function escapeHtml${widgetContext.uniqueId}(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : text;
    return div.innerHTML;
  }

  // Update the table data
  function buildItemRow${widgetContext.uniqueId}(item) {
    return "<tr><td>" + escapeHtml${widgetContext.uniqueId}(item.label) + "</td><td class=\"text-center\">" + parseFloat(item.value).toLocaleString() + "</td></tr>";
  }

  // Query the data
  function query${widgetContext.uniqueId}(value) {
    // Turn off the interval
    if (updateInterval${widgetContext.uniqueId}) {
      clearInterval(updateInterval${widgetContext.uniqueId});
      updateInterval${widgetContext.uniqueId} = null;
    }
    // Query the new data
    $.ajax({
      url: '${widgetContext.uri}?widget=${widgetContext.uniqueId}&action=get&value=' + value + '&token=${userSession.formToken}',
      type: 'GET',
      dataType: 'json',
      cache: false,
      // complete: function() {
      // },
      timeout: 5000
    }).done(function(data) {
      // Remove the old data
      $("#table${widgetContext.uniqueId} tbody").remove();
      // Build the new data
      var items = [];
      $.each(data, function (key, item) {
        items.push(buildItemRow${widgetContext.uniqueId}(item));
      });
      // Add the new data
      $('<tbody/>', {
        html: items.join('')
      }).appendTo('#table${widgetContext.uniqueId}');
      // Recovered -- spend the failure budget again from full
      failures${widgetContext.uniqueId} = 0;
      showStopped${widgetContext.uniqueId}(false);
      // Turn on the interval.
      //
      // 60s, raised from 10s, because suffixing the globals above is a real behaviour change and
      // not just hygiene: today exactly ONE report per page polls, and now every tabbed one does.
      // Per open tab, at the old 10s:
      //
      //   /admin/community/analytics          7 tabbed reports   360/hr -> 2,520/hr
      //   /admin/community/search-analytics   3 tabbed reports   360/hr -> 1,080/hr
      //   /admin/content/analytics            2 tabbed reports   360/hr ->   720/hr
      //
      // Each of those requests runs an analytics query. At 60s the busiest page lands at 420/hr,
      // near the 360/hr it costs today, so the fix does not buy a working refresh at the price of
      // seven times the database load.
      //
      // 10s was never warranted by the data anyway. These are daily and monthly aggregates -- the
      // number behind "Monthly Sessions" cannot change meaningfully between two ten-second polls.
      // If a page ever needs a faster cadence, make it a widget preference rather than raising it
      // for every report on every admin page at once.
      updateInterval${widgetContext.uniqueId} = setInterval(updateIntervalFunction${widgetContext.uniqueId}, 60000);
    }).fail(function() {
      ++failures${widgetContext.uniqueId};
      if (failures${widgetContext.uniqueId} >= maxFailures${widgetContext.uniqueId}) {
        // Out of budget: leave the interval off, and say so rather than failing silently
        showStopped${widgetContext.uniqueId}(true);
        return;
      }
      // Turn on the interval
      updateInterval${widgetContext.uniqueId} = setInterval(updateIntervalFunction${widgetContext.uniqueId}, 30000);
    });
  }

  function update${widgetContext.uniqueId}(value) {
    // Update the highlighted tab class. The tab ids carry the widget's unique id because an admin
    // page renders several of these tables side by side offering the same ranges -- without the
    // suffix every one of them emits its own "val30d" into the same document.
    $("#tabs${widgetContext.uniqueId} li").each(function(idx, li) {
      if (li.id === 'val' + value + '-${widgetContext.uniqueId}') {
        if (!li.matches('.is-active')) {
          li.className = li.className + ' is-active';
          currentValue${widgetContext.uniqueId} = value;
        }
      } else {
        if (li.matches('.is-active')) {
          li.className = li.className.replace(/\s*\bis-active\b/, "");
        }
      }
    });
    // Picking a range is a deliberate retry, so hand back a full budget -- otherwise a tab that had
    // already given up could never be restarted without reloading the page
    failures${widgetContext.uniqueId} = 0;
    showStopped${widgetContext.uniqueId}(false);
    query${widgetContext.uniqueId}(value);
  }

  document.querySelectorAll(".js-updateStats${widgetContext.uniqueId}").forEach(function (el) {
    el.addEventListener("click", function (event) {
      event.preventDefault();
      update${widgetContext.uniqueId}(el.dataset.value);
    });
  });
</script>
</c:if>