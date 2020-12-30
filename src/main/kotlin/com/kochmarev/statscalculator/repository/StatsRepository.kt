package com.kochmarev.statscalculator.repository

import com.kochmarev.statscalculator.entity.Stats
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

@Transactional
interface StatsRepository : CrudRepository<Stats?, Long?> {
}