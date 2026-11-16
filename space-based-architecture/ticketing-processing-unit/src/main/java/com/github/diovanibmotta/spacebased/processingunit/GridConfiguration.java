package com.github.diovanibmotta.spacebased.processingunit;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class GridConfiguration {

    /** Starts this processing unit's embedded data-grid member and joins the cluster. */
    @Bean(destroyMethod = "shutdown")
    HazelcastInstance hazelcastInstance(@Value("${grid.port:5701}") int port,
                                        @Value("${grid.members:127.0.0.1:5701}") List<String> members,
                                        @Value("${grid.backups:2}") int backups) {
        return Hazelcast.newHazelcastInstance(GridConfigFactory.create(port, members, backups));
    }
}
