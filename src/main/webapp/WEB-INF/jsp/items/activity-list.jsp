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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="activityList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4 class="platform-activity-title"><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div id="platform-activity-list${widgetContext.uniqueId}" class="platform-scrollable-view">
<c:if test="${empty activityList}">
  <p id="no-messages-found" class="subheader">
    No messages were found.<br />
    Write a message for others in this space to see.
  </p>
</c:if>
<c:set var="lastDate" scope="request" value="---"/>
<ul id="platform-activity-ul" class="no-bullet">
  <c:if test="${!empty activityList && recordPaging.totalRecordCount ne activityList.size()}">
    <li id="moreRecords" class="text-center">
      <small>
        continued...
        <c:choose>
          <c:when test="${recordPaging.totalRecordCount - activityList.size() == 1}">
            there is 1 older activity
          </c:when>
          <c:otherwise>
            there are ${recordPaging.totalRecordCount - activityList.size()} older activities
          </c:otherwise>
        </c:choose>
      </small>
    </li>
  </c:if>
  <c:forEach items="${activityList}" var="activity">
    <c:if test="${lastDate ne date:formatMonthDayYear(activity.created)}">
      <li class="platform-activity-date">
        <span class="label radius secondary"><c:out value="${date:formatMonthDayYear(activity.created)}"/></span>
      </li>
    </c:if>
    <c:set var="lastDate" scope="request" value="${date:formatMonthDayYear(activity.created)}"/>
    <li class="clear-float">
      <p class="platform-activity-image">
        <img src="${systemPropertyMap['system.www.context']}/images/apple-touch-icon.png" />
      </p>
      <p class="platform-activity-content">
        <c:if test="${activity.createdBy gt 0}">
          <span class="platform-activity-content-name"><c:out value="${user:name(activity.createdBy)}"/></span>
        </c:if>
        <c:if test="${!empty activity.source}">
          <span class="platform-activity-content-name"><c:out value="${activity.source}" /></span>
          <span class='label secondary radius'>APP</span>
        </c:if>
        <span class="platform-activity-content-date">${date:formatTime(activity.created)}</span>
        <br/>
        <c:choose>
          <c:when test="${activity.activityType eq 'CHAT'}">
            ${activity.messageHtml}
          </c:when>
          <c:otherwise>
            <span class="platform-activity-content-other">${activity.messageHtml}</span>
          </c:otherwise>
        </c:choose>
      </p>
    </li>
  </c:forEach>
</ul>
</div>
<script>
  // Enable notifications
  if ("Notification" in window) {
    if (Notification.permission !== 'denied') {
      Notification.requestPermission();
    }
  }
  function notify(text) {
    if (!("Notification" in window)) {
      return;
    }
    var img = '${systemPropertyMap['system.www.context']}/images/apple-touch-icon.png';
    if (Notification.permission === "granted") {
      var notification = new Notification('${js:escape(item.name)}', { body: text, icon: img });
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission(function (permission) {
        if (permission === "granted") {
          var notification = new Notification('${js:escape(item.name)}', { body: text, icon: img });
        }
      });
    }
  }
</script>
<script>
  // Polling
  String.prototype.toHtmlEntities = function() {
    return this.replace(/./gm, function(s) {
      return "&#" + s.charCodeAt(0) + ";";
    });
  };

  <%-- https://medium.com/@heatherbooker/how-to-auto-scroll-to-the-bottom-of-a-div-415e967e7a24 --%>
  var autoScrollElement = document.getElementById("platform-activity-list${widgetContext.uniqueId}");
  var autoScrollElementTop = 0;
  var doScroll = true;
  var minTimestamp = ${minTimestamp};
  var activityDate = '${activityDate}';
  var ul = document.getElementById("platform-activity-ul");

  function animateScroll(duration) {
    var start = autoScrollElement.scrollTop;
    var end = autoScrollElement.scrollHeight;
    var change = end - start;
    var increment = 20;

    function easeInOut(currentTime, start, change, duration) {
      currentTime /= duration / 2;
      if (currentTime < 1) {
        return change / 2 * currentTime * currentTime + start;
      }
      currentTime -= 1;
      return -change / 2 * (currentTime * (currentTime - 2) - 1) + start;
    }

    function animate(elapsedTime) {
      elapsedTime += increment;
      var position = easeInOut(elapsedTime, start, change, duration);
      autoScrollElement.scrollTop = position;
      if (elapsedTime < duration) {
        setTimeout(function () {
          animate(elapsedTime);
          autoScrollElementTop = autoScrollElement.scrollTop;
        }, increment)
      }
    }
    if (doScroll) {
      animate(0);
    }
  }

  function jumpToBottom() {
    autoScrollElement.scrollTop = autoScrollElement.scrollHeight;
    autoScrollElementTop = autoScrollElement.scrollTop;
  }

  function scrollToBottom() {
    animateScroll(300);
  }

  if (autoScrollElement) {
    jumpToBottom();
    scrollToBottom();
    var observer = new MutationObserver(scrollToBottom);
    observer.observe(autoScrollElement, {
      childList: true,
      subtree: true
    });
  }

  (function poll() {
    setTimeout(function () {
      $.ajax({
        url: "${ctx}/json/activityList?itemId=${item.id}&minTimestamp=" + minTimestamp,
        type: "GET",
        error: function (jq, text) {
          //alert("an error occurred: " + text);
        },
        success: function (data) {
          if (data.minTimestamp) {
            minTimestamp = data.minTimestamp;
          }
          if (data.activityList && data.activityList.length > 0) {
            // @todo find a reliable way to know if the user scrolled up
            // doScroll = (autoScrollElement.scrollTop == autoScrollElementTop);

            var noActivitiesMessage = document.getElementById("no-messages-found");
            if (noActivitiesMessage) {
              noActivitiesMessage.outerHTML = "";
            }

            for (var i = 0; i < data.activityList.length; i++) {
              var activity = data.activityList[i];
              var isChat = activity.type && activity.type == 'CHAT';
              if (activity.date != activityDate) {
                activityDate = activity.date;
                var dateLi = document.createElement("li");
                dateLi.setAttribute("class", "platform-activity-date");
                dateLi.innerHTML = "<span class=\"label radius secondary\">" + activity.date + "</span>";
                ul.appendChild(dateLi);
              }
              var content =

                "<p class=\"platform-activity-image\">" +
                "<img src=\"${systemPropertyMap['system.www.context']}/images/apple-touch-icon.png\" />\n" +
                "</p>" +
                "<p class=\"platform-activity-content\">" +
                (activity.user ? "<span class=\"platform-activity-content-name\">" + activity.user.toHtmlEntities() + "</span> " : "") +
                (activity.source ? "<span class=\"platform-activity-content-name\">" + activity.source.toHtmlEntities() + "</span> " +
                  "<span class='label secondary radius'>APP</span> " : "") +
                "<span class=\"platform-activity-content-date\">" + activity.time + "</span>" +
                "<br/>" +
                (isChat ? activity.messageHtml : "<span class=\"platform-activity-content-other\">" + activity.messageHtml + "</span>" +
                "</p>");

              var li = document.createElement("li");
              li.className = 'clear-float';
              li.innerHTML = content;
              ul.appendChild(li);
            }

            if (data.activityList.length == 1) {
              notify("A new message arrived");
            } else {
              notify(data.activityList.length + " new messages arrived");
            }
          }
        },
        dataType: "json",
        complete: poll,
        timeout: 2000
      })
    }, 5000);
  })();
</script>
<script>
    $(document).ready(function () {
        function resizeEditor() {
            var container = document.getElementById("platform-activity-list${widgetContext.uniqueId}");
            var rect = container.getBoundingClientRect(),
                scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
                scrollTop = window.pageYOffset || document.documentElement.scrollTop;
            $('#platform-activity-list${widgetContext.uniqueId}').height($(window).height() - Math.round(rect.top + scrollTop + 108));
        }
        $(window).resize(resizeEditor);
        resizeEditor();
    });
</script>