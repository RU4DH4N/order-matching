package ru4dh4n.ordermatching.helper;

import java.math.BigDecimal;
import java.time.Instant;

public class Trade {

    // both access the same, but with user-id obvs
    private final long makerOrderId;
    private final long takerOrderId;

    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant timestamp;

    public Trade(long makerOrderid, long takerOrderid, BigDecimal price, BigDecimal quantity) {
        this.makerOrderId = makerOrderid;
        this.takerOrderId = takerOrderid;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = Instant.now();
    }

    public long getMakerOrderId() { return this.makerOrderId; }
    public long getTakerOrderId() { return this.takerOrderId; }
    public BigDecimal getPrice() { return this.price; }
    public BigDecimal getQuantity() { return this.quantity; }
    public Instant getTimestamp() { return this.timestamp; }

    @Override
    public String toString() {
        return "Trade{" +
                ", price=" + price +
                ", quantity=" + quantity +
                ", makerOrderId='" + makerOrderId + '\'' +
                ", takerOrderId='" + takerOrderId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
