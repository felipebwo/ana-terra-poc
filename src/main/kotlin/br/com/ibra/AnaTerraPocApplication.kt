package br.com.ibra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
open class AnaTerraPocApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AnaTerraPocApplication>(*args)
        }
    }
}