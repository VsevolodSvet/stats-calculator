package com.kochmarev.statscalculator

import com.kochmarev.statscalculator.dao.UserDao
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {

    /* todo пока не работает, найти, как можно создать объект, реализующий интерфейс, не создаваемый явно
    @Bean
    fun userDao() : UserDao {
        return UserDao()
    }
    */
}