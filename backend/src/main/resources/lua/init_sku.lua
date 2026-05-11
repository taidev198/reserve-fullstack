-- init_sku.lua
--
-- Idempotent SKU initialization. Used at startup to seed Redis from
-- the durable Postgres copy of total / reserved. Safe to call
-- repeatedly — only writes when the keys are absent.
--
-- KEYS[1] = stock:{sku}
-- KEYS[2] = reserved:{sku}
-- ARGV[1] = total stock
-- ARGV[2] = reserved stock
--
-- Return:
--   1  if a fresh record was written
--   0  if the keys already existed (no-op)

if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end

redis.call('SET', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[2])

return 1
