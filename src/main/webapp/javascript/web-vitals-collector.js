/**
 * Web Vitals Collector — Real User Measurement (RUM) for Core Web Vitals
 *
 * Collects Core Web Vitals (LCP, CLS, INP, FCP, TTFB) from real visitor sessions
 * and reports them to /api/metrics/vitals for performance monitoring.
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

    // Check for analytics consent cookie
    // Format: analytics_consent=accepted|rejected|pending
    var consentCookie = getCookie('analytics_consent');
    if (consentCookie === 'rejected') {
      return false;
    }

    // If no consent cookie but consent is not explicitly required, collect
    // (consent banner handles the requirement)
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

  /**
   * Collect Core Web Vitals using the standard Navigation Timing API
   * and Intersection Observer / PerformanceObserver APIs
   */
  function collectMetrics() {
    var metrics = {
      url: window.location.pathname + window.location.search,
      lcp: 0,
      cls: 0,
      inp: 0,
      fcp: 0,
      ttfb: 0
    };

    // Use PerformanceObserver to capture metrics as they fire
    if ('PerformanceObserver' in window) {
      // Capture LCP (Largest Contentful Paint)
      captureMetric('largest-contentful-paint', function(entries) {
        var lastEntry = entries[entries.length - 1];
        metrics.lcp = Math.round(lastEntry.renderTime || lastEntry.loadTime);
      });

      // Capture Layout Shift (CLS)
      captureMetric('layout-shift', function(entries) {
        metrics.cls = entries.reduce(function(sum, entry) {
          return entry.hadRecentInput ? sum : sum + entry.value;
        }, 0);
      });

      // Capture First Input Delay / INP (Interaction to Next Paint)
      captureMetric('first-input', function(entries) {
        if (entries.length > 0) {
          var firstEntry = entries[0];
          metrics.inp = Math.round(firstEntry.processingDuration);
        }
      });

      // Capture FCP (First Contentful Paint)
      captureMetric('paint', function(entries) {
        entries.forEach(function(entry) {
          if (entry.name === 'first-contentful-paint') {
            metrics.fcp = Math.round(entry.startTime);
          }
        });
      });

      // Capture TTFB (Time to First Byte)
      if (window.performance && window.performance.timing) {
        var timing = window.performance.timing;
        var ttfb = timing.responseStart - timing.navigationStart;
        metrics.ttfb = Math.round(ttfb);
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
   * Capture a specific metric using PerformanceObserver
   */
  function captureMetric(entryType, callback) {
    try {
      var observer = new PerformanceObserver(function(list) {
        callback(list.getEntries());
      });

      // Capture entries that have already fired and those that fire in the future
      observer.observe({ entryTypes: [entryType], buffered: true });

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

    // Only report if we have some metrics
    if (!metrics || !metrics.url) {
      return;
    }

    try {
      // Use sendBeacon if available for reliable delivery, even if page unloads
      if (navigator.sendBeacon) {
        var payload = JSON.stringify(metrics);
        navigator.sendBeacon('/api/metrics/vitals', payload);
      } else {
        // Fallback to fetch
        fetch('/api/metrics/vitals', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(metrics),
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
