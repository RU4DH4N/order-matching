CREATE TABLE orders (
                        order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        instrument TEXT NOT NULL,
                        side TEXT NOT NULL CHECK(side IN ('BUY', 'SELL')),
                        quantity TEXT NOT NULL,
                        price TEXT NOT NULL,
                        complete BOOLEAN NOT NULL DEFAULT false,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (instrument) REFERENCES instruments(instrument_id)
);

CREATE TABLE trades (
                        trade_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        instrument TEXT NOT NULL,
                        quantity TEXT NOT NULL,
                        price TEXT NOT NULL,
                        maker_order_id INT NOT NULL,
                        taker_order_id INT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (instrument) REFERENCES instruments(instrument_id),
                        FOREIGN KEY (maker_order_id) REFERENCES orders(order_id),
                        FOREIGN KEY (taker_order_id) REFERENCES orders(order_id)
);

CREATE TABLE instruments (
                             instrument_id TEXT PRIMARY KEY,
                             name TEXT NOT NULL,
                             min_order_quantity TEXT NOT NULL,
                             min_dust_quantity TEXT NOT NULL
);

-- Remove this (Testing)
INSERT INTO instruments (instrument_id, name, min_order_quantity, min_dust_quantity) VALUES
                                                                                         ('BTC-USD', 'Bitcoin/US Dollar', '0.0001', '0.00000001'),
                                                                                         ('ETH-USD', 'Ethereum/US Dollar', '0.001', '0.000001'),
                                                                                         ('SOL-USD', 'Solana/US Dollar', '0.01', '0.0001');