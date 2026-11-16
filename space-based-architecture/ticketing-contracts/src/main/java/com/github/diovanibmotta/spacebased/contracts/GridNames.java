package com.github.diovanibmotta.spacebased.contracts;

/** Names of the distributed structures and cluster used by every participant of the grid. */
public final class GridNames {

    public static final String CLUSTER_NAME = "ticketing-grid";
    /** Distributed map: eventId -> {@link EventInventory}. */
    public static final String INVENTORY_MAP = "event-inventory";
    /** Distributed queue: the data pump (processing units -> data writer). */
    public static final String DATA_PUMP_QUEUE = "reservation-data-pump";

    private GridNames() {
    }
}
