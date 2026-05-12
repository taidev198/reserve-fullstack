-- POST /reservations with a unique orderId each request (same intent as loadtest/k6/reserve.js).
-- Run via: wrk -t4 -c100 -d30s --latency -s reserve.lua http://localhost:8080
-- Env (inherited from shell): SKUS=comma-separated list, ITEM_QTY=integer (default 1).

local function getenv(name, default)
  if type(os) ~= "table" or type(os.getenv) ~= "function" then
    return default
  end
  local v = os.getenv(name)
  if v == nil or v == "" then
    return default
  end
  return v
end

local item_qty = 1
local skus = nil
local seq = 0

function init(args)
  local skus_env = getenv("SKUS", "")
  if skus_env ~= "" then
    skus = {}
    for part in string.gmatch(skus_env, "[^,]+") do
      local s = string.gsub(part, "^%s+", "")
      s = string.gsub(s, "%s+$", "")
      skus[#skus + 1] = s
    end
  else
    skus = { "A100", "B200", "C300", "D400", "E500" }
  end
  item_qty = tonumber(getenv("ITEM_QTY", "1")) or 1
  local t = os.time()
  local c = os.clock()
  math.randomseed((t * 1000 + math.floor(c * 1e6)) % 2147483647)
  seq = math.random(1, 1e9)
  wrk.headers["Content-Type"] = "application/json"
end

function request()
  seq = seq + 1
  local sku = skus[((seq - 1) % #skus) + 1]
  local order_id = string.format(
    "wrk-%d-%d-%d",
    os.time(),
    seq,
    math.random(1, 99999999)
  )
  local body = string.format(
    '{"orderId":"%s","items":[{"sku":"%s","quantity":%d}]}',
    order_id,
    sku,
    item_qty
  )
  return wrk.format("POST", "/reservations", nil, body)
end
