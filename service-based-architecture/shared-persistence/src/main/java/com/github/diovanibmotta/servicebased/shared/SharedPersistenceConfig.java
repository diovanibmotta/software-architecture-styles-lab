package com.github.diovanibmotta.servicebased.shared;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Each service imports this to get the shared entities and repositories. */
@Configuration
@EntityScan(basePackageClasses = SharedPersistenceConfig.class)
@EnableJpaRepositories(basePackageClasses = SharedPersistenceConfig.class)
public class SharedPersistenceConfig {
}
