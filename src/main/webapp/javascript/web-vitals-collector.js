/**
 * Web Vitals Collector — Real User Measurement (RUM) for Core Web Vitals
 *
 * Collects Core Web Vitals (LCP, CLS, INP, FCP, TTFB) from real visitor sessions
 * and reports them to /rum/vitals for performance monitoring.
 *
 * Features:
 * - Consent-aware: respects analytics.consentRequired setting
 * - Lightweight: no third-party network calls after bundle
 * - Non-blocking: reports are sent asynchronously
 * - Batching: multiple metrics sent in single request
 *
 * Usage: Include this script in the page footer after <body> content
 */

(function() {
  'use strict';

  /**
   * Initialize web vitals collection
   */
  function initWebVitals() {
    // Check if collection is enabled and consent is given
    if (!shouldCollectMetrics()) {
      return;
    }

    // Collect metrics when available
    collectMetrics();
  }

  /**
   * Determine if we should collect metrics based on consent and settings
   */
  function shouldCollectMetrics() {
    // Check for opt-out flag (e.g., if user disabled analytics)
    if (typeof window.disableAnalytics !== 'undefined' && window.disableAnalytics) {
      return false;
    }

    // Check the site's real consent cookie (set by the banner in main.jsp):
    // analytics-consent=accepted|declined
    var consentCookie = getCookie('analytics-consent');
    if (consentCookie === 'declined') {
      return false;
    }

    // If no consent cookie but consent is not explicitly required, collect
    // (consent banner handles the requirement; this script is also only ever
    // loaded server-side when the same consent condition already passed)
    return true;
  }

  /**
   * Get a cookie value by name
   */
  function getCookie(name) {
    var value = '; ' + document.cookie;
    var parts = value.split('; ' + name + '=');
    if (parts.length === 2) {
      return parts.pop().split(';').shift();
    }
    return null;
  }

  // Google Core Web Vitals thresholds, mirroring WebVitalsWidget.java so
  // ingestion-time ratings agree with the admin dashboard's own classification.
  var THRESHOLDS = {
    LCP: { good: 2500, needsWork: 4000 },
    CLS: { good: 0.1, needsWork: 0.25 },
    INP: { good: 200, needsWork: 500 },
    FCP: { good: 1800, needsWork: 3000 },
    TTFB: { good: 600, needsWork: 1800 }
  };

  function rate(metricType, value) {
    var t = THRESHOLDS[metricType];
    if (value <= t.good) {
      return 'good';
    } else if (value <= t.needsWork) {
      return 'needs-improvement';
    }
    return 'poor';
  }

  /**
   * Tracks the worst (highest-duration) user interaction observed via "event" performance
   * entries, for INP (Interaction to Next Paint).
   *
   * "first-input" (First Input Delay) only ever fires once per page load and only measures the
   * very first interaction; INP replaced FID as a Core Web Vital in March 2024 specifically
   * because it instead measures the worst interaction latency across the page's entire
   * lifetime. A single logical interaction (e.g. a tap or keystroke) can emit more than one
   * "event" entry sharing the same interactionId, so entries are deduped down to each
   * interaction's own worst entry.duration before comparing across interactions. This mirrors
   * the core logic of Google's web-vitals.js library, simplified to a single worst value
   * (this codebase does not track a percentile across many interactions).
   */
  function createInpTracker() {
    var longestByInteractionId = {};

    return {
      // Processes one batch of PerformanceEventTiming entries (a PerformanceObserver callback
      // may fire many times over a page's life, once per batch of new entries) and returns the
      // worst interaction duration seen so far across all batches, or null if no qualifying
      // interaction has been observed yet.
      record: function(entries) {
        entries.forEach(function(entry) {
          // Entries without a positive interactionId are not real user interactions (some
          // "event" entries fire for non-interactive events) -- ignore them.
          if (!entry.interactionId) {
            return;
          }
          var existing = longestByInteractionId[entry.interactionId] || 0;
          if (entry.duration > existing) {
            longestByInteractionId[entry.interactionId] = entry.duration;
          }
        });

        var worst = null;
        for (var id in longestByInteractionId) {
          if (Object.prototype.hasOwnProperty.call(longestByInteractionId, id)) {
            var duration = longestByInteractionId[id];
            if (worst === null || duration > worst) {
              worst = duration;
            }
          }
        }
        return worst;
      }
    };
  }

  /**
   * Collect Core Web Vitals using the standard Navigation Timing API
   * and Intersection Observer / PerformanceObserver APIs
   */
  function collectMetrics() {
    // Only metrics that actually fired are included: the backend omits
    // a metric entirely if the page was left before it finalized.
    var metrics = {};

    function setMetric(metricType, value) {
      metrics[metricType] = { value: value, rating: rate(metricType, value) };
    }

    // Use PerformanceObserver to capture metrics as they fire
    if ('PerformanceObserver' in window) {
      // Capture LCP (Largest Contentful Paint)
      captureMetric('largest-contentful-paint', function(entries) {
        var lastEntry = entries[entries.length - 1];
        setMetric('LCP', Math.round(lastEntry.renderTime || lastEntry.loadTime));
      });

      // Capture Layout Shift (CLS)
      captureMetric('layout-shift', function(entries) {
        var cls = entries.reduce(function(sum, entry) {
          return entry.hadRecentInput ? sum : sum + entry.value;
        }, 0);
        setMetric('CLS', cls);
      });

      // Capture INP (Interaction to Next Paint): the worst interaction latency observed so
      // far. durationThreshold keeps sub-40ms entries (the vast majority, and not
      // user-perceptible) out of the observer callback entirely, matching the default the
      // web-vitals.js library itself uses.
      var inpTracker = createInpTracker();
      captureMetric('event', function(entries) {
        var worst = inpTracker.record(entries);
        if (worst !== null) {
          setMetric('INP', Math.round(worst));
        }
      }, { durationThreshold: 40 });

      // Capture FCP (First Contentful Paint)
      captureMetric('paint', function(entries) {
        entries.forEach(function(entry) {
          if (entry.name === 'first-contentful-paint') {
            setMetric('FCP', Math.round(entry.startTime));
          }
        });
      });

      // Capture TTFB (Time to First Byte)
      if (window.performance && window.performance.timing) {
        var timing = window.performance.timing;
        setMetric('TTFB', Math.round(timing.responseStart - timing.navigationStart));
      }
    }

    // Report metrics after a short delay to ensure all are captured
    // Use requestIdleCallback if available to avoid impacting page performance
    if ('requestIdleCallback' in window) {
      window.requestIdleCallback(function() {
        reportMetrics(metrics);
      });
    } else {
      setTimeout(function() {
        reportMetrics(metrics);
      }, 3000);  // 3 second delay as fallback
    }
  }

  /**
   * Capture a specific metric using PerformanceObserver.
   *
   * @param {string} entryType   the PerformanceObserver entry type to observe
   * @param {function} callback  invoked with each batch of new entries
   * @param {Object} [observeOptions]  optional extra observe() options, e.g. durationThreshold
   *   (used for "event" entries, per the Event Timing API). durationThreshold is only
   *   recognized by the single-type observe() form (`type`), not the multi-type `entryTypes`
   *   form used for every other metric here, so the init object is built accordingly.
   */
  function captureMetric(entryType, callback, observeOptions) {
    try {
      var observer = new PerformanceObserver(function(list) {
        callback(list.getEntries());
      });

      // Capture entries that have already fired and those that fire in the future
      var observeInit = (observeOptions && typeof observeOptions.durationThreshold === 'number')
        ? { type: entryType, buffered: true, durationThreshold: observeOptions.durationThreshold }
        : { entryTypes: [entryType], buffered: true };
      observer.observe(observeInit);

      // Clean up observer after some time (metrics stabilize ~5s after load)
      setTimeout(function() {
        try {
          observer.disconnect();
        } catch (e) {
          // Ignore cleanup errors
        }
      }, 5000);
    } catch (e) {
      console.warn('Failed to capture metric: ' + entryType, e);
    }
  }

  /**
   * Report collected metrics to the backend
   */
  function reportMetrics(metrics) {
    // Skip if metrics collection is disabled
    if (!shouldCollectMetrics()) {
      return;
    }

    // Only report if at least one metric actually finalized
    if (!metrics || Object.keys(metrics).length === 0) {
      return;
    }

    var payload = {
      url: window.location.pathname + window.location.search,
      metrics: metrics,
      viewportWidth: window.innerWidth || null,
      // navigator.connection is Chromium-only; absent elsewhere
      connectionType: (navigator.connection && navigator.connection.effectiveType) || null
    };

    try {
      var body = JSON.stringify(payload);
      // Use sendBeacon if available for reliable delivery, even if page unloads.
      // Note: sendBeacon cannot carry custom headers, so context (viewport/connection)
      // travels in the JSON body instead, for both delivery paths below.
      if (navigator.sendBeacon) {
        navigator.sendBeacon('/rum/vitals', body);
      } else {
        // Fallback to fetch
        fetch('/rum/vitals', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: body,
          keepalive: true  // Keep connection alive even if page unloads
        }).catch(function(error) {
          console.warn('Failed to report web vitals', error);
        });
      }
    } catch (e) {
      console.warn('Error reporting metrics', e);
    }
  }

  // Initialize when page is interactive
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWebVitals);
  } else {
    initWebVitals();
  }
})();
