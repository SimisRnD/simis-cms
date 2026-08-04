-- Issue #914: removes the dead USPS address-validation/tracking integration. AddressCommand and
-- TrackingCommand called USPS's legacy XML Web Tools API (secure.shippingapis.com/ShippingAPI.dll),
-- which USPS fully shut down in January 2026. TrackingCommand had zero callers anywhere in the
-- app. AddressCommand.verifyAddress() had exactly one caller (ShippingAddressFormWidget), but
-- ecommerce.addressValidation was seeded as a hardcoded, non-editable 'None' value (property_type
-- 'disabled'), so there was no admin UI path to ever set it to 'USPS' -- the feature was never
-- reachable in any real deployment, independent of the API shutdown.
DELETE FROM site_properties WHERE property_name IN (
  'ecommerce.addressValidation',
  'ecommerce.usps.webtools.userid'
);
