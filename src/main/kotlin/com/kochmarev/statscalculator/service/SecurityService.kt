package com.kochmarev.statscalculator.service

interface SecurityService {

    fun findLoggedInUsername(): String?

    fun autoLogin(username: String, password: String)

}