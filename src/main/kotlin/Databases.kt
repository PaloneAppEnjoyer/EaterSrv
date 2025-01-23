package app.palone

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.*
import java.awt.desktop.UserSessionEvent

@Serializable
data class UserPreferences(
    var allDishes: List<DishDto> = emptyList(),
    val likedDishes: MutableList<DishDto> = mutableListOf(),
    val dislikedDishes: MutableList<DishDto> = mutableListOf(),
    val skippedDishes: MutableList<DishDto> = mutableListOf()
)

fun Application.configureDatabases() {
    val dbConnection: Connection = connectToPostgres(embedded = false)
    //val cityService = CityService(dbConnection)
    val dishService = DishService(dbConnection)
    val userPreferencesToName = mutableMapOf<String, UserPreferences>()

    val predictor by inject<DishPredictor>()

    routing {
        authenticate("auth-bearer") {
            get("/cuisine") {
                val cuisines: List<Cuisine> = dishService.readCuisines()
                call.respond(HttpStatusCode.OK, cuisines)
            }
            post("/startSession") {
                if(userPreferencesToName[call.principal<UserIdPrincipal>()?.name] == null){
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name!!] = UserPreferences()}

                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes?.clear()
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes?.clear()
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes?.clear()
                var cuisine: Cuisine? = null
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FormItem && part.name == "CUISINE") {
                        cuisine = Json.decodeFromString(part.value)
                    }
                    part.dispose()
                }
                val dishes: List<Dish> = dishService.readDishesByCuisine(cuisine?.name?.uppercase() ?: "all")
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.allDishes = dishes.map {
                    dishService.readDishDto(it.id)
                }
                call.respond(HttpStatusCode.OK, dishes.map { dishService.readDishDto(it.id) }.shuffled().takeLast(10))
            }

            post("/continueSession") {
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part !is PartData.FormItem) return@forEachPart

                    if (part.name == "LIKED") {
                        userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes!! += Json.decodeFromString<List<DishDto>>(
                            part.value
                        )
                    }
                    if (part.name == "DISLIKED") {
                        userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes!! += Json.decodeFromString<List<DishDto>>(
                            part.value
                        )
                    }
                    if (part.name == "SKIPPED") {
                        userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes!! += Json.decodeFromString<List<DishDto>>(
                            part.value
                        )
                    }
                    part.dispose()
                }
                println(
                    "Dishes swiped: ${
                        userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes!!.size +
                                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes!!.size +
                                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes!!.size
                    }"
                )
                val dishes: List<DishDto> =
                    userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.allDishes!!.filter {
                        !userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes!!.any { liked -> liked.id == it.id } &&
                                !userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes!!.any { disliked -> disliked.id == it.id } &&
                                !userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes!!.any { skipped -> skipped.id == it.id }
                    }.shuffled().takeLast(10)


                if ((userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes!!.size +
                            userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes!!.size +
                            userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes!!.size >= 30) || dishes.size <= 5
                ) {
                    call.respond(
                        HttpStatusCode.OK,
                        listOf(userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.allDishes!![0])
                    )
                } else
                    call.respond(HttpStatusCode.OK, dishes)
            }

            get("/sessionResults") {
                val recommendedDishes = predictor.recommendDishesBasedOnLikes(
                    userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes!!,
                    userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes!!,
                    userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.allDishes!!
                )
                call.respond(HttpStatusCode.OK, recommendedDishes.take(3))
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.likedDishes?.clear()
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.dislikedDishes?.clear()
                userPreferencesToName[call.principal<UserIdPrincipal>()?.name]?.skippedDishes?.clear()
            }
        }
    }
}

/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */
fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        log.info("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}
