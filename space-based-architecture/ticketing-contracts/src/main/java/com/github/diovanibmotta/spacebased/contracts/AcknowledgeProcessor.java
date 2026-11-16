package com.github.diovanibmotta.spacebased.contracts;

import com.hazelcast.map.EntryProcessor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/** Executed on the grid by the data writer, after the reservations were committed to the database. */
public class AcknowledgeProcessor implements EntryProcessor<String, EventInventory, Void>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Set<String> persistedReservationIds;

    public AcknowledgeProcessor(Set<String> persistedReservationIds) {
        this.persistedReservationIds = Set.copyOf(persistedReservationIds);
    }

    @Override
    public Void process(Map.Entry<String, EventInventory> entry) {
        if (entry.getValue() != null) {
            entry.setValue(entry.getValue().acknowledge(persistedReservationIds));
        }
        return null;
    }
}
