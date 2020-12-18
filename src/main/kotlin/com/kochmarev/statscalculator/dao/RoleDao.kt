package com.kochmarev.statscalculator.dao

import com.kochmarev.statscalculator.entity.Role
import org.springframework.data.jpa.repository.JpaRepository

interface RoleDao : JpaRepository<Role?, Long?> {
}