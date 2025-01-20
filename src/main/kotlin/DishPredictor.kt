package app.palone

class DishPredictor {
    fun recommendDishesBasedOnLikes(
        likedDishes: List<DishDto>,
        dislikedDishes: List<DishDto>,
        allDishes: List<DishDto>
    ): List<DishDto> {
        // Zbiór składników z polubionych i niepolubionych dań
        val likedIngredients = likedDishes.flatMap { it.ingredients }.groupingBy { it }.eachCount()
        val dislikedIngredients = dislikedDishes.flatMap { it.ingredients }.toSet()

        // Obliczanie wyniku dla każdego dania
        val dishScores = allDishes.associateWith { dish ->
            var score = 0
            for (ingredient in dish.ingredients.map { it.copy(id = 0, color = "0") }) {
                when {
                    ingredient in likedIngredients -> score += likedIngredients[ingredient] ?: 0
                    ingredient in dislikedIngredients -> score -= 1
                }
            }
            score
        }

        // Sortowanie dań według wyniku
        return dishScores.entries
            .filter { it.value > 0 } // Tylko dania z pozytywnym wynikiem
            .sortedByDescending { it.value }
            .map { it.key }.filter { dish->
                dislikedDishes.find { disliked-> disliked.name==dish.name }==null
            }
    }
}