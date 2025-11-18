package br.com.ibra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class AnaTerraPocApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AnaTerraPocApplication>(*args)
        }
    }
}