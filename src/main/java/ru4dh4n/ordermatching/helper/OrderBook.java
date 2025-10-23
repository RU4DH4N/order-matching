package ru4dh4n.ordermatching.helper;

import ru4dh4n.ordermatching.components.InstrumentRegistry;

import java.math.BigDecimal;
import java.util.*;

public class OrderBook {

    private final String instrumentId;
    private final InstrumentRegistry instrumentRegistry;

    private final PriorityQueue<Order> buyOrders;
    private final PriorityQueue<Order> sellOrders;

    public OrderBook(String instrumentId, InstrumentRegistry instrumentRegistry) {
        this.instrumentId = instrumentId;
        this.instrumentRegistry = instrumentRegistry;

        this.buyOrders = new PriorityQueue<>(Comparator
                .comparing(Order::getPrice, Comparator.reverseOrder())
                .thenComparing(Order::getOrderId));

        this.sellOrders = new PriorityQueue<>(Comparator
                .comparing(Order::getPrice)
                .thenComparing(Order::getOrderId));
    }

    public synchronized void processOrder(Order newOrder, TradeHandler tradeHandler) {
        assert(newOrder.getInstrumentId().equals(instrumentId));

        Optional<Instrument> instrument = instrumentRegistry.getInstrument(newOrder.getInstrumentId());
        if (instrument.isEmpty()) return;

        if (newOrder.getSide() == Order.Side.BUY) {
            matchWithSellOrders(newOrder, tradeHandler);

            if (!newOrder.isFulfilled()) {
                buyOrders.add(newOrder);
            }
        } else if (newOrder.getSide() == Order.Side.SELL) {
            matchWithBuyOrders(newOrder, tradeHandler);

            if (!newOrder.isFulfilled()) {
                sellOrders.add(newOrder);
            }
        } else {
            throw new IllegalArgumentException("Invalid order side " + newOrder.getSide());
        }
    }

    private void matchWithBuyOrders(Order sellOrder, TradeHandler handler) {
        while (!buyOrders.isEmpty() && !sellOrder.isFulfilled()) {
            Order makerOrder = buyOrders.peek();

            if (sellOrder.getPrice().compareTo(makerOrder.getPrice()) > 0) break;

            if (match(makerOrder, sellOrder, handler, buyOrders)) break;
        }
    }

    private void matchWithSellOrders(Order buyOrder, TradeHandler handler) {
        while (!sellOrders.isEmpty() && !buyOrder.isFulfilled()) {
            Order makerOrder = sellOrders.peek();

            if (buyOrder.getPrice().compareTo(makerOrder.getPrice()) < 0) break;

            if (match(makerOrder, buyOrder, handler, sellOrders)) break;
        }
    }

    private boolean match(Order makerOrder, Order takerOrder, TradeHandler tradeHandler, PriorityQueue<Order> orderQueue) {
        BigDecimal matchQty = makerOrder.getRemainingQuantity().min(takerOrder.getRemainingQuantity());
        BigDecimal tradePrice = makerOrder.getPrice();

        boolean success = tradeHandler.onTrade(
                makerOrder.getOrderId(),
                takerOrder.getOrderId(),
                tradePrice,
                matchQty
        );

        if (!success) return true;

        makerOrder.addFulfilledQuantity(matchQty);
        takerOrder.addFulfilledQuantity(matchQty);

        if (makerOrder.isFulfilled()) {
            orderQueue.poll();
        }
        return false;
    }
}
