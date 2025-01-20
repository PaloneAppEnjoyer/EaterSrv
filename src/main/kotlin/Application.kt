package app.palone

import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)

}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureFrameworks()
    configureRouting()
}
