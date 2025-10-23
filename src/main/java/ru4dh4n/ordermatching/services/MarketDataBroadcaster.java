package ru4dh4n.ordermatching.services;

import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import ru4dh4n.ordermatching.grpc.Trade;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// FIXME: probably should fix this

@Service
public class MarketDataBroadcaster {
    private final Map<String, List<StreamObserver<Trade>>> observers = new ConcurrentHashMap<>();

    public void addObserver(String instrumentId, StreamObserver<Trade> observer) {
        observers.computeIfAbsent(instrumentId, k -> new CopyOnWriteArrayList<>()).add(observer);
    }

    public void removeObserver(String instrumentId, StreamObserver<Trade> observer) {
        if (observers.containsKey(instrumentId)) {
            observers.get(instrumentId).remove(observer);
        }
    }

    public void onNewTrade(Trade trade) {
        String instrumentId = trade.getInstrumentId();

        if (!observers.containsKey(instrumentId)) return;

        for (StreamObserver<Trade> observer : observers.get(instrumentId)) {
            try {
                observer.onNext(trade);
            } catch (Exception e) {
                removeObserver(instrumentId, observer);
            }
        }
    }
}
