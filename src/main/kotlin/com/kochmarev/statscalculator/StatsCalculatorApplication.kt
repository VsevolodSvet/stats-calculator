package com.kochmarev.statscalculator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StatsCalculatorApplication

fun main(args: Array<String>) {
    runApplication<StatsCalculatorApplication>(*args) {

    }
}