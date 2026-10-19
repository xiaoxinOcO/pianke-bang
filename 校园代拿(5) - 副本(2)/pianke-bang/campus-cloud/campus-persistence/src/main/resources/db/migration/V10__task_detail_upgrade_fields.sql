ALTER TABLE delivery_task
    ADD COLUMN pickup_lat DECIMAL(10,6) NULL,
    ADD COLUMN pickup_lng DECIMAL(10,6) NULL,
    ADD COLUMN dropoff_lat DECIMAL(10,6) NULL,
    ADD COLUMN dropoff_lng DECIMAL(10,6) NULL,
    ADD COLUMN delivery_start_at DATETIME NULL,
    ADD COLUMN delivery_end_at DATETIME NULL,
    ADD COLUMN image_urls LONGTEXT NULL;

