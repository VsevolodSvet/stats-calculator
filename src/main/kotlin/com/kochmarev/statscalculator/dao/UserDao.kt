package com.kochmarev.statscalculator.dao

import com.kochmarev.statscalculator.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserDao : JpaRepository<User, Long> {
    fun findByUsername(username: String): User
}