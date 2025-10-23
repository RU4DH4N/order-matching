package ru4dh4n.ordermatching.components;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru4dh4n.ordermatching.dao.InstrumentDao;
import ru4dh4n.ordermatching.helper.Instrument;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InstrumentRegistry {
    private final InstrumentDao instrumentDao;
    private volatile Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    public InstrumentRegistry(InstrumentDao instrumentDao) {
        this.instrumentDao = instrumentDao;
    }

    @PostConstruct
    public void init() {
        refreshInstruments();
    }

    @Scheduled(cron = "0 0/15 * * * ?")
    public void scheduledRefresh() {
        refreshInstruments();
    }

    public void refreshInstruments() {
        try {
            this.instruments = new ConcurrentHashMap<>(instrumentDao.getAllInstruments());
        } catch (Exception e) {
            System.err.println("Failed to refresh instruments: " + e.getMessage());
        }
    }

    public Optional<Instrument> getInstrument(String instrumentId) {
        return Optional.ofNullable(instruments.get(instrumentId));
    }
}
