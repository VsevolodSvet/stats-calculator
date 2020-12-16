package com.kochmarev.statscalculator.entity

import javax.persistence.*

@Entity
@Table(name = "roles")
data class Role (
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long,

    @Column(name = "name")
    val name: String,

    @ManyToMany(mappedBy = "roles")
    val users: Set<User>
)