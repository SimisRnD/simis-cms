-- Make theme.logo.color (header) and theme.footer.logo.color (footer) actually take effect.
--
-- Both properties have always been visible and editable in Admin > Theme Settings, but were
-- unreachable dead weight: the header.default and footer.default/footer.4column layouts each
-- hardcoded a <view> preference on their logo widget, and cms/logo.jsp only consults the
-- theme property when a widget's view is completely blank. That hardcoding is removed as part
-- of this change, so these two properties now genuinely control what renders.
--
-- Both properties seed at 'text-only' (NEW_10000__new_database.sql originally, now changed to
-- 'color-and-white'/'all-white' for fresh installs), which -- now that the properties are
-- reachable -- would render no logo at all in the header and footer. Bump only installations
-- still on that untouched default to the values that reproduce today's actual rendering
-- (header: <view>color</view> mapped to the mixed-color logo; footer: <view>white</view> mapped
-- to the all-white logo), so no existing site's header/footer visibly changes on upgrade. An
-- install where an admin already changed one of these away from 'text-only' -- possible, since
-- the dropdown has always been clickable even though it silently did nothing -- is left alone;
-- their choice simply starts taking effect for the first time, which is correct, not an
-- accidental override.
UPDATE site_properties
SET property_value = 'color-and-white'
WHERE property_name = 'theme.logo.color'
  AND property_value = 'text-only';

UPDATE site_properties
SET property_value = 'all-white'
WHERE property_name = 'theme.footer.logo.color'
  AND property_value = 'text-only';

-- The property changes above are necessary but not sufficient. header.default's and
-- footer.default's/footer.4column's XML is not read from header-layout.xml/footer-layout.xml on
-- an already-installed site -- NEW_20000__insert_containers.sql copied a snapshot of that XML
-- into the web_containers table at install time, and XMLHeaderLoader/XMLFooterLoader always
-- prefer the database row over the file. Without this, the hardcoded <view> tags this change
-- removes from the XML files would keep rendering from the stale database copy indefinitely,
-- silently defeating the whole fix on every existing install -- caught by a Docker rehearsal
-- against a fresh install, which the same install-seed staleness affects for the same reason.
--
-- Scoped to an exact substring match of the original stock markup (same before/after text as
-- the NEW_20000 seed edit), so this is a no-op -- not a corruption risk -- on any install where
-- an admin has already customized header.default/footer.default via the Page Layout designer
-- and the stock <view> tag is no longer present verbatim.
UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="logo" class="float-left margin-right-25">
        <view>color</view>
        <maxHeight>50px</maxHeight>
      </widget>',
  '<widget name="logo" class="float-left margin-right-25">
        <maxHeight>50px</maxHeight>
      </widget>'
)
WHERE container_name = 'header.default'
  AND container_xml LIKE '%<widget name="logo" class="float-left margin-right-25">
        <view>color</view>
        <maxHeight>50px</maxHeight>
      </widget>%';

UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="logo">
        <view>white</view>
        <maxHeight>50px</maxHeight>
      </widget>',
  '<widget name="logo">
        <colorProperty>theme.footer.logo.color</colorProperty>
        <maxHeight>50px</maxHeight>
      </widget>'
)
WHERE container_name = 'footer.default'
  AND container_xml LIKE '%<widget name="logo">
        <view>white</view>
        <maxHeight>50px</maxHeight>
      </widget>%';

UPDATE web_containers
SET container_xml = REPLACE(
  container_xml,
  '<widget name="logo">
          <view>white</view>
          <maxHeight>50px</maxHeight>
        </widget>',
  '<widget name="logo">
          <colorProperty>theme.footer.logo.color</colorProperty>
          <maxHeight>50px</maxHeight>
        </widget>'
)
WHERE container_name = 'footer.4column'
  AND container_xml LIKE '%<widget name="logo">
          <view>white</view>
          <maxHeight>50px</maxHeight>
        </widget>%';
