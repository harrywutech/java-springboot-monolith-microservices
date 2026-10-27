-- Stock, permanent per-order state, pending event hash; atomic admission + durable intent.
local state = redis.call('GET', KEYS[2])
if state == 'LOCKED' then return 0 end
if state then return -3 end -- a released order must never reserve again
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock then return -2 end -- fail closed: no blind hydration while MQ is behind
if stock <= 0 then return -1 end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], 'LOCKED')
redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
return 1
