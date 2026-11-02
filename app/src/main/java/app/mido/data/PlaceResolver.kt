package app.mido.data

data class ResolvedPlace(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
)

/** Turns "Planet Fitness" into coordinates. */
interface PlaceResolver {
    suspend fun resolve(query: String): ResolvedPlace?

    object None : PlaceResolver {
        override suspend fun resolve(query: String): ResolvedPlace? = null
    }
}
