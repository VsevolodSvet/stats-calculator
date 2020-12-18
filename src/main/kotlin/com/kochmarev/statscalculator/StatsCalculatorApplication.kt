package com.kochmarev.statscalculator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.support.FileSystemXmlApplicationContext


@SpringBootApplication
class StatsCalculatorApplication

fun main(args: Array<String>) {
    runApplication<StatsCalculatorApplication>(*args) {

    }
}