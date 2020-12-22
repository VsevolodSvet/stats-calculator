package com.kochmarev.statscalculator.repository

import com.kochmarev.statscalculator.entity.Role
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

@Transactional
interface RoleRepository : CrudRepository<Role?, Long?> {
}