-- Issue #411 PR3: focal point as a 0-100 percentage of width/height, so a fixed-aspect crop can
-- center on the subject instead of blind-center-cropping. Mirrors the images table change in
-- NEW_10010__new_cms.sql exactly (install/ and upgrade/ must stay in sync).
ALTER TABLE images ADD COLUMN IF NOT EXISTS focal_x NUMERIC(5,2) NOT NULL DEFAULT 50.00;
ALTER TABLE images ADD COLUMN IF NOT EXISTS focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00;
