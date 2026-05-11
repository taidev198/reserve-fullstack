-- Seed catalog so the UI has something to show on first launch.
INSERT INTO products (sku, name) VALUES
  ('A100', 'Wireless Mouse'),
  ('B200', 'Mechanical Keyboard'),
  ('C300', '27" Monitor'),
  ('D400', 'USB-C Hub'),
  ('E500', 'Noise-Cancelling Headphones');

INSERT INTO inventory (product_id, total_quantity, reserved_quantity)
SELECT id, 100, 0 FROM products WHERE sku = 'A100';
INSERT INTO inventory (product_id, total_quantity, reserved_quantity)
SELECT id, 50,  0 FROM products WHERE sku = 'B200';
INSERT INTO inventory (product_id, total_quantity, reserved_quantity)
SELECT id, 20,  0 FROM products WHERE sku = 'C300';
INSERT INTO inventory (product_id, total_quantity, reserved_quantity)
SELECT id, 10,  0 FROM products WHERE sku = 'D400';
INSERT INTO inventory (product_id, total_quantity, reserved_quantity)
SELECT id, 5,   0 FROM products WHERE sku = 'E500';
