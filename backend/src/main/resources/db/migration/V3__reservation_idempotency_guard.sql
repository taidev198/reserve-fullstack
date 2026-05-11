-- At most one active reservation per order (data cleanup before index).
-- Allows historical rows (CONFIRMED/CANCELLED) while preventing duplicate
-- concurrent PENDING holds caused by retries/races.
--
-- Some environments already contain duplicate PENDING rows from earlier race
-- windows. Before enforcing uniqueness, keep the newest PENDING row per order
-- and cancel older duplicates while releasing their quantities from inventory.
-- Partial unique index is applied in V4 (PostgreSQL only; see V4 *.postgresql.sql).
WITH ranked_pending AS (
    SELECT
        id,
        order_id,
        ROW_NUMBER() OVER (
            PARTITION BY order_id
            ORDER BY created_at DESC, id DESC
        ) AS rn
    FROM reservations
    WHERE status = 'PENDING'
),
to_cancel AS (
    SELECT id
    FROM ranked_pending
    WHERE rn > 1
),
release_by_product AS (
    SELECT
        ri.product_id,
        SUM(ri.quantity) AS qty_to_release
    FROM reservation_items ri
    JOIN to_cancel tc ON tc.id = ri.reservation_id
    GROUP BY ri.product_id
)
UPDATE inventory i
SET reserved_quantity = GREATEST(0, i.reserved_quantity - rbp.qty_to_release),
    updated_at = CURRENT_TIMESTAMP
FROM release_by_product rbp
WHERE i.product_id = rbp.product_id;

WITH ranked_pending AS (
    SELECT
        id,
        order_id,
        ROW_NUMBER() OVER (
            PARTITION BY order_id
            ORDER BY created_at DESC, id DESC
        ) AS rn
    FROM reservations
    WHERE status = 'PENDING'
)
UPDATE reservations r
SET status = 'CANCELLED',
    updated_at = CURRENT_TIMESTAMP
FROM ranked_pending rp
WHERE r.id = rp.id
  AND rp.rn > 1;
