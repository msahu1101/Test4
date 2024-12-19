package com.mgm.payments.processing.service.config.datasource;

import com.mgm.payments.processing.service.repository.jpa.primary.PrimaryRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@EnableJpaRepositories(
        basePackages = {"com.mgm.payments.processing.service.repository.jpa.primary"},
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager",
        basePackageClasses = PrimaryRepository.class
)
public class PrimaryRepositoryConfig {

    @Value("${spring.primary.datasource.url}")
    private String primaryDataSourceUrl;

    @Value("${spring.datasource.hikari.connection-timeout}")
    private String hikariConnectionTimeout;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private String hikariMaximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle}")
    private String hikariMinimumIdle;

    @Value("${spring.datasource.hikari.idle-timeout}")
    private String hikariIdleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime}")
    private String hikariMaxLifetime;

    @Value("${spring.datasource.hikari.auto-commit}")
    private String hikariAutoCommit;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String hibernateDdlAuto;

    @Value("${spring.jpa.properties.hibernate.dialect}")
    private String hibernateDialect;

    @Value("${spring.jpa.hibernate.naming.physical-strategy}")
    private String hibernatePhysicalNamingStrategy;

    @Primary
    @Bean
    public DataSource primaryDataSource() {
        HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class).url(primaryDataSourceUrl)
                .driverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver").build();
        dataSource.setConnectionTimeout(Long.parseLong(hikariConnectionTimeout));
        dataSource.setMaximumPoolSize(Integer.parseInt(hikariMaximumPoolSize));
        dataSource.setMinimumIdle(Integer.parseInt(hikariMinimumIdle));
        dataSource.setIdleTimeout(Long.parseLong(hikariIdleTimeout));
        dataSource.setMaxLifetime(Long.parseLong(hikariMaxLifetime));
        dataSource.setAutoCommit(Boolean.parseBoolean(hikariAutoCommit));
        return dataSource;
    }


    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(@Qualifier("primaryDataSource") DataSource primaryDataSource
    ) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(primaryDataSource);
        emf.setPackagesToScan("com.mgm.payments.processing.service.entity.jpa"); // adjust package to your entities
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", hibernateDdlAuto);
        properties.put("hibernate.dialect", hibernateDialect);
        properties.put("hibernate.physical_naming_strategy", hibernatePhysicalNamingStrategy);
        emf.setJpaPropertyMap(properties);
        return emf;
    }



    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory primaryEntityManagerFactory) {
        return new JpaTransactionManager(primaryEntityManagerFactory);
    }


}