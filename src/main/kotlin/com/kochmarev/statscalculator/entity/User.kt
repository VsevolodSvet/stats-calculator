package com.kochmarev.statscalculator.entity

import javax.persistence.*

@Entity
@Table(name = "users")
data class User (
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long,

    @Column(name = "username")
    val username: String,

    @Column(name = "password")
    val password: String,

    @ManyToMany
    @JoinTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")],
            inverseJoinColumns = [JoinColumn(name = "role_id")])
    val roles: Set<Role>
)