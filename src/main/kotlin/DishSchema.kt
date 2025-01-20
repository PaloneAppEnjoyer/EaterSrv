package app.palone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.PreparedStatement

@Serializable
data class Dish(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val description: String,
)

@Serializable
data class Cuisine(
    val id: Int,
    val name: String
)

@Serializable
data class Ingredient(
    val id: Int,
    val name: String,
    val color: String
)

@Serializable
data class DishDto(
    val id: Int,
    val name: String,
    val cuisine: Cuisine,
    val ingredients: List<Ingredient>,
    val imageUrl: String,
    val description: String
)


class DishService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_DISH =
            "CREATE TABLE DISHES (ID SERIAL PRIMARY KEY, NAME VARCHAR(255), IMAGE_URL VARCHAR(255),DESCRIPTION VARCHAR(255));"
        private const val SELECT_DISH_BY_ID = "SELECT * FROM dishes WHERE id = ?"

        private const val CREATE_TABLE_CUISINE =
            "CREATE TABLE CUISINES (ID SERIAL PRIMARY KEY, NAME VARCHAR(255));"
        private const val SELECT_CUISINE_BY_ID = "SELECT * FROM cuisines WHERE id = ?"

        private const val CREATE_TABLE_INGREDIENT =
            "CREATE TABLE INGREDIENTS (ID SERIAL PRIMARY KEY, NAME VARCHAR(255), COLOR VARCHAR(255));"
        private const val SELECT_INGREDIENT_BY_ID = "SELECT * FROM ingredients WHERE id = ?"

        private const val CREATE_TABLE_INGREDIENT_TO_DISH =
            "CREATE TABLE INGREDIENTS_TO_DISH (ID SERIAL PRIMARY KEY, INGREDIENT_ID INT, DISH_ID INT);"
        private const val SELECT_INGREDIENT_ID_TO_DISH_ID =
            "SELECT ingredient_id FROM ingredients_to_dish WHERE dish_id = ?"

        private const val CREATE_TABLE_CUISINE_TO_DISH =
            "CREATE TABLE DISH_TO_CUISINE (ID SERIAL PRIMARY KEY, CUISINE_ID INT, DISH_ID INT);"
        private const val SELECT_DISH_ID_TO_CUISINE_ID = "SELECT cuisine_id FROM dish_to_cuisine WHERE dish_id = ?"
    }

    init {
        val statement = connection.createStatement()
    }

    suspend fun readDish(id: Int): Dish = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_DISH_BY_ID)
        statement.setInt(1, id)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val name = resultSet.getString("name")
            val imageUrl = resultSet.getString("image_url")
            val description = resultSet.getString("description")
            return@withContext Dish(id, name, imageUrl, description)
        } else {
            throw Exception("Record not found")
        }
    }

    suspend fun readCuisine(id: Int): Cuisine = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_CUISINE_BY_ID)
        statement.setInt(1, id)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val name = resultSet.getString("name")
            return@withContext Cuisine(id, name)
        } else {
            throw Exception("Record not found")
        }
    }

    suspend fun readCuisines(): List<Cuisine> = withContext(Dispatchers.IO) {
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery("SELECT * FROM cuisines")
        val cuisines = mutableListOf<Cuisine>()
        while (resultSet.next()) {
            val id = resultSet.getInt("id")
            val name = resultSet.getString("name")
            cuisines.add(Cuisine(id, name))
        }
        return@withContext cuisines
    }

    suspend fun readDishesByCuisine(cuisine: String): List<Dish> = withContext(Dispatchers.IO) {

        println("Cuisine: $cuisine")
        val statement = if (cuisine == "WSZYSTKIE") {
            connection.prepareStatement("SELECT * FROM dishes")
        } else {
            connection.prepareStatement("SELECT * FROM dishes WHERE id IN (SELECT dish_id FROM dish_to_cuisine WHERE cuisine_id = (SELECT id FROM cuisines WHERE name = ?))")
        }
        if (cuisine != "WSZYSTKIE") {
            statement?.setString(1, cuisine)
        }
        val resultSet = statement?.executeQuery()

        val dishes = mutableListOf<Dish>()
        while (resultSet!!.next()) {
            val id = resultSet.getInt("id")
            val name = resultSet.getString("name")
            val imageUrl = resultSet.getString("image_url")
            val description = resultSet.getString("description")
            dishes.add(Dish(id, name, imageUrl, description))
        }
        return@withContext dishes
    }

    suspend fun readIngredient(id: Int): Ingredient = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_INGREDIENT_BY_ID)
        statement.setInt(1, id)
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val name = resultSet.getString("name")
            val color = resultSet.getString("color")
            return@withContext Ingredient(id, name, color)
        } else {
            throw Exception("Record not found")
        }
    }

    suspend fun readDishDto(id: Int): DishDto = withContext(Dispatchers.IO) {
        val dish = readDish(id)
        val cuisineId = connection.prepareStatement(SELECT_DISH_ID_TO_CUISINE_ID)
        cuisineId.setInt(1, id)
        val cuisineIdResultSet = cuisineId.executeQuery()
        val cuisineIdList = mutableListOf<Int>()
        while (cuisineIdResultSet.next()) {
            cuisineIdList.add(cuisineIdResultSet.getInt("cuisine_id"))
        }
        val cuisine = readCuisine(cuisineIdList[0])

        val ingredientId = connection.prepareStatement(SELECT_INGREDIENT_ID_TO_DISH_ID)
        ingredientId.setInt(1, id)
        val ingredientIdResultSet = ingredientId.executeQuery()
        val ingredientIdList = mutableListOf<Int>()
        while (ingredientIdResultSet.next()) {
            ingredientIdList.add(ingredientIdResultSet.getInt("ingredient_id"))
        }
        val ingredients = ingredientIdList.map { readIngredient(it) }

        return@withContext DishDto(id, dish.name, cuisine, ingredients, dish.imageUrl, dish.description)
    }
}
