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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%-- Full Calendar --%>
<link rel="stylesheet" href="${ctx}/css/fullcalendar-3.9.0/fullcalendar.min.css" />
<link rel="stylesheet" href="${ctx}/css/platform-calendar.css?v=<%= VERSION %>" />
<script src="${ctx}/javascript/fullcalendar-3.9.0/moment.min.js"></script>
<script src="${ctx}/javascript/fullcalendar-3.9.0/fullcalendar.min.js"></script>
<%-- Render the widget --%>
<div id="calendar-small"></div>
<script>
  $(function () {
    $('#calendar-small').fullCalendar({
      header: {
        left:   'title',
        center: '',
        right:  'today prev,next'
      },
      // defaultDate: today,
      selectable: false,
      selectHelper: false,
      // aspectRatio: 1,
      height: 600,
      defaultView: 'month',
      fixedWeekCount: true,
      views: {
        month: {
          titleFormat: 'MMMM YYYY'
        }
      },
      select: function (start, end) {

        alert('You clicked to view a date');

        /*
        // on select we show the Sweet Alert modal with an input
        swal({
          title: 'Create an Event',
          html: '<br><input class="form-control" placeholder="Event Title" id="input-field">',
          showCancelButton: true,
          closeOnConfirm: true
        }, function () {

          var eventData;
          event_title = $('#input-field').val();

          if (event_title) {
            eventData = {
              title: event_title,
              start: start,
              end: end
            };
            $calendar.fullCalendar('renderEvent', eventData, true); // stick? = true
          }

          $calendar.fullCalendar('unselect');

        });
        */

      },
      editable: false,
      eventLimit: true, // allow "more" link when too many events
      eventSources: [
        // {
        //   url: '/myfeed.php',
        //   data: {
        //     custom_param1: 'something',
        //     custom_param2: 'somethingelse'
        //   },
        //   color: '#E3F3FA',
        //   color: '#F5DDDD',
        //   color: '#ECF4DB',
        //   color: '#FAEEDE',
        //   textColor: '#000000'
        // },
        // { googleCalendarId: 'abcd1234@group.calendar.google.com' },
        {
          events: [
            {
              id: 1,
              title: 'Conference',
              start: '2018-04-18',
              end: '2018-04-25'
              // className: 'event-blue'
            },
            {
              id: 3,
              title: 'Busch Gardens (link)',
              start: '2018-04-28T10:00:00',
              end: '2018-04-28T19:30:00',
              url: 'http://buschgardens.com/',
              allDay: false
            }
          ],
          // color: '#DEE5FA',
          // textColor: '#000000'
          color: '#79C554',
          textColor: '#ffffff'
        }
      ],
      eventClick: function(event) {
        if (event.url) {
          window.open(event.url);
          return false;
        } else {
          alert('You clicked on event id ' + event.id);
        }
      }
    });
  });
</script>