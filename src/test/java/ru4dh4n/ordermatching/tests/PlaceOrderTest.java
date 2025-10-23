package ru4dh4n.ordermatching.tests;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import ru4dh4n.ordermatching.grpc.*;
import ru4dh4n.ordermatching.tests.helper.BearerTokenCredentials;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderService including order placement and subscription to updates.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PlaceOrderTest {

    @Value("${jwt.secret}")
    private String validSecret;

    private static final String INVALID_SECRET = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TARGET = "localhost:9090";

    private ManagedChannel channel;
    private OrderServiceGrpc.OrderServiceBlockingStub baseBlockingStub;
    private OrderServiceGrpc.OrderServiceStub baseAsyncStub;

    @BeforeAll
    void setup() {
        channel = ManagedChannelBuilder.forTarget(TARGET)
                .usePlaintext()
                .build();
        baseBlockingStub = OrderServiceGrpc.newBlockingStub(channel);
        baseAsyncStub = OrderServiceGrpc.newStub(channel);
    }

    @AfterAll
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private String generateJwt(String secret, String jti) {
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("test-user")
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Should place buy order, subscribe to updates, then fulfill with sell orders")
    void testPlaceOrderAndSubscribe_FulfillWithSellOrders() throws InterruptedException {
        String jwt = generateJwt(validSecret, UUID.randomUUID().toString());

        OrderServiceGrpc.OrderServiceBlockingStub authedBlockingStub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(jwt));

        OrderRequest buyOrderRequest = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.BUY)
                .setPrice("65000")
                .setQuantity("1.0")
                .build();

        PlaceOrderResponse buyResponse = authedBlockingStub.placeOrder(buyOrderRequest);
        assertNotNull(buyResponse);
        String buyOrderId = buyResponse.getOrderId();
        assertNotNull(buyOrderId);
        System.out.println("Created buy order: " + buyOrderId);

        String subscribeJwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceStub authedAsyncStub =
                baseAsyncStub.withCallCredentials(new BearerTokenCredentials(subscribeJwt));

        OrderUpdateRequest updateRequest = OrderUpdateRequest.newBuilder()
                .setOrderId(buyOrderId)
                .build();

        List<OrderUpdate> receivedUpdates = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(1);
        CountDownLatch updatesLatch = new CountDownLatch(2); // Expecting 2 trade updates FIXME: this could be 2+ depending on stay alive

        StreamObserver<OrderUpdateStream> responseObserver = new StreamObserver<OrderUpdateStream>() {
            @Override
            public void onNext(OrderUpdateStream value) {
                if (value.hasUpdate()) {
                    OrderUpdate update = value.getUpdate();
                    System.out.println("Received update - Fulfilled: " + update.getFulfilledQuantity() +
                            " at price: " + update.getTradePrice());
                    receivedUpdates.add(update);
                    updatesLatch.countDown();
                } else if (value.hasKeepAlive()) {
                    System.out.println("Received keep-alive");
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Stream error: " + t.getMessage());
                completionLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream completed");
                completionLatch.countDown();
            }
        };

        authedAsyncStub.subscribeToOrderUpdates(updateRequest, responseObserver);

        Thread.sleep(500);

        String sellJwt1 = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub authedStub1 =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sellJwt1));

        OrderRequest sellOrderRequest1 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("65000")
                .setQuantity("0.6")
                .build();

        PlaceOrderResponse sellResponse1 = authedStub1.placeOrder(sellOrderRequest1);
        assertNotNull(sellResponse1);
        System.out.println("Created first sell order: " + sellResponse1.getOrderId());

        String sellJwt2 = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub authedStub2 =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sellJwt2));

        OrderRequest sellOrderRequest2 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("65000")
                .setQuantity("0.4")
                .build();

        PlaceOrderResponse sellResponse2 = authedStub2.placeOrder(sellOrderRequest2);
        assertNotNull(sellResponse2);
        System.out.println("Created second sell order: " + sellResponse2.getOrderId());

        boolean updatesReceived = updatesLatch.await(10, TimeUnit.SECONDS);
        assertTrue(updatesReceived, "Should receive 2 trade updates");

        assertEquals(2, receivedUpdates.size(), "Should have received 2 order updates");

        OrderUpdate firstUpdate = receivedUpdates.getFirst();
        assertEquals("0.6", firstUpdate.getFulfilledQuantity());
        assertEquals("65000", firstUpdate.getTradePrice());

        OrderUpdate secondUpdate = receivedUpdates.get(1);
        assertEquals("0.4", secondUpdate.getFulfilledQuantity());
        assertEquals("65000", secondUpdate.getTradePrice());

        System.out.println("Test completed successfully!");
    }

    @Test
    @DisplayName("Should only match sell orders at or below buy order price")
    void testPriceMatching_BuyOrderWithVariedSellPrices() throws InterruptedException {
        String buyJwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub buyStub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(buyJwt));

        OrderRequest buyOrderRequest = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.BUY)
                .setPrice("65000")
                .setQuantity("1.0")
                .build();

        PlaceOrderResponse buyResponse = buyStub.placeOrder(buyOrderRequest);
        assertNotNull(buyResponse);
        String buyOrderId = buyResponse.getOrderId();
        assertNotNull(buyOrderId);
        System.out.println("Created buy order: " + buyOrderId + " (price: 65000, quantity: 1.0)");

        String subscribeJwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceStub subscribeStub =
                baseAsyncStub.withCallCredentials(new BearerTokenCredentials(subscribeJwt));

        OrderUpdateRequest updateRequest = OrderUpdateRequest.newBuilder()
                .setOrderId(buyOrderId)
                .build();

        List<OrderUpdate> receivedUpdates = new ArrayList<>();
        CountDownLatch updatesLatch = new CountDownLatch(3); // FIXME: this could be 3+

        StreamObserver<OrderUpdateStream> responseObserver = new StreamObserver<OrderUpdateStream>() {
            @Override
            public void onNext(OrderUpdateStream value) {
                if (value.hasUpdate()) {
                    OrderUpdate update = value.getUpdate();
                    System.out.println("Received update - Fulfilled: " + update.getFulfilledQuantity() +
                            " at price: " + update.getTradePrice());
                    receivedUpdates.add(update);
                    updatesLatch.countDown();
                } else if (value.hasKeepAlive()) {
                    System.out.println("Received keep-alive");
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Stream error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream completed");
            }
        };

        subscribeStub.subscribeToOrderUpdates(updateRequest, responseObserver);
        Thread.sleep(500);

        // FIXME: the logic below is all wrong, and will result in a failed test

        String sell1Jwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub sell1Stub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sell1Jwt));

        OrderRequest sellOrder1 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("66000")
                .setQuantity("0.5")
                .build();

        PlaceOrderResponse sellResponse1 = sell1Stub.placeOrder(sellOrder1);
        assertNotNull(sellResponse1);
        System.out.println("Created sell order at 66000 (above buy price - should NOT match): " +
                sellResponse1.getOrderId());

        Thread.sleep(500);

        String sell2Jwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub sell2Stub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sell2Jwt));

        OrderRequest sellOrder2 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("64000")
                .setQuantity("0.3")
                .build();

        PlaceOrderResponse sellResponse2 = sell2Stub.placeOrder(sellOrder2);
        assertNotNull(sellResponse2);
        System.out.println("Created sell order at 64000 (below buy price - should match): " +
                sellResponse2.getOrderId());

        Thread.sleep(500);

        String sell3Jwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub sell3Stub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sell3Jwt));

        OrderRequest sellOrder3 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("65000")
                .setQuantity("0.4")
                .build();

        PlaceOrderResponse sellResponse3 = sell3Stub.placeOrder(sellOrder3);
        assertNotNull(sellResponse3);
        System.out.println("Created sell order at 65000 (at buy price - should match): " +
                sellResponse3.getOrderId());

        Thread.sleep(500);

        String sell4Jwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub sell4Stub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(sell4Jwt));

        OrderRequest sellOrder4 = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setPrice("63000")
                .setQuantity("0.3")
                .build();

        PlaceOrderResponse sellResponse4 = sell4Stub.placeOrder(sellOrder4);
        assertNotNull(sellResponse4);
        System.out.println("Created sell order at 63000 (below buy price - should match): " +
                sellResponse4.getOrderId());

        boolean updatesReceived = updatesLatch.await(10, TimeUnit.SECONDS);
        assertTrue(updatesReceived, "Should receive 3 trade updates (orders at or below buy price)");

        assertEquals(3, receivedUpdates.size(), "Should have received exactly 3 order updates");

        OrderUpdate firstUpdate = receivedUpdates.getFirst();
        assertEquals("0.3", firstUpdate.getFulfilledQuantity());
        assertEquals("64000", firstUpdate.getTradePrice());

        OrderUpdate secondUpdate = receivedUpdates.get(1);
        assertEquals("0.4", secondUpdate.getFulfilledQuantity());
        assertEquals("65000", secondUpdate.getTradePrice());

        OrderUpdate thirdUpdate = receivedUpdates.get(2);
        assertEquals("0.3", thirdUpdate.getFulfilledQuantity());
        assertEquals("63000", thirdUpdate.getTradePrice());

        BigDecimal totalFilled = receivedUpdates.stream()
                .map(update -> new BigDecimal(update.getFulfilledQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("1.0"), totalFilled,
                "Total filled quantity should be 1.0 BTC");

        System.out.println("Test completed successfully! Buy order filled with " +
                receivedUpdates.size() + " trades at varying prices.");
    }

    @Test
    @DisplayName("Should succeed when placing an order with a valid JWT in headers")
    void testPlaceOrder_Success() {
        OrderRequest orderRequest = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.BUY)
                .setPrice("65000")
                .setQuantity("0.01")
                .build();

        String jwt = generateJwt(validSecret, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub authedStub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(jwt));

        PlaceOrderResponse response = authedStub.placeOrder(orderRequest);

        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getOrderId());
    }

    @Test
    @DisplayName("Should fail with UNAUTHENTICATED when the JWT signature is invalid")
    void testPlaceOrder_FailsWithInvalidSignature() {
        OrderRequest orderRequest = OrderRequest.newBuilder()
                .setInstrumentId("BTC-USD")
                .setSide(OrderSide.SELL)
                .setQuantity("1")
                .setPrice("100")
                .build();

        String jwt = generateJwt(INVALID_SECRET, UUID.randomUUID().toString());
        OrderServiceGrpc.OrderServiceBlockingStub authedStub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(jwt));

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            authedStub.placeOrder(orderRequest);
        });

        assertEquals(Status.UNAUTHENTICATED.getCode(), exception.getStatus().getCode());
    }

    @Test
    @DisplayName("Should fail with UNAUTHENTICATED when no Authorization header is provided")
    void testPlaceOrder_FailsWithMissingAuthHeader() {
        OrderRequest orderRequest = OrderRequest.newBuilder()
                .setInstrumentId("ETH-USD")
                .setSide(OrderSide.BUY)
                .setQuantity("10")
                .setPrice("3000")
                .build();

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            baseBlockingStub.placeOrder(orderRequest);
        });

        assertEquals(Status.UNAUTHENTICATED.getCode(), exception.getStatus().getCode());
    }

    @Test
    @DisplayName("Should fail when the same JWT (same JTI) is sent twice (replay attack)")
    void testPlaceOrder_FailsOnReplayAttack() {
        OrderRequest orderRequest = OrderRequest.newBuilder()
                .setInstrumentId("SOL-USD")
                .setSide(OrderSide.BUY)
                .setQuantity("100")
                .setPrice("150")
                .build();

        String jti = UUID.randomUUID().toString();
        String jwt = generateJwt(validSecret, jti);

        OrderServiceGrpc.OrderServiceBlockingStub authedStub =
                baseBlockingStub.withCallCredentials(new BearerTokenCredentials(jwt));

        PlaceOrderResponse firstResponse = authedStub.placeOrder(orderRequest);
        assertNotNull(firstResponse);
        assertNotNull(firstResponse.getOrderId());

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            authedStub.placeOrder(orderRequest);
        });

        assertEquals(Status.UNAUTHENTICATED.getCode(), exception.getStatus().getCode());
    }
}