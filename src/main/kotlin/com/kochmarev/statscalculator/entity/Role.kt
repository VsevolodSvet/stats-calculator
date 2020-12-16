package com.kochmarev.statscalculator.entity

import javax.persistence.*

@Entity
@Table(name = "roles")
class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null

    @Column(name = "name")
    private var name: String? = null

    @ManyToMany(mappedBy = "roles")
    private var users: Set<User?>? = null

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getUsers(): Set<User?>? {
        return users
    }

    fun setUsers(users: Set<User?>?) {
        this.users = users
    }
}