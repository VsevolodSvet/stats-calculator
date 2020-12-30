package com.kochmarev.statscalculator.repository

import com.kochmarev.statscalculator.entity.User
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

@Transactional
interface UserRepository : CrudRepository<User, Long> {
    fun findByUsername(username: String): User
    fun findByEmail(email: String): User
}