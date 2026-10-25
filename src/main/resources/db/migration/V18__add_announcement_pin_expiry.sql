ALTER TABLE post
    ADD COLUMN pinned_until DATETIME NULL AFTER is_pinned;

UPDATE post
SET is_pinned = 1,
    pinned_until = NULL
WHERE post_type = 'ANNOUNCEMENT'
  AND status = 1;
