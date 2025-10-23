package ru4dh4n.ordermatching.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru4dh4n.ordermatching.helper.Trade;

import java.sql.*;
import java.util.List;
import java.util.Optional;

@Repository
public class TradeDao {

    private final JdbcTemplate jdbcTemplate;

    public TradeDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> saveTrade(String instrument, Trade trade) {
        String query = "INSERT INTO trades (instrument, quantity, price, maker_order_id, taker_order_id) " +
                "VALUES (?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, instrument);
            ps.setString(2, trade.getQuantity().toPlainString());
            ps.setString(3, trade.getPrice().toPlainString());
            ps.setLong(4, trade.getMakerOrderId());
            ps.setLong(5, trade.getTakerOrderId());
            return ps;
        }, keyHolder);

        return Optional.ofNullable(keyHolder.getKey()).map(Number::longValue);
    }

    public List<Trade> getTrades(long orderId, Timestamp from) {
        if (from == null) {
            String query = "SELECT * FROM trades WHERE (maker_order_id = ? OR taker_order_id = ?)";
            return jdbcTemplate.query(query, this::mapRowToTrade, orderId, orderId);
        }

        String query = "SELECT * FROM trades WHERE (maker_order_id = ? OR taker_order_id = ?) AND created_at >= ?";
        return jdbcTemplate.query(query, this::mapRowToTrade, orderId, orderId, from);
    }

    private Trade mapRowToTrade(ResultSet rs, int rowNum) throws SQLException {
        return new Trade(
                rs.getLong("maker_order_id"),
                rs.getLong("taker_order_id"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("quantity")
        );
    }
}