package com.campus.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EntityScan("com.campus.model")
@EnableJpaRepositories("com.campus.repository")
public class CampusPersistenceAutoConfiguration {
}
