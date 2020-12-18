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
    private lateinit var userDao: UserDao

    @Autowired
    private lateinit var roleDao: RoleDao

    @Autowired
    private lateinit var bCryptPasswordEncoder: BCryptPasswordEncoder

    override fun save(user: User?) {
        user?.setPassword(bCryptPasswordEncoder.encode(user.getPassword()))
        val roles: MutableSet<Role?> = HashSet()
        roles.add(roleDao.getOne(1L))
        user?.setRoles(roles)
        userDao.save(user!!)
    }

    override fun findByUsername(username: String?): User? {
        return userDao.findByUsername(username.toString())
    }
}