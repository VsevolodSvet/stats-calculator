package com.kochmarev.statscalculator.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import java.util.*
import javax.sql.DataSource


@Configuration
@EnableJpaRepositories("com.kochmarev.statscalculator.repository")
class DataConfiguration {
    @Value("\${spring.datasource.username}")
    private val username: String? = null

    @Value("\${spring.datasource.driverClassName}")
    private val driverClassName: String? = null

    @Value("\${spring.datasource.password}")
    private val password: String? = null

    @Value("\${spring.datasource.url}")
    private val url: String? = null

    @Value("\${spring.jpa.hibernate.dialect}")
    private val dialect: String? = null

    @Value("\${spring.jpa.hibernate.show_sql}")
    private val showSql: String? = null

    @Bean
    fun jpaVendorAdapter() : HibernateJpaVendorAdapter {
        return HibernateJpaVendorAdapter()
    }

    @Bean
    fun dataSource(): DataSource? {
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName(driverClassName.toString())
        dataSource.username = username
        dataSource.password = password
        dataSource.url = url
        return dataSource
    }

    @Bean
    fun entityManagerFactory() : LocalContainerEntityManagerFactoryBean {
        val localContainerEntityManagerFactoryBean = LocalContainerEntityManagerFactoryBean()
        localContainerEntityManagerFactoryBean.dataSource = dataSource()!!
        localContainerEntityManagerFactoryBean.setPackagesToScan("com.kochmarev.statscalculator.entity")
        localContainerEntityManagerFactoryBean.jpaVendorAdapter = jpaVendorAdapter()
        val jpaProps = Properties()
        jpaProps.setProperty("hibernate.dialect", dialect)
        jpaProps.setProperty("hibernate.show_sql", showSql)
        localContainerEntityManagerFactoryBean.setJpaProperties(jpaProps)
        return localContainerEntityManagerFactoryBean
    }

    @Bean
    fun transactionManager() : JpaTransactionManager {
        val jpaTransactionManager = JpaTransactionManager()
        val jpaProps = Properties()
        jpaProps.setProperty("entityManagerFactory", "entityManagerFactory")
        jpaTransactionManager.setJpaProperties(jpaProps)
        return jpaTransactionManager
    }
}