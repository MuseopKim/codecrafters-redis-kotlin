package store.authentication

class Authentication(
    val users: MutableMap<String, User> = mutableMapOf(
        "default" to DEFAULT_USER
    )
) {

    companion object {

        val DEFAULT_USER = User("default", mutableListOf("nopass"))
    }

    fun getUser(username: String): User? {
        return users[username]
    }
}
