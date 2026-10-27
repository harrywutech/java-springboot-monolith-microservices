-- Local course fixtures only; preserve the historical migration checksums.
UPDATE promotion SET start_time = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY),
 end_time = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 YEAR)
 WHERE commodity_id IN ('b859ee94-20f1-11ed-861d-0242ac120002', 'b859ee94-20f1-11ed-861d-0242ac120003')
 AND end_time < start_time;
-- The supplied UI sends MD5; match its demo login test/test.
UPDATE user SET password = MD5('test') WHERE user_id = '1' AND password = 'test';
