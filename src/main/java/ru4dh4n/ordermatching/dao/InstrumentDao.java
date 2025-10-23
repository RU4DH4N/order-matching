package ru4dh4n.ordermatching.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru4dh4n.ordermatching.helper.Instrument;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class InstrumentDao {

    private final JdbcTemplate jdbcTemplate;

    public InstrumentDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private Instrument mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Instrument(
                rs.getString("instrument_id"),
                rs.getString("name"),
                new BigDecimal(rs.getString("min_order_quantity")),
                new BigDecimal(rs.getString("min_dust_quantity"))
        );
    }

    public Map<String, Instrument> getAllInstruments() {
        String query = "SELECT * FROM instruments";
        return jdbcTemplate.query(query, this::mapRow).stream()
                .collect(Collectors.toMap(Instrument::instrumentId, i -> i));
    }
}
