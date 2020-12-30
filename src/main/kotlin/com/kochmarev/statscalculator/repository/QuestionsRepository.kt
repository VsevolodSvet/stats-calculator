package com.kochmarev.statscalculator.repository

import com.kochmarev.statscalculator.entity.Question
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

@Transactional
interface QuestionsRepository : CrudRepository<Question, Long> {
    fun findByCategory(category: Int): Question
    fun findByWeight(weight: Int): Question
}