package ru4dh4n.ordermatching.grpc.services;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import ru4dh4n.ordermatching.annotations.PublicEndpoint;
import ru4dh4n.ordermatching.grpc.MarketDataServiceGrpc;
import ru4dh4n.ordermatching.grpc.MarketSubscriptionRequest;
import ru4dh4n.ordermatching.grpc.Trade;
import ru4dh4n.ordermatching.services.MarketDataBroadcaster;

// FIXME: this is all outdated

@GrpcService
public class MarketDataServiceImpl extends MarketDataServiceGrpc.MarketDataServiceImplBase {
    private final MarketDataBroadcaster broadcaster;

    @Autowired
    public MarketDataServiceImpl(MarketDataBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    @PublicEndpoint
    public void subscribeToTrades(MarketSubscriptionRequest request, StreamObserver<Trade> responseObserver) {
        System.out.println(request.toString()); // TODO: replace with a logger + proper word-ey words
        final String instrumentId = request.getInstrumentId();

        // TODO: check that instrumentId is a valid id

        broadcaster.addObserver(instrumentId, responseObserver);

        Context.current().addListener(
                context -> broadcaster.removeObserver(instrumentId, responseObserver),
                Runnable::run
        );
    }
}