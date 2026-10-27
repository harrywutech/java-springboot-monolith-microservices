UPDATE user SET password=MD5('test') WHERE user_id='1';
INSERT INTO user(user_id,user_name,password) VALUES('2','test2',MD5('test'));
