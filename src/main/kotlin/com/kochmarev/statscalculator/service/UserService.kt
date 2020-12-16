package com.kochmarev.statscalculator.service

import com.kochmarev.statscalculator.entity.User

interface UserService {

    fun save(user: User?)

    fun findByUsername(username: String?): User?

}