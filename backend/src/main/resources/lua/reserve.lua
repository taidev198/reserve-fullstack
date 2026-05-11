-- reserve.lua
--
-- Atomically reserve stock for N SKUs.
--
-- KEYS layout (length = 2 * N):
--   KEYS[1], KEYS[2]  =  stock:{sku_1}, reserved:{sku_1}
--   KEYS[3], KEYS[4]  =  stock:{sku_2}, reserved:{sku_2}
--   ...
--
-- ARGV[i] = quantity to reserve for SKU i
--
-- Return:
--    1  on success
--   -1  if any SKU has insufficient available stock (no writes happen)
--
-- Atomicity comes from Redis running scripts single-threaded — between
-- the check loop and the write loop, no other client can squeeze in
-- a read or a write on any of the keys we touch.

local n = #KEYS / 2

for i = 1, n do
  local stockKey    = KEYS[(i - 1) * 2 + 1]
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  local stock       = tonumber(redis.call('GET', stockKey)    or '0')
  local reserved    = tonumber(redis.call('GET', reservedKey) or '0')
  if (stock - reserved) < qty then
    return -1
  end
end

for i = 1, n do
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  redis.call('INCRBY', reservedKey, qty)
end

return 1
