package com.kochmarev.statscalculator.dao

import com.kochmarev.statscalculator.entity.Role
import com.kochmarev.statscalculator.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface RoleDao : JpaRepository<Role?, Long?> {
}