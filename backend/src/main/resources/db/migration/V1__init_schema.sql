-- Catalog of physical SKUs known to the warehouse.
CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    sku        VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Per-SKU inventory. Kept as a separate row from products so that
-- inventory writes (reserve/release) can be locked independently of
-- product catalog reads. total_quantity is "on-hand" stock and
-- reserved_quantity is the sum of all currently-held reservations.
-- Available = total_quantity - reserved_quantity.
CREATE TABLE inventory (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT      NOT NULL UNIQUE REFERENCES products(id),
    total_quantity     INTEGER     NOT NULL,
    reserved_quantity  INTEGER     NOT NULL DEFAULT 0,
    version            BIGINT      NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_total_nonneg     CHECK (total_quantity >= 0),
    CONSTRAINT chk_inventory_reserved_nonneg  CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_inventory_no_oversell      CHECK (reserved_quantity <= total_quantity)
);

CREATE TABLE reservations (
    id          BIGSERIAL PRIMARY KEY,
    order_id    VARCHAR(64) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reservation_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED'))
);

-- One reservation can hold multiple SKUs. Each line is immutable
-- after creation; lifecycle (confirm/cancel) lives on the parent.
CREATE TABLE reservation_items (
    id             BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT  NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    product_id     BIGINT  NOT NULL REFERENCES products(id),
    quantity       INTEGER NOT NULL,
    CONSTRAINT chk_reservation_item_qty_pos CHECK (quantity > 0)
);

CREATE INDEX idx_reservations_order_id   ON reservations(order_id);
CREATE INDEX idx_reservations_status     ON reservations(status);
CREATE INDEX idx_reservation_items_resv  ON reservation_items(reservation_id);
CREATE INDEX idx_reservation_items_prod  ON reservation_items(product_id);
