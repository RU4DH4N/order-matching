package ru4dh4n.ordermatching.helper;

import java.math.BigDecimal;
import java.util.Objects;

public final class Order implements Comparable<Order> {
    public enum Side{BUY, SELL}

    private final long orderId;
    private final String userId;

    private final String instrumentId;
    private final Side side;

    private final BigDecimal price;
    private final BigDecimal totalQuantity;
    private BigDecimal quantityFulfilled;

    public Order(long orderId, String userId, String instrumentId, Side side, BigDecimal totalQuantity, BigDecimal price) {
        this.orderId = orderId;
        this.userId = userId;

        this.instrumentId = instrumentId;
        this.side = side;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.quantityFulfilled = BigDecimal.ZERO;
    }

    public long getOrderId() { return this.orderId; }
    public String getUserId() { return this.userId; }
    public String getInstrumentId() { return this.instrumentId; }
    public Side getSide() { return this.side; }
    public BigDecimal getPrice() { return this.price; }
    public BigDecimal getTotalQuantity() { return this.totalQuantity; }

    public BigDecimal getRemainingQuantity() { return this.totalQuantity.subtract(this.quantityFulfilled); }

    public boolean isFulfilled() { return quantityFulfilled.compareTo(totalQuantity) >= 0; }
    public void addFulfilledQuantity(BigDecimal quantity) { this.quantityFulfilled = this.quantityFulfilled.add(quantity); }

    @Override
    public int compareTo(Order o) {
        return Long.compare(this.orderId, o.orderId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return orderId == order.getOrderId();
    }

    @Override
    public int hashCode() { return Objects.hash(orderId); }

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", userId='" + userId + '\'' +
                ", instrumentId='" + instrumentId + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", totalQuantity=" + totalQuantity +
                ", quantityFulfilled=" + quantityFulfilled +
                ", remaining=" + getRemainingQuantity() +
                '}';
    }
}