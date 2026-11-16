package com.github.diovanibmotta.spacebased.processingunit;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;

import java.util.List;

import static com.github.diovanibmotta.spacebased.contracts.GridNames.CLUSTER_NAME;
import static com.github.diovanibmotta.spacebased.contracts.GridNames.DATA_PUMP_QUEUE;
import static com.github.diovanibmotta.spacebased.contracts.GridNames.INVENTORY_MAP;

/**
 * Builds the Hazelcast member configuration. Kept outside Spring so tests can start several members in one JVM.
 * <p>
 * Replication: every entry has {@code backups} synchronous backup copies. With 3 units and backups=2 each unit
 * holds the whole inventory (the "replicated cache" of the book), while writes stay atomic per key.
 */
public final class GridConfigFactory {

    private GridConfigFactory() {
    }

    public static Config create(int port, List<String> members, int backups) {
        Config config = new Config();
        config.setClusterName(CLUSTER_NAME);
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.phone.home.enabled", "false");

        config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        TcpIpConfig tcpIp = join.getTcpIpConfig().setEnabled(true);
        members.forEach(tcpIp::addMember);

        config.getMapConfig(INVENTORY_MAP).setBackupCount(backups).setAsyncBackupCount(0);
        config.getQueueConfig(DATA_PUMP_QUEUE).setBackupCount(backups).setAsyncBackupCount(0);
        return config;
    }
}
