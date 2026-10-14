local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local count = #KEYS
local buckets = {}
local retry_ms = 0

for i = 1, count do
    local offset = (i - 1) * 3
    local capacity = tonumber(ARGV[offset + 1])
    local refill_tokens = tonumber(ARGV[offset + 2])
    local refill_period_ms = tonumber(ARGV[offset + 3])
    local stored = redis.call('HMGET', KEYS[i], 'tokens', 'last_refill_ms')
    local tokens = capacity
    local last = now
    if stored[1] then
        tokens = tonumber(stored[1])
        last = tonumber(stored[2])
        local elapsed = math.max(0, now - last)
        tokens = math.min(capacity, tokens + elapsed * refill_tokens / refill_period_ms)
    end
    buckets[i] = {capacity, refill_tokens, refill_period_ms, tokens}
    if tokens < 1 then
        local wait = math.ceil((1 - tokens) * refill_period_ms / refill_tokens)
        retry_ms = math.max(retry_ms, wait, 1)
    end
end

if retry_ms > 0 then
    return {0, math.max(1, math.ceil(retry_ms / 1000))}
end

for i = 1, count do
    local bucket = buckets[i]
    local remaining = bucket[4] - 1
    redis.call('HSET', KEYS[i], 'tokens', string.format('%.12f', remaining), 'last_refill_ms', now)
    local ttl = math.ceil(bucket[1] * bucket[3] / bucket[2]) + 1000
    redis.call('PEXPIRE', KEYS[i], math.max(1, ttl))
end
return {1, 0}
