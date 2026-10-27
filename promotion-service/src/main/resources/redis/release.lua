local state = redis.call('GET', KEYS[2])
if state == 'RELEASED' then return 0 end
if state ~= 'LOCKED' or redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
redis.call('INCR', KEYS[1])
redis.call('SET', KEYS[2], 'RELEASED')
return 1
