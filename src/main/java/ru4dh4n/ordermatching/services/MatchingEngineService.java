package ru4dh4n.ordermatching.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import ru4dh4n.ordermatching.components.InstrumentRegistry;
import ru4dh4n.ordermatching.dao.OrderDao;
import ru4dh4n.ordermatching.dao.TradeDao;
import ru4dh4n.ordermatching.helper.Order;
import ru4dh4n.ordermatching.helper.OrderBook;
import ru4dh4n.ordermatching.helper.Trade;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MatchingEngineService {

    private final TradePropagationService tradePropagationService;
    // instrumentId -> OrderBook
    private Cache<String, OrderBook> orderBookCache; // FIXME: this will cause a memory leak over time
    private ExecutorService executor;
    private final OrderDao orderDao;
    private final TradeDao tradeDao;
    private final InstrumentRegistry instrumentRegistry;


    @PostConstruct
    public void start() {
        this.orderBookCache = Caffeine.newBuilder().maximumSize(10_000).build();
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public MatchingEngineService(OrderDao orderDao, TradeDao tradeDao, InstrumentRegistry instrumentRegistry, TradePropagationService tradePropagationService) {
        this.orderDao = orderDao;
        this.tradeDao = tradeDao;
        this.instrumentRegistry = instrumentRegistry;
        this.tradePropagationService = tradePropagationService;
    }

    @PreDestroy
    public void shutdown() {
        if (this.executor != null) this.executor.shutdown();
    }

    public Optional<Long> submitOrder(String userId, String instrumentId, Order.Side side,
                                      BigDecimal orderPrice, BigDecimal quantity) {
        Optional<Order> order = orderDao.saveOrder(userId, instrumentId, side, orderPrice, quantity);
        if (order.isEmpty()) { return Optional.empty(); }

        OrderBook orderBook = orderBookCache.get(instrumentId, k -> new OrderBook(instrumentId, instrumentRegistry));
        orderBook.processOrder(order.get(), (makerId, takerId, price, qty) -> {
            Trade trade = new Trade(makerId, takerId, price, qty);
            Optional<Long> tradeIdOpt = tradeDao.saveTrade(instrumentId, trade);
            tradeIdOpt.ifPresent(tradeId ->
                    tradePropagationService.propagate(trade)
            );
            return tradeIdOpt.isPresent();
        });

        return Optional.of(order.get().getOrderId());
    }
}
