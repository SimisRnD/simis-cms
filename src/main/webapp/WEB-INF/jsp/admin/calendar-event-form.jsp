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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarEvent" class="com.simisinc.platform.domain.model.cms.CalendarEvent" scope="request"/>
<jsp:useBean id="tagsListValue" class="java.lang.String" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${calendarEvent.id}"/>
  <input type="hidden" name="calendarId" value="${calendarEvent.calendarId}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <p class="help-text page-help">
    <i class="fa fa-info-circle"></i> <strong>No recurring events.</strong> This is a single,
    independent event -- there's no way to make it repeat weekly/monthly automatically. A calendar's
    own full page view offers a "Duplicate" button, but that only creates one more standalone copy,
    not a linked series.
  </p>
  <%-- Form Content --%>
  <label>Name
    <input type="text" placeholder="Name of event" name="title" maxlength="255" value="<c:out value="${calendarEvent.title}"/>">
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="summary" value="<c:out value="${calendarEvent.summary}"/>">
  </label>
  <label>All day?
    <div class="switch large">
      <input class="switch-input" id="allDay-yes-no" type="checkbox" name="allDay" value="true"<c:if test="${calendarEvent.allDay}"> checked</c:if>>
      <label class="switch-paddle" for="allDay-yes-no">
        <span class="switch-active" aria-hidden="true">Yes</span>
        <span class="switch-inactive" aria-hidden="true">No</span>
      </label>
    </div>
  </label>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label for="startDate">Start <span class="js-date-label">Date/Time</span>
        <div class="input-group">
          <input type="text" placeholder="Click to select..." id="startDate" name="startDate" value="<c:out value="${date:formatDateTimeInput(calendarEvent.startDate)}"/>" readonly aria-label="Select event start date and time" />
          <span class="input-group-addon">
            <i class="fa fa-calendar"></i>
          </span>
        </div>
      </label>
      <small class="help-text"><i class="fa fa-info-circle"></i> Format: <span class="js-date-hint">mm-dd-yyyy hh:ii (e.g., 07-26-2026 14:30)</span></small>
    </div>
    <div class="small-12 medium-6 cell">
      <label for="endDate">End <span class="js-date-label">Date/Time</span>
        <div class="input-group">
          <input type="text" placeholder="Click to select..." id="endDate" name="endDate" value="<c:out value="${date:formatDateTimeInput(calendarEvent.endDate)}"/>" readonly aria-label="Select event end date and time" />
          <span class="input-group-addon">
            <i class="fa fa-calendar"></i>
          </span>
        </div>
      </label>
      <small class="help-text"><i class="fa fa-info-circle"></i> <span class="js-end-hint">Must be after start time</span></small>
    </div>
  </div>
  <link rel="stylesheet" href="${ctx}/javascript/foundation-datepicker-20180424/foundation-datepicker.css" />
  <script src="${ctx}/javascript/foundation-datepicker-20180424/foundation-datepicker.js"></script>
  <script nonce="${cspNonce}">
    $(function () {
      // An all-day event has no meaningful time, so the picker drops to a date-only view when
      // "All day?" is on. The value on the wire still has to carry a time either way:
      // PageServlet registers the Timestamp converter with pattern "MM-dd-yyyy HH:mm", and a
      // date-only string converts to null, which fails the save.
      var DATE_ONLY = 'mm-dd-yyyy';
      var DATE_TIME = 'mm-dd-yyyy hh:ii';
      var TIME_SUFFIX = / \d{1,2}:\d{2}$/;
      var $allDay = $('#allDay-yes-no');
      var $fields = $('#startDate, #endDate');

      function isAllDay() {
        return $allDay.is(':checked');
      }

      function withoutTime(value) {
        return $.trim(value).replace(TIME_SUFFIX, '');
      }

      function withTime(value) {
        var trimmed = $.trim(value);
        if (trimmed === '' || TIME_SUFFIX.test(trimmed)) {
          return trimmed;
        }
        return trimmed + ' 00:00';
      }

      function applyMode() {
        var allDay = isAllDay();
        $fields.each(function () {
          var $field = $(this);
          // Re-create rather than reconfigure: fdatepicker reads format/pickTime once, at
          // construction. 'remove' detaches its handlers and clears the element's data.
          if ($field.data('datepicker')) {
            $field.fdatepicker('remove');
          }
          // Convert the current value first, so the rebuilt picker can parse its own field.
          $field.val(allDay ? withoutTime($field.val()) : withTime($field.val()));
          $field.attr('aria-label', $field.attr('id') === 'startDate'
              ? (allDay ? 'Select event start date' : 'Select event start date and time')
              : (allDay ? 'Select event end date' : 'Select event end date and time'));
          $field.fdatepicker({
            format: allDay ? DATE_ONLY : DATE_TIME,
            disableDblClickSelection: true,
            pickTime: !allDay
          });
        });
        $('.js-date-label').text(allDay ? 'Date' : 'Date/Time');
        $('.js-date-hint').text(allDay
            ? 'mm-dd-yyyy (e.g., 07-26-2026)'
            : 'mm-dd-yyyy hh:ii (e.g., 07-26-2026 14:30)');
        $('.js-end-hint').text(allDay
            ? 'Same day as the start, or later'
            : 'Must be after start time');
      }

      applyMode();
      $allDay.on('change', applyMode);

      // The field shows only a date in all-day mode; put midnight back before it is submitted.
      $allDay.closest('form').on('submit', function () {
        if (isAllDay()) {
          $fields.each(function () {
            $(this).val(withTime($(this).val()));
          });
        }
      });
    });
  </script>
  <label>Location
    <input type="text" placeholder="Name of Location" name="location" value="<c:out value="${calendarEvent.location}"/>">
  </label>
  <small class="help-text"><i class="fa fa-info-circle"></i> The venue's name (e.g. "Main Auditorium" or "Zoom"). Put the street address in the fields below rather than in here -- search engines read them as a structured address, and a whole address typed into this one box is just an opaque string to them.</small>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>Street address
        <input type="text" placeholder="Street" name="street" value="<c:out value="${calendarEvent.street}"/>">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>City
        <input type="text" placeholder="City" name="city" value="<c:out value="${calendarEvent.city}"/>">
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-4 cell">
      <label>State / region
        <input type="text" placeholder="State" name="state" value="<c:out value="${calendarEvent.state}"/>">
      </label>
    </div>
    <div class="small-12 medium-4 cell">
      <label>Postal code
        <input type="text" placeholder="Postal Code" name="postalCode" value="<c:out value="${calendarEvent.postalCode}"/>">
      </label>
    </div>
    <div class="small-12 medium-4 cell">
      <label>Country
        <input type="text" placeholder="Country" name="country" value="<c:out value="${calendarEvent.country}"/>">
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>URL for more information
        <input type="text" placeholder="Details Url" name="detailsUrl" value="<c:out value="${calendarEvent.detailsUrl}"/>">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>URL to sign up
        <input type="text" placeholder="Sign Up Url" name="signUpUrl" value="<c:out value="${calendarEvent.signUpUrl}"/>">
      </label>
    </div>
  </div>
  <label>Video / Meeting Link
    <input type="text" placeholder="https://..." name="videoUrl" value="<c:out value="${calendarEvent.videoUrl}"/>">
  </label>
  <small class="help-text"><i class="fa fa-info-circle"></i> Paste a link to a video or live meeting (Teams, Zoom, Google Meet, a YouTube stream, etc). Shown as a "Join" button on the event's page.</small>
  <label>Tags
    <input type="text" placeholder="conference, quarterly, all-hands" name="tagsList" value="<c:out value="${tagsListValue}"/>" maxlength="255">
  </label>
  <small class="help-text"><i class="fa fa-info-circle"></i> Comma-separated list of tags for this event (e.g. "conference, quarterly, all-hands"). Limited to 255 characters total.</small>
  <p>
    <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${!empty calendarEvent.published}">checked</c:if>/><label for="enabled">Publish it?</label>
    <br/><small class="help-text"><i class="fa fa-info-circle"></i> Unchecked saves this event as a draft, hidden from the public calendar.</small>
  </p>
  <div class="button-container">
    <c:choose>
      <c:when test="${!empty returnPage}">
        <input type="submit" class="button radius primary" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius primary expanded" value="Save"/>
      </c:otherwise>
    </c:choose>
  </div>
</form>
