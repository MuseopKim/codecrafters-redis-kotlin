package store.authentication

class Authentication(
    val users: MutableMap<String, User> = mutableMapOf(
        "default" to DEFAULT_USER
    )
) {

    companion object {

        val DEFAULT_USER = User(
            username = "default",
            flags = mutableListOf("nopass"),
            passwords = mutableListOf()
        )
    }

    fun getUser(username: String): User? {
        return users[username]
    }
}
