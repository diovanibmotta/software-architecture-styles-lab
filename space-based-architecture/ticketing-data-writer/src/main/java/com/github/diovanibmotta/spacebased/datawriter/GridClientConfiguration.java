package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.GridNames;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class GridClientConfiguration {

    /** The writer is NOT a grid member (holds no data); it is a client of the processing units' grid. */
    @Bean(destroyMethod = "shutdown")
    HazelcastInstance gridClient(@Value("${grid.members}") List<String> members) {
        ClientConfig config = new ClientConfig();
        config.setClusterName(GridNames.CLUSTER_NAME);
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.getNetworkConfig().setAddresses(members);
        // keep retrying forever: the writer may boot before the processing units
        config.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(Long.MAX_VALUE);
        return HazelcastClient.newHazelcastClient(config);
    }
}
