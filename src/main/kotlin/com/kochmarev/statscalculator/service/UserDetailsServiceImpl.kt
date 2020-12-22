package com.kochmarev.statscalculator.service

import com.kochmarev.statscalculator.repository.UserRepository
import com.kochmarev.statscalculator.entity.User
import com.kochmarev.statscalculator.entity.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.jvm.Throws

class UserDetailsServiceImpl : UserDetailsService {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Transactional(readOnly = true)
    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): UserDetails {
        val user: User = userRepository.findByUsername(username)
        val grantedAuthorities: MutableSet<GrantedAuthority> = HashSet()
        for (role: Role? in user.getRoles()!!) {
            grantedAuthorities.add(SimpleGrantedAuthority(role!!.getName()))
        }
        return org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), grantedAuthorities)
    }

}