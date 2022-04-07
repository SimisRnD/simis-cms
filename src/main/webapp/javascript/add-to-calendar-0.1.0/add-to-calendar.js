;(function(exports) {

  /* Config */

  var MS_IN_MINUTES = 60 * 1000;
    
  var CONFIG = {
    selector  : ".add-to-calendar",
    duration  : 60,
    texts : {
      label    : "Add to Calendar",
      title    : "New event",
      download : "Calendar-event.ics",
      google   : "Google Calendar",
      yahoo    : "Yahoo! Calendar",
      off365   : "Office 365",
      outlkcom : "Outlook.com",
      ical     : "Apple iCalendar",
      outlook  : "Outlook",
      ienoblob : "Sorry, your browser does not support downloading Calendar events."
    }
  };
  
  if (typeof ADDTOCAL_CONFIG != "undefined") {
    CONFIG = ADDTOCAL_CONFIG;
  }

  /* Browser Sniffing */

  // ie < edg (=chromium) doesnt support data-uri:text/calendar
  var ieCanDownload = ('msSaveOrOpenBlob' in window.navigator);
  var ieMustDownload = /\b(MSIE |Trident.*?rv:|Edge\/)(\d+)/.exec(navigator.userAgent);


  /* Generators */
  
  var calendarGenerators = {
  
    google: function(event) {
      var startTime,endTime;
      
      if (event.allday) {
        // google wants 2 consecutive days at 00:00
        startTime = formatTime(event.tzstart);
        endTime = formatTime(getEndDate(event.tzend,60*24));
        startTime = stripISOTime(startTime);
        endTime = stripISOTime(endTime);
      } else {
        if (event.timezone) {
          // google is somehow weird with timezones. 
          // it works better when giving the local
          // time in the given timezone without the zulu, 
          // and pass timezone as argument.
          // but then the dates we have loaded 
          // need to shift inverse with tzoffset the 
          // browser gave us. 
          var shiftstart, shiftend;
          shiftstart = new Date(event.start.getTime()-event.start.getTimezoneOffset()*MS_IN_MINUTES);
          if (event.end) {
            shiftend = new Date(event.end.getTime()-event.end.getTimezoneOffset()*MS_IN_MINUTES);
          }
          startTime = formatTime(shiftstart);
          endTime = formatTime(shiftend);
          // strip the zulu and pass the tz as argument later
          startTime = startTime.substring(0,startTime.length-1);
          endTime = endTime.substring(0,endTime.length-1);
        } else {
          // use regular times
          startTime = formatTime(event.start);
          endTime = formatTime(event.end);
        }
      }
      
      var href = ([
        'https://www.google.com/calendar/render',
        '?action=TEMPLATE',
        '&text=' + encodeURIComponent(event.title || ''),
        '&dates=' + encodeURIComponent(startTime || ''),
        '/' + encodeURIComponent(endTime || ''),
        (event.timezone)?'&ctz='+event.timezone:'',
        '&details=' + encodeURIComponent(event.description || ''),
        '&location=' + encodeURIComponent(event.address || ''),
        '&sprop=&sprop=name:'
      ].join(''));
      
      return '<a class="icon-google" target="_blank" href="' + href + '">'+CONFIG.texts.google+'</a>';
    },

    yahoo: function(event) {

      var st = formatTime(event.tzstart) || '';

      if (event.allday) {
        if (stripISOTime(formatTime(event.tzstart)) == stripISOTime(formatTime(event.tzend))) {
          // Single day
          var yahooEventDuration = '&dur=allday';
        } else {
          // Spans multiple days
          var et = '&et=' + stripISOTime(formatTime(getEndDate(event.tzend, 60 * 24))) || '';
        }
      } else {
        // Specific date and time specified
        var eventDuration = event.tzend ?
        ((event.tzend.getTime() - event.tzstart.getTime())/ MS_IN_MINUTES) :
        event.duration;

        // Yahoo dates are crazy, we need to convert the duration from minutes to hh:mm
        var yahooHourDuration = eventDuration < 600 ?
          '0' + Math.floor((eventDuration / 60)) :
          Math.floor((eventDuration / 60)) + '';
  
        var yahooMinuteDuration = eventDuration % 60 < 10 ?
          '0' + eventDuration % 60 :
          eventDuration % 60 + '';
  
        var yahooEventDuration = '&dur=' + yahooHourDuration + yahooMinuteDuration;
      }

      var href = ([
        'http://calendar.yahoo.com/?v=60&view=d&type=20',
        '&title=' + encodeURIComponent(event.title || ''),
        '&st=' + st,
        et || '',
        yahooEventDuration || '',
        '&desc=' + encodeURIComponent(event.description || ''),
        '&in_loc=' + encodeURIComponent(event.address || '')
      ].join(''));

      return '<a class="icon-yahoo" target="_blank" href="' + href + '">'+CONFIG.texts.yahoo+'</a>';
    },

    off365: function(event) {
      var startTime = formatTime(event.tzstart);
      var endTime = formatTime(event.tzend);
      if (event.outlookStart) startTime = event.outlookStart;
      if (event.outlookEnd) endTime = event.outlookEnd;

      var description = event.description || '';
      do {
        var href = ([
          'https://outlook.office365.com/owa/',
          '?path=/calendar/action/compose',
          '&rru=addevent',
          '&subject=' + encodeURIComponent(event.title || '').replaceAll('%26','and'),
          '&startdt=' + encodeURIComponent(startTime || ''),
          '&enddt=' + encodeURIComponent(endTime || ''),
          '&body=' + encodeURIComponent(description),
          '&location=' + encodeURIComponent(event.address || ''),
          '&allday=' + (event.allday?'true':'false')
      ].join(''));
        if (href.length > 2084)
          description = String(description).replace(/\s.*?$/, '');
      } while(href.length > 2084 && /\s/.test(description));

      return '<a class="icon-off365" target="_blank" href="' + href + '">'+CONFIG.texts.off365+'</a>';
    },

    outlkcom: function(event) {
      var startTime = formatTime(event.tzstart);
      var endTime = formatTime(event.tzend);

      var description = event.description || '';
      do {
        var href = ([
          'https://outlook.live.com/owa/',
          '?path=/calendar/action/compose',
          '&rru=addevent',
          '&subject=' + encodeURIComponent(event.title || ''),
          '&startdt=' + encodeURIComponent(startTime || ''),
          '&enddt=' + encodeURIComponent(endTime || ''),
          '&body=' + encodeURIComponent(description),
          '&location=' + encodeURIComponent(event.address || ''),
          '&allday=' + (event.allday?'true':'false')
        ].join(''));
        if (href.length > 2084)
          description = String(description).replace(/\s.*?$/, '');
      } while(href.length > 2084 && /\s/.test(description));

      return '<a class="icon-outlkcom" target="_blank" href="' +
        href + '">'+CONFIG.texts.outlkcom+'</a>';
    },
    
    ics: function(event, eClass, calendarName) {
      var startTime,endTime;

      if (event.allday) {
        // DTSTART and DTEND need to be equal and 0
        var startTimeVal = formatTime(event.tzstart);
        var endTimeVal = formatTime(getEndDate(event.tzend,60*24));
        startTime = 'DTSTART;VALUE=DATE:' + stripISOTime(startTimeVal);
        endTime = 'DTEND;VALUE=DATE:' + stripISOTime(endTimeVal);
      } else {
        startTime = 'DTSTART:' + formatTime(event.tzstart);
        endTime = 'DTEND:' + formatTime(event.tzend);
      }
      
      var cal = [
          'BEGIN:VCALENDAR',
          'VERSION:2.0',
          'BEGIN:VEVENT',
          'URL:' + document.URL,
          startTime,
          endTime,
          'SUMMARY:' + (event.title || ''),
          'DESCRIPTION:' + (event.description ? String(event.description).replace(/[\r\n]/g,'\\n') : ''),
          'LOCATION:' + (event.address || ''),
          'UID:' + (event.id || '') + '-' + document.URL,
          'END:VEVENT',
          'END:VCALENDAR'].join('\n');

      if (ieMustDownload) {
        return '<a class="' + eClass + '" onclick="ieDownloadCalendar(\'' +
          escapeJSValue(cal) + '\')">' + calendarName + '</a>';
      }

      var href = encodeURI('data:text/calendar;charset=utf8,' + cal);

      return '<a class="' + eClass + '" download="'+CONFIG.texts.download+'" href="' +
        href + '">' + calendarName + '</a>';
    },

    ical: function(event) {
      return this.ics(event, 'icon-ical', CONFIG.texts.ical);
    },

    outlook: function(event) {
      return this.ics(event, 'icon-outlook', CONFIG.texts.outlook);
    }
  };
  
  /* Helpers */
  
  var changeTimezone = function(date,timezone) {
    if (date) {
      try {
        if (timezone) {
          var invdate = new Date(date.toLocaleString('en-US', {
            timeZone: timezone
          }));
          var diff = date.getTime()-invdate.getTime();
          return new Date(date.getTime()+diff);
        }
      } catch(error) {
        // Catch for IE
        // console.error(error);
      }
      return date;
    }
    return;
  };
  
  var formatTime = function(date) {
    try {
      return date?date.toISOString().replace(/-|:|\.\d+/g, ''):'';
    } catch(error) {
      // Catch for IE
      // console.error(error);
    }
  };
  
  var getEndDate = function(start,duration) {
    return new Date(start.getTime() + duration * MS_IN_MINUTES);
  };

  var stripISOTime = function(isodatestr) {
    return isodatestr.substr(0,isodatestr.indexOf('T'));
  };

  var escapeJSValue = function(text) {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\"/g, '&quot;')
      .replace(/\'/g, '\\\'')
      .replace(/(\r?\n|\r)/gm, '\\n');
  };
  
  var getWindowDimensions = function() {
    var doc = document, w = window;
    var docEl = (doc.compatMode && doc.compatMode === 'CSS1Compat') ? doc.documentElement : doc.body;

    var width = docEl.clientWidth;
    var height = docEl.clientHeight;

    // mobile zoomed in
    if (w.innerWidth && width > w.innerWidth) {
      width = w.innerWidth;
      height = w.innerHeight;
    }

    return {
      width: width,
      height: height
    };
  };

  var getPosition = function(el) {
    let rect, TOP = 0, LEFT = 0, WIDTH = 0, HEIGHT = 0, BOTTOM = 0;
    if (el) {
      rect = el.getBoundingClientRect();
      TOP = Math.ceil(rect.top + window.scrollY);
      LEFT = Math.ceil(rect.left + window.scrollX);
      WIDTH = Math.ceil(rect.width);
      HEIGHT = Math.ceil(rect.height);
      BOTTOM = Math.ceil(TOP + HEIGHT);
    }
    return {top: TOP, left: LEFT, width: WIDTH, height: HEIGHT, bottom: BOTTOM}
  };

  /* Output handling */

  var generateMarkup = function(calendars, params) {

    var clazz = params.options.class;
    var calendarId = params.options.id;
    var icon = params.data.icon ? '<i class="' + params.data.icon + '"></i>' : '';

    var result = document.createElement('div');
    result.innerHTML = '<button class="button add-to-calendar-label" id="add-to-calendar-label-id-' + calendarId + '" onclick="return doAddToCalenderClick(this,' + calendarId + ');">'+icon+CONFIG.texts.label+'</button>';

    var dropdown = document.createElement('div');
    dropdown.className = 'add-to-calendar-dropdown';
    dropdown.id = 'add-to-calendar-dropdown-id-' + calendarId;

    Object.keys(calendars).forEach(function(services) {
      dropdown.innerHTML += calendars[services];
    });

    result.appendChild(dropdown);
    
    result.className = 'add-to-calendar-widget';
    if (clazz !== undefined) {
      result.className += (' ' + clazz);
    }

    result.id = calendarId;
    return result;
  };

  var generateCalendars = function(event) {
    return {
      ical: calendarGenerators.ical(event),
      google: calendarGenerators.google(event),
      off365: calendarGenerators.off365(event),
      outlook: calendarGenerators.outlook(event),
      outlkcom: calendarGenerators.outlkcom(event)
      // yahoo: calendarGenerators.yahoo(event)
    };
  };

  /* Input Handling */
  
  var sanitizeParams = function(params) {
    if (!params.options) {
      params.options = {}
    }
    if (!params.options.id) {
      params.options.id = Math.floor(Math.random() * 1000000);
    }
    if (!params.options.class) {
      params.options.class = '';
    }
    if (!params.data) {
      params.data = {};
    }
    if (!params.data.start) {
    	params.data.start=new Date();
    }
    if (params.data.end) {
      delete params.data.duration;
    } else {
      if (!params.data.duration) {
        params.data.duration = CONFIG.duration;
      }
    }
    if (params.data.duration) {
      params.data.end = getEndDate(params.data.start,params.data.duration);
    }
    
    if (params.data.timezone) {
      params.data.tzstart = changeTimezone(params.data.start,params.data.timezone);
      params.data.tzend = changeTimezone(params.data.end,params.data.timezone);
    } else {
      params.data.tzstart = params.data.start;
      params.data.tzend = params.data.end;
    }
    if (!params.data.title) {
      params.data.title = CONFIG.texts.title;
    }
  };
  
  var validParams = function(params) {
    return params.data !== undefined && params.data.start !== undefined &&
      (params.data.end !== undefined || params.data.allday !== undefined);
  };
  
  var parseCalendar = function(elm) {
    
    /*
      <div class="addtocalendar">
        <span class="start">12/18/2018 08:00 AM</span>
        <span class="end">12/18/2018 10:00 AM</span>
        <span class="duration">45</span>
        <span class="allday">true</span>
        <span class="timezone">America/Los_Angeles</span>
        <span class="title">Summary of the event</span>
        <span class="description">Description of the event</span>
        <span class="location">Location of the event</span>
        <span class="icon">far fa-calendar-plus</span>
      </div>
    */

    var data = {}, node;
    
    node = elm.querySelector('.start');
    if (node) data.start = new Date(node.textContent);
    
    node = elm.querySelector('.end');
    if (node) data.end = new Date(node.textContent);
    
    node = elm.querySelector('.duration');
    if (node) data.duration = 1*node.textContent;
    
    node = elm.querySelector('.allday');
    if (node) data.allday = true;
    
    node = elm.querySelector('.title');
    if (node) data.title = node.textContent;
    
    node = elm.querySelector('.description');
    if (node) data.description = node.textContent;
    
    node = elm.querySelector('.address');
    if (node) data.address = node.textContent;
    if (!data.address) {
      node = elm.querySelector('.location');
      if (node) data.address = node.textContent;
    }
    
    node = elm.querySelector('.timezone');
    if (node) data.timezone = node.textContent;

    node = elm.querySelector('.outlookStart');
    if (node) data.outlookStart = node.textContent;

    node = elm.querySelector('.outlookEnd');
    if (node) data.outlookEnd = node.textContent;

    node = elm.querySelector('.icon');
    if (node) data.icon = node.textContent;

    cal = createCalendar({data:data});
    if (cal) elm.appendChild(cal);
    return cal;
    
  };
  
  /* Exports */
  
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/matches
  if (!Element.prototype.matches) {
    Element.prototype.matches = Element.prototype.msMatchesSelector || Element.prototype.webkitMatchesSelector;
  }

  exports.ieDownloadCalendar = function(cal) {
    if (ieCanDownload) {
      var blob = new Blob([cal], { type: 'text/calendar' });
      window.navigator.msSaveOrOpenBlob(blob, CONFIG.texts.download);
    } else {
      alert(CONFIG.texts.ienoblob);
    }
  };

  exports.doAddToCalenderClick = function(el, calendarId) {
    var parent = el.parentElement;
    if (parent.matches('.open')){
      parent.className = parent.className.replace(/\s*\bopen\b/, "");
    } else {
      parent.className = parent.className + ' open';
      setTimeout(function(){
        var onClick = function(event) {
          var isClickInside = el.nextSibling.contains(event.target) && !event.target.matches('a');
          if (!isClickInside) {
            parent.className = parent.className.replace(/\bopen\b/, "");
            document.removeEventListener('click', onClick);
          }
        };
        document.addEventListener('click', onClick);
      }, 1);

      // adjust drop-down if not visible
      var button = document.getElementById("add-to-calendar-label-id-" + calendarId);
      var buttonPosition = getPosition(button);
      var dropdown = document.getElementById("add-to-calendar-dropdown-id-" + calendarId);
      var dropdownPosition = getPosition(dropdown);

      var win = getWindowDimensions();
      var pageScrollTop = document.documentElement.scrollTop || document.body.scrollTop;

      var buttonStyle = button.currentStyle || window.getComputedStyle(button);
      var marginTop = parseFloat(buttonStyle.marginTop || 0);
      if ((buttonPosition.bottom + dropdownPosition.height) > (win.height + pageScrollTop)) {
        var diff = (buttonPosition.bottom + dropdownPosition.height) - (win.height + pageScrollTop);
        dropdown.style.top = (buttonPosition.height - diff) + 'px';
        dropdown.style.left = '10px';
      } else {
        dropdown.style.top = marginTop + buttonPosition.height + 'px';
        dropdown.style.left = '0';
      }
    }
    return false;
  };

  exports.addToCalendarData = function(params) {
  	if (!params) params = {};
  	sanitizeParams(params);
    if (!validParams(params)) {
      console.error('Event details missing.');
      return;
    }
    return generateCalendars(params.data);
  };

  // bwc
  exports.createCalendar = function(params) {
    return addToCalendar(params);
  };
  
  exports.addToCalendar = function(params) {
    
    if (!params) params = {};

    if (params instanceof HTMLElement) {
      //console.log('HTMLElement');
      return parseCalendar(params);
    }
    
    if (params instanceof NodeList) {
      //console.log('NodeList');
      var success = (params.length>0);
      Array.prototype.forEach.call(params, function(node) { 
        success = success && addToCalendar(node);
      }); 
      return success;
    }
    
    sanitizeParams(params);
    
    if (!validParams(params)) {
      console.error('Event details missing.');
      return;
    }

    return generateMarkup(
      generateCalendars(params.data),
      params
   );
   
  };
  
  // document.ready
  document.addEventListener("DOMContentLoaded", function(event) { 
    addToCalendar(document.querySelectorAll(CONFIG.selector));
  });
  
})(this);