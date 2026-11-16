package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class ReservationRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public ReservationRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Idempotent: the pump delivers at-least-once, so a replayed message (same reservationId) is ignored.
     *
     * @return true if applied, false if it was a duplicate
     */
    public boolean save(ReservationConfirmed r) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                jdbc.sql("INSERT INTO reservations (id, event_id, customer_id, quantity, confirmed_at) VALUES (?, ?, ?, ?, ?)")
                        .params(r.reservationId(), r.eventId(), r.customerId(), r.quantity(), Timestamp.from(r.confirmedAt()))
                        .update();
                jdbc.sql("UPDATE events SET available_seats = available_seats - ? WHERE id = ?")
                        .params(r.quantity(), r.eventId())
                        .update();
                return true;
            }));
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public List<EventInventory> findAllEvents() {
        return jdbc.sql("SELECT id, name, total_seats, available_seats FROM events")
                .query((rs, i) -> new EventInventory(rs.getString("id"), rs.getString("name"),
                        rs.getInt("total_seats"), rs.getInt("available_seats")))
                .list();
    }

    public int availableSeats(String eventId) {
        return jdbc.sql("SELECT available_seats FROM events WHERE id = ?").param(eventId).query(Integer.class).single();
    }
}
