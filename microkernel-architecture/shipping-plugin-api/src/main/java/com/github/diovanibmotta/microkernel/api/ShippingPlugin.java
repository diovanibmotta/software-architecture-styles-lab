package com.github.diovanibmotta.microkernel.api;

import java.util.List;

/**
 * The plug-in contract. Plug-ins are discovered with {@link java.util.ServiceLoader}
 * (a {@code META-INF/services/...ShippingPlugin} file inside the plug-in jar).
 */
public interface ShippingPlugin {

    /** Stable unique id, e.g. {@code express}. */
    String id();

    String version();

    /** Quotes for this request; an empty list means "not applicable" (e.g. route not served). */
    List<Quote> quote(QuoteRequest request);

    /** Lifecycle hook called once after loading, before the first quote. */
    default void start() {}

    /** Lifecycle hook called before unloading; release resources here. */
    default void stop() {}
}
