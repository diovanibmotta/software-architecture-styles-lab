package com.github.diovanibmotta.servicebased.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Compare-and-set PENDING -> PAID. Returns 0 when the order was already paid (or does not exist). */
    @Modifying(clearAutomatically = true)
    @Query("update CustomerOrder o set o.status = 'PAID' where o.id = :id and o.status = 'PENDING'")
    int markPaid(@Param("id") Long id);
}
