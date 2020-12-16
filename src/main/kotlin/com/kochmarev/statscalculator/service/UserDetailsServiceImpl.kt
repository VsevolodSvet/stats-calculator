package com.kochmarev.statscalculator.service

import com.kochmarev.statscalculator.dao.UserDao
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
    private lateinit var userDao: UserDao

    @Transactional(readOnly = true)
    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): UserDetails {
        val user: User = userDao.findByUsername(username)
        val grantedAuthorities: MutableSet<GrantedAuthority> = HashSet()
        for (role: Role in user.roles) {
            grantedAuthorities.add(SimpleGrantedAuthority(role.name))
        }
        return org.springframework.security.core.userdetails.User(user.username, user.password, grantedAuthorities)
    }
}