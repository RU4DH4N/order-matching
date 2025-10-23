package ru4dh4n.ordermatching.helper;

import org.springframework.lang.NonNull;

import java.math.BigDecimal;

public record Instrument(String instrumentId, String name, BigDecimal minOrderQuantity, BigDecimal minDustQuantity) {

    @Override
    @NonNull
    public String toString() {
        return "Instrument{" +
                "instrumentId='" + instrumentId + '\'' +
                ", name='" + name + '\'' +
                ", minOrderQuantity=" + minOrderQuantity +
                ", minDustQuantity=" + minDustQuantity +
                '}';
    }
}
