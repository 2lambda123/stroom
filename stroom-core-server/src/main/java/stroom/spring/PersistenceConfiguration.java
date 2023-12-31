/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.spring;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import stroom.node.server.GlobalProperties;
import stroom.util.config.StroomProperties;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Version;

import javax.persistence.EntityManagerFactory;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Configures anything related to persistence, e.g. transaction management, the
 * entity manager factory, data sources.
 */
@Configuration
@EnableTransactionManagement
public class PersistenceConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(PersistenceConfiguration.class);

    public PersistenceConfiguration() {
        LOGGER.info("PersistenceConfiguration loading...");
    }

    @Bean
    public ComboPooledDataSource dataSource(final GlobalProperties globalProperties) throws PropertyVetoException {
        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(StroomProperties.getProperty("stroom.jdbcDriverClassName"));
        dataSource.setJdbcUrl(StroomProperties.getProperty("stroom.jdbcDriverUrl|trace"));
        dataSource.setUser(StroomProperties.getProperty("stroom.jdbcDriverUsername"));
        dataSource.setPassword(StroomProperties.getProperty("stroom.jdbcDriverPassword"));
        dataSource.setMinPoolSize(1);
        dataSource.setMaxPoolSize(StroomProperties.getIntProperty("stroom.jdbcMaxPoolSize", 10));
        dataSource.setMaxIdleTimeExcessConnections(60);
        dataSource.setIdleConnectionTestPeriod(60);
        dataSource.setPreferredTestQuery("select 1");
        dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.connectionTesterClassName"));
        return dataSource;
    }

    @Bean
    public Flyway flyway(final ComboPooledDataSource dataSource) throws PropertyVetoException {
        final String jpaHbm2DdlAuto = StroomProperties.getProperty("stroom.jpaHbm2DdlAuto", "validate");
        if (!"update".equals(jpaHbm2DdlAuto)) {
            final Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);

            final String driver = StroomProperties.getProperty("stroom.jdbcDriverClassName");
            if (driver.toLowerCase().contains("hsqldb")) {
                flyway.setLocations("stroom/db/migration/hsqldb");
            } else {
                flyway.setLocations("stroom/db/migration/mysql");
            }

            Version version = null;
            boolean usingFlyWay = false;
            LOGGER.info("Testing installed Stroom schema version");

            try {
                try (final Connection connection = dataSource.getConnection()) {
                    try (final Statement statement = connection.createStatement()) {
                        try (final ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version ORDER BY installed_rank DESC")) {
                            if (resultSet.next()) {
                                usingFlyWay = true;

                                final String ver = resultSet.getString(1);
                                final String[] parts = ver.split("\\.");
                                int maj = 0;
                                int min = 0;
                                int pat = 0;
                                if (parts.length > 0) {
                                    maj = Integer.valueOf(parts[0]);
                                }
                                if (parts.length > 1) {
                                    min = Integer.valueOf(parts[1]);
                                }
                                if (parts.length > 2) {
                                    pat = Integer.valueOf(parts[2]);
                                }

                                version = new Version(maj, min, pat);
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                LOGGER.debug(e.getMessage());
                // Ignore.
            }

            if (version == null) {
                try {
                    try (final Connection connection = dataSource.getConnection()) {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT VER_MAJ, VER_MIN, VER_PAT FROM STROOM_VER ORDER BY VER_MAJ DESC, VER_MIN DESC, VER_PAT DESC LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }

            if (version == null) {
                try {
                    try (final Connection connection = dataSource.getConnection()) {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FD LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(2, 0, 0);
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }

            if (version == null) {
                try {
                    try (final Connection connection = dataSource.getConnection()) {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FEED LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(2, 0, 0);
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }

            if (version != null) {
                LOGGER.info("Detected current Stroom version is v" + version.toString());
            } else {
                LOGGER.info("This is a new installation!");
            }


            if (version == null) {
                // If we have no version then this is a new Stroom instance so perform full FlyWay migration.
                flyway.migrate();
            } else if (usingFlyWay) {
                // If we are already using FlyWay then allow FlyWay to attempt migration.
                flyway.migrate();
            } else if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
                // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
                flyway.setBaselineVersionAsString("4.0.60");
                flyway.baseline();
                flyway.migrate();
            } else {
                final String message = "The current Stroom version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
                LOGGER.fatal(message);
                throw new RuntimeException(message);
            }

            return flyway;
        }

        return null;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            final ComboPooledDataSource dataSource, final Flyway flyway) throws PropertyVetoException {
        final LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPersistenceUnitName("StroomPersistenceUnit");
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        // entityManagerFactory.setPackagesToScan("stroom.**.shared",
        // "stroom.**.shared");
        entityManagerFactory.setPackagesToScan("stroom");

        final Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.hbm2ddl.auto", StroomProperties.getProperty("stroom.jpaHbm2DdlAuto"));
        jpaProperties.put("hibernate.show_sql", StroomProperties.getProperty("stroom.showSql"));
        jpaProperties.put("hibernate.dialect", StroomProperties.getProperty("stroom.jpaDialect"));
        entityManagerFactory.setJpaProperties(jpaProperties);
        return entityManagerFactory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(final EntityManagerFactory entityManagerFactory) {
        final JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}
