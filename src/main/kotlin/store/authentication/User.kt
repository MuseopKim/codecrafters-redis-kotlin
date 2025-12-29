package store.authentication

data class User(
    val username: String,
    val flags: MutableList<String> = mutableListOf()
)
