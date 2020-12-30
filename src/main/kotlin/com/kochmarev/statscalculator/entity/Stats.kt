package com.kochmarev.statscalculator.entity

import javax.persistence.*

@Entity
@Table(name = "stats")
class Stats {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private var id: Long? = null

    @Column(name = "strength")
    private var strength: Int? = null

    @Column(name = "perception")
    private var perception: Int? = null

    @Column(name = "endurance")
    private var endurance: Int? = null

    @Column(name = "charisma")
    private var charisma: Int? = null

    @Column(name = "intelligence")
    private var intelligence: Int? = null

    @Column(name = "agility")
    private var agility: Int? = null

    @Column(name = "luck")
    private var luck: Int? = null
}