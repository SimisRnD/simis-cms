-- Adds security.csp.reportOnly, the candidate Content-Security-Policy that PageServlet emits as a
-- Content-Security-Policy-Report-Only header. The browser evaluates it, enforces nothing, and posts
-- a report for whatever would have been refused.
--
-- This exists because the directives still missing from #1430 cannot be written by reading the
-- source. Every third-party integration enters as a <script src> and then calls endpoints of its
-- own -- Stripe's script reaches api.stripe.com, gtag builds google-analytics.com at runtime,
-- Square uses pci-connect.squareup.com -- and none of those hosts appear anywhere in this
-- repository. They also change when a vendor updates their SDK, with no commit here. A guessed
-- connect-src fails by silently breaking checkout, so the list has to come from real traffic.
--
-- Seeds blank, which disables the header and the /csp-report endpoint together, so this changes
-- nothing for any existing site until an administrator sets a policy to test. It is stored as a
-- property rather than compiled in on purpose: unlike the enforced policy, a report-only policy
-- cannot break a page, so there is no risk in letting it be edited -- and adjusting the candidate
-- without a release is the whole point of running an inventory.
--
-- property_order 40 places it after the password properties on /admin/security-properties.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (40, 'CSP report-only policy', 'security.csp.reportOnly', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
