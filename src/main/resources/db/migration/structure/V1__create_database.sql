
CREATE TABLE IF NOT EXISTS auction_item (
    id UUID PRIMARY KEY,
    external_id VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL,
    category VARCHAR(50),
    purchase_date DATE,
    purchase_price NUMERIC(10, 2),
    bidding_end_date DATE NOT NULL,
    starting_price INTEGER NOT NULL,
    minimum_raise INTEGER NOT NULL,
    times_renewed INTEGER DEFAULT 0,
    is_transferred BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS bid (
   id UUID PRIMARY KEY,
   fk_auction_item_id UUID NOT NULL,
   bid_price INTEGER NOT NULL,
   bidder_email VARCHAR(255) NOT NULL,
   bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
   is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
   FOREIGN KEY (fk_auction_item_id) REFERENCES auction_item(id)
);


