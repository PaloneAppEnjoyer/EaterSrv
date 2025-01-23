package app.palone

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class User(
    val id: Int,
    val name: String,
    val password: String
)


class AuthService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_USER =
            "CREATE TABLE USERS (ID SERIAL PRIMARY KEY, NAME VARCHAR(255), PASSWORD VARCHAR(255));"
        private const val SELECT_USER_BY_NAME = "SELECT * FROM users WHERE name = ?"
    }

    init {
        /*connection.createStatement().use {
            it.execute(CREATE_TABLE_USER)
        }*/
    }

    fun createUser(name: String, password: String) {
        val statement = connection.prepareStatement("INSERT INTO users (name, password) VALUES (?, ?)")
        statement.setString(1, name)
        statement.setString(2, password)
        statement.execute()
    }

    fun readUser(name: String): User? {
        val statement = connection.prepareStatement(SELECT_USER_BY_NAME)
        statement.setString(1, name)
        val resultSet = statement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                name = resultSet.getString("name"),
                password = resultSet.getString("password")
            )
        } else {
            null
        }
    }



}