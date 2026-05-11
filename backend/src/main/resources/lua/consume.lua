-- consume.lua
--
-- Permanently remove reserved units from the warehouse — the stock
-- has shipped. Decrements BOTH stock:{sku} and reserved:{sku}.
--
-- Same KEYS / ARGV shape as reserve.lua.
--
-- Return:
--    1  on success
--   -2  if any SKU would over-consume (reserved < qty or stock < qty)

local n = #KEYS / 2

for i = 1, n do
  local stockKey    = KEYS[(i - 1) * 2 + 1]
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  local stock       = tonumber(redis.call('GET', stockKey)    or '0')
  local reserved    = tonumber(redis.call('GET', reservedKey) or '0')
  if reserved < qty or stock < qty then
    return -2
  end
end

for i = 1, n do
  local stockKey    = KEYS[(i - 1) * 2 + 1]
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  redis.call('DECRBY', stockKey,    qty)
  redis.call('DECRBY', reservedKey, qty)
end

return 1
