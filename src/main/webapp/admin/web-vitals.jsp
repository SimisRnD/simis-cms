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

<div class="section">
  <h1><i class="fa fa-tachometer-alt"></i> Core Web Vitals Performance</h1>
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
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${sortedUrls}" var="url">
          <c:set var="summary" value="${summaryByUrl[url]}" />
          <tr>
            <td><code>${url}</code></td>
            <td>
              <span class="badge badge-${summary.lcpStatus}">
                ${summary.lcpP75 > 0 ? summary.lcpP75 : '—'}
              </span>
            </td>
            <td>
              <span class="badge badge-${summary.clsStatus}">
                ${summary.clsP75 > 0 ? summary.clsP75 / 10 : '—'}%
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
