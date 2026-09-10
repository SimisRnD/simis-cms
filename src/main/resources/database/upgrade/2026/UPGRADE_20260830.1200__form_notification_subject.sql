-- Lets each form set its own admin-notification subject, so a recipient can tell a sales enquiry
-- from a password question without opening the message. Every form previously notified with the
-- same sentence ("Website contact-us form 202608300003 submitted"), which names the form and
-- says nothing about what arrived.
--
-- Blank keeps the previous subject, so existing forms are unchanged and nobody has to fill this in.
-- The value may carry {{fieldName}} placeholders; they are resolved and sanitised in
-- FormNotificationSubjectCommand, because the substituted values come from a public form.
ALTER TABLE form_definitions ADD COLUMN IF NOT EXISTS notification_subject VARCHAR(255);
