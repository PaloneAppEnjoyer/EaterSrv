package app.palone

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.util.UUID


@Serializable
data class RegisterRequest(val email: String, val password: String)
@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTimestamp: Long,
    val userId: String
)

fun Application.configureAuth() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val authService = AuthService(dbConnection)
    val tokens = mutableMapOf<String,User>()
    install(Authentication) {
        bearer("auth-bearer") {
            authenticate { bearerTokenCredential->
                if (tokens.containsKey(bearerTokenCredential.token)) {
                    val user = tokens[bearerTokenCredential.token]
                    println("Found token: ${bearerTokenCredential.token}")
                    if (user != null) {
                        UserIdPrincipal(user.name)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }
    routing {
        authenticate("auth-bearer") {
            get("/auth") {
                call.respondText("Hello, ${call.principal<UserIdPrincipal>()?.name}!")
            }
        }
        post("/register") {
            val post = call.receive<RegisterRequest>()
            val name = post.email
            val password =
                post.password
            authService.createUser(name, password)
            call.respondText("User $name registered")
        }
        post("/login"){
            val post = call.receive<LoginRequest>()
            val name = post.email
            val password =
                post.password
            val userAuth: User? = authService.readUser(name)
            if (userAuth != null && userAuth.password == password) {
                val token = UUID.randomUUID().toString()
                tokens[token] = userAuth
                call.respond(LoginResponse(
                    accessToken = token,
                    refreshToken = "",
                    accessTokenExpirationTimestamp = System.currentTimeMillis() + 1000 * 60 * 60,
                    userId = userAuth.name
                ))
                println("Sent token: ${token}")
            } else {
                call.respondText("Invalid credentials", status = HttpStatusCode.Unauthorized)
            }
        }
    }
}