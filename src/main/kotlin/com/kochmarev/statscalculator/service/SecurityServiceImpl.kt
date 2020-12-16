package com.kochmarev.statscalculator.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service

@Service
class SecurityServiceImpl : SecurityService {

    @Autowired
    private var authenticationManager: AuthenticationManager? = null

    @Autowired
    private var userDetailsService: UserDetailsService? = null

    override fun findLoggedInUsername(): String? {
        val userDetails = SecurityContextHolder.getContext().authentication.details
        return if (userDetails is UserDetails) {
            userDetails.username
        } else null
    }

    override fun autoLogin(username: String, password: String) {
        val userDetails = userDetailsService!!.loadUserByUsername(username)
        val authenticationToken = UsernamePasswordAuthenticationToken(userDetails, password, userDetails.authorities)
        authenticationManager!!.authenticate(authenticationToken)
        if (authenticationToken.isAuthenticated) {
            SecurityContextHolder.getContext().authentication = authenticationToken
            logger.debug(String.format("Successfully %s auto logged in", username))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityServiceImpl::class.java)
    }
}