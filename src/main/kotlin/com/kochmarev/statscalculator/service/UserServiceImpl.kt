package com.kochmarev.statscalculator.service

import com.kochmarev.statscalculator.repository.RoleRepository
import com.kochmarev.statscalculator.repository.UserRepository
import com.kochmarev.statscalculator.entity.Role
import com.kochmarev.statscalculator.entity.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserServiceImpl : UserService {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var bCryptPasswordEncoder: BCryptPasswordEncoder

    override fun save(user: User?) {
        user?.setPassword(bCryptPasswordEncoder.encode(user.getPassword()))
        val roles: MutableSet<Role?> = HashSet()
        roles.add(roleRepository.findById(1L).get())
        user?.setRoles(roles)
        userRepository.save(user!!)
    }

    override fun findByUsername(username: String?): User? {
        return userRepository.findByUsername(username.toString())
    }
}