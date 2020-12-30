package com.kochmarev.statscalculator.entity

import net.minidev.json.JSONObject
import javax.persistence.*

@Entity
@Table(name = "questions")
class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private var id: Long? = null

    @Column(name = "category")
    private var category: String? = null

    @Column(name = "weight")
    private var weight: String? = null

    @Column(name = "question")
    private var question: JSONObject? = null

    @Column(name = "answer")
    private var answer: JSONObject? = null

    @Column(name = "media_link")
    private var mediaLink: String? = null
}