package ru4dh4n.ordermatching.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru4dh4n.ordermatching.helper.Order;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

@Repository
public class OrderDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<Order> saveOrder(String userId, String instrumentId, Order.Side side, BigDecimal price, BigDecimal totalQuantity) {
        String sql = "INSERT INTO orders(user_id, instrument, side, quantity, price) VALUES (?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, userId);
            ps.setString(2, instrumentId);
            ps.setString(3, side.name());
            ps.setString(4, totalQuantity.toPlainString());
            ps.setString(5, price.toPlainString());
            return ps;
        }, keyHolder);

        if (Objects.isNull(keyHolder.getKey())) { return Optional.empty(); }
        long orderId = keyHolder.getKey().longValue();

        return Optional.of(new Order(orderId, userId, instrumentId, side, totalQuantity, price));
    }

    public boolean orderComplete(long orderId) {
        String sql = "SELECT complete FROM orders WHERE order_id = ?";
        try {
            Boolean complete = jdbcTemplate.queryForObject(sql, Boolean.class, orderId);
            return complete != null && complete;
        } catch (EmptyResultDataAccessException | NumberFormatException ex) {
            return false;
        }
    }
}