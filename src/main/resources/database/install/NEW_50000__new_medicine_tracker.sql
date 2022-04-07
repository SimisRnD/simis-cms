-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Medicine Tracking

CREATE TABLE medicines (
  medicine_id BIGSERIAL PRIMARY KEY,
  individual_id BIGINT REFERENCES items(item_id),
  drug_id BIGINT REFERENCES items(item_id),
  drug_name VARCHAR(255) NOT NULL,
  dosage VARCHAR(255) NOT NULL,
  form_of_medicine VARCHAR(255) NOT NULL,
  appearance VARCHAR(255),
  cost NUMERIC(15,6) DEFAULT 0,
  pills_left INTEGER,
  barcode VARCHAR(1024),
  condition VARCHAR(200),
  comments VARCHAR(200),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  assigned_to BIGINT REFERENCES users(user_id),
  suspended TIMESTAMP(3),
  suspended_by BIGINT REFERENCES users(user_id),
  archived TIMESTAMP(3),
  archived_by BIGINT REFERENCES users(user_id),
  last_taken TIMESTAMP(3),
  last_administered_by BIGINT REFERENCES users(user_id)
);
-- Pills, cc, ml, mg, Drops, Pieces, Puffs, Units, teaspoon, tablespoon,
-- patch, mcg
CREATE INDEX med_ind_id_idx ON medicines(individual_id);
CREATE INDEX med_archive_idx ON medicines(archived);

CREATE TABLE prescriptions (
  prescription_id BIGSERIAL PRIMARY KEY,
  medicine_id BIGINT REFERENCES medicines(medicine_id) NOT NULL,
  pharmacy VARCHAR(255),
  pharmacy_location VARCHAR(255),
  pharmacy_phone VARCHAR(255),
  rx_number VARCHAR(255),
  refills_left INTEGER DEFAULT 0,
  pill_total INTEGER,
  barcode VARCHAR(1024),
  comments VARCHAR(200),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX prescript_med_id_idx ON prescriptions(medicine_id);

-- "Take 600mg 3 times a day for the next 7 days, starting tonight"
-- "Then take 200mg 3 times a day for 7 days"

CREATE TABLE medicine_schedule (
  schedule_id BIGSERIAL PRIMARY KEY,
  medicine_id BIGINT REFERENCES medicines(medicine_id) NOT NULL,
  as_needed BOOLEAN DEFAULT false,
  every_day BOOLEAN DEFAULT false,
  every_x_days INTEGER,
  on_monday BOOLEAN DEFAULT false,
  on_tuesday BOOLEAN DEFAULT false,
  on_wednesday BOOLEAN DEFAULT false,
  on_thursday BOOLEAN DEFAULT false,
  on_friday BOOLEAN DEFAULT false,
  on_saturday BOOLEAN DEFAULT false,
  on_sunday BOOLEAN DEFAULT false,
  times_a_day INTEGER,
  start_date TIMESTAMP(3) NOT NULL,
  end_date TIMESTAMP(3),
  comments VARCHAR(200),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX med_sched_med_id_idx ON medicine_schedule(medicine_id);
CREATE INDEX med_sched_ev_day_idx ON medicine_schedule(every_day);
CREATE INDEX med_sched_ev_xday_idx ON medicine_schedule(every_x_days);
CREATE INDEX med_sched_on_mon_idx ON medicine_schedule(on_monday);
CREATE INDEX med_sched_on_tue_idx ON medicine_schedule(on_tuesday);
CREATE INDEX med_sched_on_wed_idx ON medicine_schedule(on_wednesday);
CREATE INDEX med_sched_on_thu_idx ON medicine_schedule(on_thursday);
CREATE INDEX med_sched_on_fri_idx ON medicine_schedule(on_friday);
CREATE INDEX med_sched_on_sat_idx ON medicine_schedule(on_saturday);
CREATE INDEX med_sched_on_sun_idx ON medicine_schedule(on_sunday);
CREATE INDEX med_sched_start_idx ON medicine_schedule(start_date);
CREATE INDEX med_sched_end_idx ON medicine_schedule(end_date);

-- what times? and how much? (8am; take Quantity: #)
CREATE TABLE medicine_times (
  time_id BIGSERIAL PRIMARY KEY,
  schedule_id BIGINT REFERENCES medicine_schedule(schedule_id),
  medicine_id BIGINT REFERENCES medicines(medicine_id) NOT NULL,
  hour INTEGER NOT NULL,
  minute INTEGER NOT NULL,
  quantity INTEGER NOT NULL
);
CREATE INDEX med_times_sched_id_idx ON medicine_times(schedule_id);
CREATE INDEX med_times_med_id_idx ON medicine_times(medicine_id);

-- In code, for any given calendar day, be able to show the schedule
-- as a formula, not as a database entry.


-- Determine the reminders (just the next reminder)
-- For the schedule days + hours
CREATE TABLE medicine_reminders (
  reminder_id BIGSERIAL PRIMARY KEY,
  individual_id BIGINT REFERENCES items(item_id),
  medicine_id BIGINT REFERENCES medicines(medicine_id) NOT NULL,
  schedule_id BIGINT REFERENCES medicine_schedule(schedule_id) NOT NULL,
  time_id BIGINT REFERENCES medicine_times(time_id) NOT NULL,
  reminder_date TIMESTAMP(3) NOT NULL,
  processed TIMESTAMP(3),
  logged TIMESTAMP(3),
  was_taken BOOLEAN DEFAULT false,
  was_skipped BOOLEAN DEFAULT false
);
CREATE INDEX med_remind_med_id_idx ON medicine_reminders(medicine_id);
CREATE INDEX med_remind_sched_id_idx ON medicine_reminders(schedule_id);
CREATE INDEX med_remind_time_id_idx ON medicine_reminders(time_id);

CREATE TABLE medicine_log (
  log_id BIGSERIAL PRIMARY KEY,
  medicine_id BIGINT REFERENCES medicines(medicine_id) NOT NULL,
  individual_id BIGINT REFERENCES items(item_id),
  reminder_id BIGINT REFERENCES medicine_reminders(reminder_id),
  reminder_date TIMESTAMP(3),
  drug_id BIGINT REFERENCES items(item_id),
  drug_name VARCHAR(255) NOT NULL,
  dosage VARCHAR(255),
  form_of_medicine VARCHAR(255),
  quantity INTEGER DEFAULT 0,
  comments VARCHAR(255),
  pills_left INTEGER,
  administered_by BIGINT REFERENCES users(user_id) NOT NULL,
  administered TIMESTAMP(3),
  was_taken BOOLEAN DEFAULT false,
  was_skipped BOOLEAN DEFAULT false,
  taken_on_time BOOLEAN DEFAULT false,
  reason_refused BOOLEAN DEFAULT false,
  reason_individual BOOLEAN DEFAULT false,
  reason_caregiver BOOLEAN DEFAULT false,
  reason_medicine BOOLEAN DEFAULT false,
  reason_med_ran_out BOOLEAN DEFAULT false,
  reason_dose_not_needed BOOLEAN DEFAULT false,
  reason_health_concerns BOOLEAN DEFAULT false,
  reason_other_concern BOOLEAN DEFAULT false,
  reason_comments VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX med_log_ind_id_idx ON medicine_log(individual_id);

-- Refused to take the medicine (reason_refused)
-- Medicine isn't near me (reason_unavailable)
-- Forgot / busy / asleep (reason_was_busy)
-- Ran out of the medicine (reason_med_ran_out)
-- Don't need to take this dose (reason_dose_not_needed)
-- Side effects / other health concerns (reason_had_side_effects)
