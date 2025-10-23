package ru4dh4n.ordermatching.services;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru4dh4n.ordermatching.dao.OrderDao;
import ru4dh4n.ordermatching.dao.TradeDao;
import ru4dh4n.ordermatching.grpc.OrderUpdate;
import ru4dh4n.ordermatching.grpc.OrderUpdateRequest;
import ru4dh4n.ordermatching.grpc.OrderUpdateStream;
import ru4dh4n.ordermatching.helper.Trade;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TradePropagationService {

    private final TaskExecutor taskExecutor;
    private final TradeDao tradeDao;
    private final OrderDao orderDao;

    private final ConcurrentHashMap<Long, List<StreamObserver<OrderUpdateStream>>> orderUpdates = new ConcurrentHashMap<>();

    @Autowired
    public TradePropagationService(TaskExecutor taskExecutor, TradeDao tradeDao, OrderDao orderDao) {
        this.taskExecutor = taskExecutor;
        this.tradeDao = tradeDao;
        this.orderDao = orderDao;
    }

    @Scheduled(cron = "*/30 * * * * ?")
    public void checkObservers() {
        orderUpdates.forEach((s, o) -> {
            orderUpdates.computeIfPresent(s, (key, list) -> {
                list.removeIf(o1 -> {
                    try {
                        o1.onNext(OrderUpdateStream.newBuilder().setKeepAlive(Empty.newBuilder().build()).build());
                        return false;
                    } catch (Exception ignored) {
                        return true;
                    }
                });
                return list.isEmpty() ? null : list;
            });
        });
    }

    private OrderUpdate createOrder(Trade trade) {
        return OrderUpdate.newBuilder()
                .setFulfilledQuantity(trade.getQuantity().toPlainString())
                .setTradePrice(trade.getPrice().toPlainString())
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(trade.getTimestamp().getEpochSecond())
                        .setNanos(trade.getTimestamp().getNano())
                        .build()
                )
                .build();
    }

    private void propagateHistoricalUpdates(Long orderId, java.sql.Timestamp lastUpdate, StreamObserver<OrderUpdateStream> streamObserver) {
        try {
            List<Trade> tradeList = tradeDao.getTrades(orderId, lastUpdate);

            for (Trade trade : tradeList) {
                streamObserver.onNext(OrderUpdateStream.newBuilder().setUpdate(this.createOrder(trade)).build());
            }
        } catch (Exception e) {
            orderUpdates.computeIfPresent(orderId, (key, list) -> {
                list.remove(streamObserver);
                return list.isEmpty() ? null : list;
            });

            try {
                streamObserver.onError(new StatusRuntimeException(
                        Status.INTERNAL.withDescription("Failed to load historical updates")
                ));
            } catch (Exception ignored) {
                // observer might already be disconnected TODO: log this
            }
        }
    }

    public boolean subscribe(long orderId, java.sql.Timestamp lastUpdate, StreamObserver<OrderUpdateStream> streamObserver) {
        try {
            boolean complete = orderDao.orderComplete(orderId);

            if (complete) {
                taskExecutor.execute(() -> propagateHistoricalUpdates(orderId, lastUpdate, streamObserver));
                return false;
            }

            orderUpdates.compute(orderId, (key, list) -> {
                if (list == null) {
                    list = new CopyOnWriteArrayList<>();
                }
                list.add(streamObserver);
                return list;
            });

            taskExecutor.execute(() -> propagateHistoricalUpdates(orderId, lastUpdate, streamObserver));
        } catch (Exception ignored) {
            return false;
        }

        return true;
    }

    public void unsubscribe(Long orderId) {
        List<StreamObserver<OrderUpdateStream>> observers = orderUpdates.remove(orderId);
        if (observers != null) {
            observers.forEach(observer -> {
                try {
                    observer.onCompleted();
                } catch (Exception e) {
                    // client might have disconnected already, TODO: log this probably
                }
            });
        }
    }

    @Async
    public void propagate(Trade trade) {
        List<StreamObserver<OrderUpdateStream>> makerObservers = orderUpdates.get(trade.getMakerOrderId());
        List<StreamObserver<OrderUpdateStream>> takerObservers = orderUpdates.get(trade.getTakerOrderId());

        if ((makerObservers == null || makerObservers.isEmpty()) &&
                (takerObservers == null || takerObservers.isEmpty())) {
            return;
        }

        OrderUpdateStream message = OrderUpdateStream.newBuilder().setUpdate(this.createOrder(trade)).build();

        if (makerObservers != null) {
            makerObservers.forEach(observer -> {
                try {
                    observer.onNext(message);
                } catch (Exception e) {
                    // disconnected client, exception will be caught in the keep-alive loop
                }
            });
        }

        if (takerObservers != null) {
            takerObservers.forEach(observer -> {
                try {
                    observer.onNext(message);
                } catch (Exception e) {
                    // disconnected client, exception will be caught in the keep-alive loop
                }
            });
        }
    }
}