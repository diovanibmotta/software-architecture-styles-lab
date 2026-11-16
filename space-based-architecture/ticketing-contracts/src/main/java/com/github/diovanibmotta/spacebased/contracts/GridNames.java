package com.github.diovanibmotta.spacebased.contracts;

/** Names of the distributed structures and cluster used by every participant of the grid. */
public final class GridNames {

    public static final String CLUSTER_NAME = "ticketing-grid";
    /** Distributed map: eventId -> {@link EventInventory} (stock + outbox of unpersisted reservations). */
    public static final String INVENTORY_MAP = "event-inventory";

    private GridNames() {
    }
}
