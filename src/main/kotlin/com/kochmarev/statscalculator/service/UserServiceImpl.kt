package com.kochmarev.statscalculator.service

import com.kochmarev.statscalculator.dao.RoleDao
import com.kochmarev.statscalculator.dao.UserDao
import com.kochmarev.statscalculator.entity.Role
import com.kochmarev.statscalculator.entity.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserServiceImpl : UserService {

    @Autowired
    private var userDao: UserDao? = null

    @Autowired
    private var roleDao: RoleDao? = null

    @Autowired
    private val bCryptPasswordEncoder: BCryptPasswordEncoder? = null

    override fun save(user: User?) {
        user?.setPassword(bCryptPasswordEncoder!!.encode(user.getPassword()))
        val roles: MutableSet<Role?> = HashSet()
        roles.add(roleDao!!.getOne(1L))
        user?.setRoles(roles)
        userDao!!.save(user!!)
    }

    override fun findByUsername(username: String?): User? {
        return userDao!!.findByUsername(username.toString())
    }
}