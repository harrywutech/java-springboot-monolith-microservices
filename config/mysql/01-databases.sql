-- Each application has a distinct database account and no cross-service grants.
CREATE DATABASE IF NOT EXISTS step3_user CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_user'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_user.* TO 'step3_user'@'%';
CREATE DATABASE IF NOT EXISTS step3_commodity CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_commodity'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_commodity.* TO 'step3_commodity'@'%';
CREATE DATABASE IF NOT EXISTS step3_promotion CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_promotion'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_promotion.* TO 'step3_promotion'@'%';
CREATE DATABASE IF NOT EXISTS step3_order1 CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_order1'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_order1.* TO 'step3_order1'@'%';
CREATE DATABASE IF NOT EXISTS step3_order2 CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'step3_order2'@'%' IDENTIFIED BY 'step3_local';
GRANT ALL ON step3_order2.* TO 'step3_order2'@'%';
