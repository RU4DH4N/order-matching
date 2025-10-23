package ru4dh4n.ordermatching.helper;

import java.math.BigDecimal;

@FunctionalInterface
public interface TradeHandler {
    boolean onTrade(
            long makerOrderId,
            long takerOrderId,
            BigDecimal price,
            BigDecimal quantity
    );
}
