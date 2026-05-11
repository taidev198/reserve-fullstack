-- release.lua
--
-- Return previously-held reservation units to the available pool.
-- Used when cancelling a PENDING reservation.
--
-- Same KEYS / ARGV shape as reserve.lua.
--
-- Return:
--    1  on success
--   -2  if any SKU would over-release (reserved < qty); a bookkeeping bug
--       — fail loudly rather than let reserved drift negative.

local n = #KEYS / 2

for i = 1, n do
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  local reserved    = tonumber(redis.call('GET', reservedKey) or '0')
  if reserved < qty then
    return -2
  end
end

for i = 1, n do
  local reservedKey = KEYS[(i - 1) * 2 + 2]
  local qty         = tonumber(ARGV[i])
  redis.call('DECRBY', reservedKey, qty)
end

return 1
