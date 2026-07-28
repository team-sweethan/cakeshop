ALTER TABLE `members`
    ADD COLUMN `name` VARCHAR(50) NULL AFTER `password`;

UPDATE `members`
SET `name` = `nickname`
WHERE `name` IS NULL
   OR TRIM(`name`) = '';

ALTER TABLE `members`
    MODIFY COLUMN `name` VARCHAR(50) NOT NULL;
