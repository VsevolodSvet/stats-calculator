package com.kochmarev.statscalculator.entity

import javax.persistence.*

@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private var id: Long? = null

    @Column(name = "username")
    private var username: String? = null

    @Column(name = "password")
    private var password: String? = null

    @ManyToMany
    @JoinTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")],
            inverseJoinColumns = [JoinColumn(name = "role_id")])
    private var roles: Set<Role?>? = null

    @Transient
    private var confirmPassword: String? = null

    fun getUsername(): String? {
        return username
    }

    fun setUsername(username : String) {
        this.username = username
    }

    fun getPassword(): String? {
        return password
    }

    fun setPassword(password : String) {
        this.password = password
    }

    fun getRoles(): Set<Role?>? {
        return roles
    }

    fun setRoles(roles : Set<Role?>) {
        this.roles = roles
    }

    fun getConfirmPassword(): String? {
        return confirmPassword
    }

    fun setConfirmPassword(confirmPassword: String) {
        this.confirmPassword = confirmPassword
    }
}