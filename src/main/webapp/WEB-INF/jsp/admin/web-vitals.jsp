<%--
  ~ Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="trendUrls" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="trendDataList" class="java.util.ArrayList" scope="request"/>

<div class="section">
  <h2 class="h1"><i class="fa fa-tachometer-alt"></i> Core Web Vitals Performance</h2>
  <p>Real User Measurement (RUM) data from the last 7 days. P75 percentile shown (75% of visitors experience this performance or better).</p>

  <c:if test="${not empty errorMessage}">
    <div class="alert alert-warning">${errorMessage}</div>
  </c:if>

  <c:if test="${empty summaryByUrl}">
    <div class="alert alert-info">No performance data yet. Metrics will appear as visitors browse the site.</div>
  </c:if>

  <c:if test="${not empty summaryByUrl}">
    <table class="table table-striped">
      <thead>
        <tr>
          <th>URL</th>
          <th title="Largest Contentful Paint">LCP (ms) <br/><small>&lt;2500 good</small></th>
          <th title="Cumulative Layout Shift">CLS (%) <br/><small>&lt;10% good</small></th>
          <th title="Interaction to Next Paint">INP (ms) <br/><small>&lt;200 good</small></th>
          <th title="First Contentful Paint">FCP (ms) <br/><small>&lt;1800 good</small></th>
          <th title="Time to First Byte">TTFB (ms) <br/><small>&lt;600 good</small></th>
          <th>Score</th>
          <th title="Real-user samples the newest day's aggregate is based on">Samples</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${sortedUrls}" var="url">
          <c:set var="summary" value="${summaryByUrl[url]}" />
          <tr>
            <td><code><c:out value="${url}" /></code></td>
            <td>
              <span class="badge badge-${summary.lcpStatus}">
                ${summary.lcpP75 > 0 ? summary.lcpP75 : '—'}
              </span>
            </td>
            <td>
              <span class="badge badge-${summary.clsStatus}">
                <c:choose>
                  <c:when test="${summary.clsP75 > 0}"><fmt:formatNumber value="${summary.clsP75 * 100}" maxFractionDigits="1" />%</c:when>
                  <c:otherwise>&mdash;</c:otherwise>
                </c:choose>
              </span>
            </td>
            <td>
              <span class="badge badge-${summary.inpStatus}">
                ${summary.inpP75 > 0 ? summary.inpP75 : '—'}
              </span>
            </td>
            <td>
              <span class="badge badge-${summary.fcpStatus}">
                ${summary.fcpP75 > 0 ? summary.fcpP75 : '—'}
              </span>
            </td>
            <td>
              <span class="badge badge-${summary.ttfbStatus}">
                ${summary.ttfbP75 > 0 ? summary.ttfbP75 : '—'}
              </span>
            </td>
            <td>
              <c:if test="${summary.overallScore > 75}">
                <span class="badge badge-success">${summary.overallScore}</span>
              </c:if>
              <c:if test="${summary.overallScore > 50 && summary.overallScore <= 75}">
                <span class="badge badge-warning">${summary.overallScore}</span>
              </c:if>
              <c:if test="${summary.overallScore <= 50}">
                <span class="badge badge-danger">${summary.overallScore}</span>
              </c:if>
            </td>
            <td><fmt:formatNumber value="${summary.sampleCount}" /></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <div style="margin-top: 20px;">
      <h3>Thresholds (Google Core Web Vitals)</h3>
      <table class="table table-sm">
        <tr>
          <th style="width: 25%;">Metric</th>
          <th style="width: 25%;"><span class="badge badge-good">Good</span></th>
          <th style="width: 25%;"><span class="badge badge-needsWork">Needs Work</span></th>
          <th style="width: 25%;"><span class="badge badge-poor">Poor</span></th>
        </tr>
        <tr>
          <td><strong>LCP</strong></td>
          <td>&lt; 2.5s</td>
          <td>2.5 – 4.0s</td>
          <td>&gt; 4.0s</td>
        </tr>
        <tr>
          <td><strong>CLS</strong></td>
          <td>&lt; 0.1</td>
          <td>0.1 – 0.25</td>
          <td>&gt; 0.25</td>
        </tr>
        <tr>
          <td><strong>INP</strong></td>
          <td>&lt; 200ms</td>
          <td>200 – 500ms</td>
          <td>&gt; 500ms</td>
        </tr>
        <tr>
          <td><strong>FCP</strong></td>
          <td>&lt; 1.8s</td>
          <td>1.8 – 3.0s</td>
          <td>&gt; 3.0s</td>
        </tr>
        <tr>
          <td><strong>TTFB</strong></td>
          <td>&lt; 600ms</td>
          <td>600 – 1800ms</td>
          <td>&gt; 1800ms</td>
        </tr>
      </table>
    </div>

    <style>
      .badge-good {
        background-color: #28a745;
        color: white;
      }
      .badge-needsWork {
        background-color: #ffc107;
        color: black;
      }
      .badge-poor {
        background-color: #dc3545;
        color: white;
      }
      .badge-unknown {
        background-color: #6c757d;
        color: white;
      }
      table code {
        background-color: #f5f5f5;
        padding: 2px 4px;
        border-radius: 3px;
      }
    </style>
  </c:if>
</div>

<div class="section">
  <h3><i class="fa fa-chart-line"></i> Trend</h3>

  <c:if test="${empty trendUrls}">
    <div class="alert alert-info">No trend data yet. The nightly aggregation job populates this once a URL has more than one day of history.</div>
  </c:if>

  <c:if test="${not empty trendUrls}">
    <div class="trend-controls" style="display:flex; flex-wrap:wrap; gap:1rem; align-items:flex-end; margin-bottom:1rem;">
      <label>URL
        <select id="trendUrlSelect${widgetContext.uniqueId}">
          <c:forEach items="${trendUrls}" var="urlOption">
            <option value="<c:out value="${urlOption}"/>"<c:if test="${urlOption == trendUrl}"> selected="selected"</c:if>><c:out value="${urlOption}"/></option>
          </c:forEach>
        </select>
      </label>
      <label>Metric
        <select id="trendMetricSelect${widgetContext.uniqueId}">
          <option value="LCP"<c:if test="${trendMetric == 'LCP'}"> selected="selected"</c:if>>LCP</option>
          <option value="CLS"<c:if test="${trendMetric == 'CLS'}"> selected="selected"</c:if>>CLS</option>
          <option value="INP"<c:if test="${trendMetric == 'INP'}"> selected="selected"</c:if>>INP</option>
          <option value="FCP"<c:if test="${trendMetric == 'FCP'}"> selected="selected"</c:if>>FCP</option>
          <option value="TTFB"<c:if test="${trendMetric == 'TTFB'}"> selected="selected"</c:if>>TTFB</option>
        </select>
      </label>
      <div id="trendRangeTabs${widgetContext.uniqueId}" role="group" aria-label="Date range">
        <button type="button" class="button<c:if test="${trendDays == 7}"> is-active</c:if>" data-days="7">7 days</button>
        <button type="button" class="button<c:if test="${trendDays == 30}"> is-active</c:if>" data-days="30">30 days</button>
        <button type="button" class="button<c:if test="${trendDays == 90}"> is-active</c:if>" data-days="90">90 days</button>
      </div>
    </div>

    <%-- The canvas chart is not readable by assistive technology, so it is labeled and paired with an
         equivalent screen-reader-only data table (WCAG 2.1 SC 1.1.1 / 1.3.1; Section 508), matching the
         convention in site-stats-line-chart.jsp. --%>
    <canvas id="trendChart${widgetContext.uniqueId}" width="600" height="250" role="img"
            aria-label="Core Web Vitals trend chart. The data follows in a table."></canvas>
    <table class="show-for-sr" id="trendTable${widgetContext.uniqueId}">
      <caption>Core Web Vitals trend &ndash; data table</caption>
      <thead>
        <tr>
          <th scope="col">Date</th>
          <th scope="col">p50</th>
          <th scope="col">p75</th>
          <th scope="col">p95</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${trendDataList}" var="point">
          <tr>
            <th scope="row"><c:out value="${point.dateLabel}"/></th>
            <td><c:out value="${point.p50Value}"/></td>
            <td><c:out value="${point.p75Value}"/></td>
            <td><c:out value="${point.p95Value}"/></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <script src="${ctx}/javascript/chartjs-4.4.1/chart.umd.min.js"></script>
    <script nonce="${cspNonce}">
      (function() {
        // Good/needs-improvement thresholds per metric (p75 targets) -- must stay in sync with
        // WebVitalsWidget.java's LCP_GOOD/LCP_NEEDS_WORK etc. constants and the thresholds table
        // above; both are small, rarely-changed literal tables, so this mirrors rather than shares
        // them across the Java/JS boundary, same as the thresholds table's own hardcoded values.
        var METRIC_THRESHOLDS = {
          LCP: { good: 2500, poor: 4000 },
          CLS: { good: 0.1, poor: 0.25 },
          INP: { good: 200, poor: 500 },
          FCP: { good: 1800, poor: 3000 },
          TTFB: { good: 600, poor: 1800 }
        };

        // Shades the chart's plot area green/yellow/red at the current metric's Good/Needs
        // Improvement/Poor boundaries -- the same badge colors used by the summary table above.
        var thresholdBandsPlugin = {
          id: 'thresholdBands',
          beforeDraw: function(chart) {
            var bands = chart.config._thresholdBands;
            var yScale = chart.scales.y;
            if (!bands || !yScale) {
              return;
            }
            var area = chart.chartArea;
            var ctx = chart.ctx;
            var goodY = Math.max(area.top, Math.min(yScale.getPixelForValue(bands.good), area.bottom));
            var poorY = Math.max(area.top, Math.min(yScale.getPixelForValue(bands.poor), area.bottom));
            ctx.save();
            ctx.fillStyle = 'rgba(40, 167, 69, 0.12)';
            ctx.fillRect(area.left, goodY, area.width, area.bottom - goodY);
            ctx.fillStyle = 'rgba(255, 193, 7, 0.12)';
            ctx.fillRect(area.left, poorY, area.width, goodY - poorY);
            ctx.fillStyle = 'rgba(220, 53, 69, 0.12)';
            ctx.fillRect(area.left, area.top, area.width, poorY - area.top);
            ctx.restore();
          }
        };

        var trendCtx = document.getElementById("trendChart${widgetContext.uniqueId}").getContext('2d');
        var trendChart = new Chart(trendCtx, {
          type: 'line',
          data: {
            labels: [
              <c:forEach items="${trendDataList}" var="point" varStatus="status">
              "${js:escape(point.dateLabel)}"<c:if test="${!status.last}">, </c:if>
              </c:forEach>
            ],
            datasets: [
              {
                label: 'p75',
                data: [
                  <c:forEach items="${trendDataList}" var="point" varStatus="status">
                  ${point.p75Value}<c:if test="${!status.last}">, </c:if>
                  </c:forEach>
                ],
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.15)',
                tension: 0.1,
                fill: false
              },
              {
                label: 'p50',
                data: [
                  <c:forEach items="${trendDataList}" var="point" varStatus="status">
                  ${point.p50Value}<c:if test="${!status.last}">, </c:if>
                  </c:forEach>
                ],
                borderColor: 'rgb(160, 160, 160)',
                borderDash: [4, 4],
                pointRadius: 2,
                tension: 0.1,
                fill: false
              },
              {
                label: 'p95',
                data: [
                  <c:forEach items="${trendDataList}" var="point" varStatus="status">
                  ${point.p95Value}<c:if test="${!status.last}">, </c:if>
                  </c:forEach>
                ],
                borderColor: 'rgb(220, 53, 69)',
                borderDash: [2, 2],
                pointRadius: 2,
                tension: 0.1,
                fill: false
              }
            ]
          },
          options: {
            responsive: true,
            plugins: {
              legend: {
                display: true
              }
            },
            scales: {
              y: {
                beginAtZero: true
              },
              x: {}
            }
          },
          plugins: [thresholdBandsPlugin]
        });

        var urlSelect = document.getElementById("trendUrlSelect${widgetContext.uniqueId}");
        var metricSelect = document.getElementById("trendMetricSelect${widgetContext.uniqueId}");
        var rangeTabs = document.getElementById("trendRangeTabs${widgetContext.uniqueId}");
        var activeRangeButton = rangeTabs.querySelector('.is-active');
        var currentDays = activeRangeButton ? activeRangeButton.getAttribute('data-days') : '30';

        // Escape a label for safe insertion into the screen-reader table (dates are server-formatted,
        // but this mirrors site-stats-table.jsp's defensive escaping for AJAX-refreshed text nodes).
        function escapeHtml${widgetContext.uniqueId}(text) {
          var div = document.createElement('div');
          div.textContent = text == null ? '' : text;
          return div.innerHTML;
        }

        function applyThresholdBands() {
          trendChart.config._thresholdBands = METRIC_THRESHOLDS[metricSelect.value] || null;
        }

        function rebuildTrendTable(points) {
          var rows = [];
          $.each(points, function(i, point) {
            rows.push("<tr><th scope=\"row\">" + escapeHtml${widgetContext.uniqueId}(point.date) + "</th><td>" +
                parseFloat(point.p50).toLocaleString() + "</td><td>" +
                parseFloat(point.p75).toLocaleString() + "</td><td>" +
                parseFloat(point.p95).toLocaleString() + "</td></tr>");
          });
          $("#trendTable${widgetContext.uniqueId} tbody").remove();
          $('<tbody/>', { html: rows.join('') }).appendTo("#trendTable${widgetContext.uniqueId}");
        }

        function refreshTrendChart() {
          $.ajax({
            url: '${widgetContext.uri}?widget=${widgetContext.uniqueId}&action=get' +
                '&trendUrl=' + encodeURIComponent(urlSelect.value) +
                '&trendMetric=' + encodeURIComponent(metricSelect.value) +
                '&trendDays=' + encodeURIComponent(currentDays) +
                '&token=${userSession.formToken}',
            type: 'GET',
            dataType: 'json',
            cache: false,
            timeout: 5000
          }).done(function(points) {
            var labels = [];
            var p50 = [];
            var p75 = [];
            var p95 = [];
            $.each(points, function(i, point) {
              labels.push(point.date);
              p50.push(point.p50);
              p75.push(point.p75);
              p95.push(point.p95);
            });
            trendChart.data.labels = labels;
            trendChart.data.datasets[0].data = p75;
            trendChart.data.datasets[1].data = p50;
            trendChart.data.datasets[2].data = p95;
            applyThresholdBands();
            trendChart.update();
            rebuildTrendTable(points);
          });
        }

        urlSelect.addEventListener('change', refreshTrendChart);
        metricSelect.addEventListener('change', refreshTrendChart);
        Array.prototype.forEach.call(rangeTabs.querySelectorAll('button'), function(btn) {
          btn.addEventListener('click', function() {
            currentDays = btn.getAttribute('data-days');
            Array.prototype.forEach.call(rangeTabs.querySelectorAll('button'), function(other) {
              other.classList.remove('is-active');
            });
            btn.classList.add('is-active');
            refreshTrendChart();
          });
        });

        // Paint the threshold bands for the initial (server-rendered) metric selection.
        applyThresholdBands();
        trendChart.update();
      })();
    </script>
  </c:if>
</div>
