package store.authentication

class Authentication(
    private val users: MutableMap<String, User> = mutableMapOf(
        "default" to User("default")
    )
) {

    fun getUser(username: String): User? {
        return users[username]
    }
}
