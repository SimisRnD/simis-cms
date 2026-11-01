/**
 * Sends the Google Analytics contact_click event when a visitor clicks a link to the contact page.
 *
 * The site sent this from a Google Tag Manager container (GTM-PR2X8TG6) loaded by the old server,
 * which went offline in late September 2026; the CMS loads gtag.js directly and no GTM container,
 * so the event stopped. This restores the same rule and the same parameters, so the event's
 * history in GA continues unbroken: on a click whose link URL contains "contact-us", send
 * contact_click with page_path set to the page the click happened on.
 *
 * One click listener on the document, registered from a nonce'd script, rather than a handler
 * on each link. The site's CSP blocks inline event handlers (onclick=...), and the old tag was a
 * GTM Custom HTML script that the CSP would block as well.
 *
 * main.jsp loads this only alongside gtag.js, so it inherits the same gating: no analytics key,
 * Do-Not-Track/GPC honored, consent not given, and admin pages all leave it out.
 */

(function () {
  'use strict';

  // Capture phase, so a link whose own handler stops the click from bubbling is still counted
  document.addEventListener('click', function (event) {
    if (typeof window.gtag !== 'function') {
      return;
    }
    var target = event.target;
    // closest() also counts a click on an icon or <span> inside the link, which GTM's element URL
    // missed
    var link = target && target.closest ? target.closest('a[href]') : null;
    if (!link || String(link.href).indexOf('contact-us') === -1) {
      return;
    }
    window.gtag('event', 'contact_click', { page_path: window.location.pathname });
  }, true);
})();
