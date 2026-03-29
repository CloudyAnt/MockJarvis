package cn.itscloudy.mockjarvis.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class QWeatherService(
    private val webClientBuilder: WebClient.Builder,
    @param:Value($$"${weather.qweather.api-host:}") private val apiHost: String,
    @param:Value($$"${weather.qweather.project-id:}") private val projectId: String,
    @param:Value($$"${weather.qweather.credential-id:}") private val credentialId: String,
    @param:Value($$"${weather.qweather.ed25519-private-path:}") private val ed25519PrivatePath: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val signingKey: PrivateKey by lazy { loadPrivateKey(ed25519PrivatePath) }

    fun getCurrentWeather(city: String): String {
        val normalizedCity = city.trim()
        if (normalizedCity.isBlank()) {
            return "Cannot get current weather because the city is blank."
        }
        if (!isConfigured()) {
            logger.warn("QWeather is not fully configured, weather lookup skipped.")
            return "Cannot get current weather because QWeather is not configured."
        }

        val cityQuery = parseCityQuery(normalizedCity)
        val location = getCityLocation(cityQuery)
            ?: return "Cannot get current weather due to failed to get the city location."
        val weather = getWeatherNow(location.id)
            ?: return "Cannot get current weather due to api call failed."

        return "It's ${weather.text} in ${location.name}. Current temperature is ${weather.temp}, " +
            "feels like ${weather.feelsLike}. Humidity is ${weather.humidity}. " +
            "Wind direction is ${weather.windDir} and scale is ${weather.windScale}."
    }

    private fun isConfigured(): Boolean {
        return apiHost.isNotBlank() &&
            projectId.isNotBlank() &&
            credentialId.isNotBlank() &&
            ed25519PrivatePath.isNotBlank()
    }

    private fun parseCityQuery(city: String): CityQuery {
        val parts = city.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.size <= 1) {
            CityQuery(location = city, superiorAdm = null)
        } else {
            CityQuery(location = parts.first(), superiorAdm = parts.drop(1).joinToString(", "))
        }
    }

    private fun getCityLocation(cityQuery: CityQuery): CityLocation? {
        val uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl())
            .path(CITY_LOOKUP_PATH)
            .queryParam("location", cityQuery.location)
            .queryParam("lang", "en")
        cityQuery.superiorAdm?.let { uriBuilder.queryParam("adm", it) }

        val response = executeGet(
            uriBuilder.build(true).toUriString(),
            CityLookupResponse::class.java,
        ) ?: return null

        if (response.code != SUCCESS_CODE || response.location.isEmpty()) {
            logger.warn("Failed to resolve city location for {}: code={}", cityQuery.location, response.code)
            return null
        }

        return response.location.first()
    }

    private fun getWeatherNow(locationId: String): CurrentWeather? {
        val response = executeGet(
            UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path(CURRENT_WEATHER_PATH)
                .queryParam("location", locationId)
                .queryParam("lang", "en")
                .build(true)
                .toUriString(),
            CurrentWeatherResponse::class.java,
        ) ?: return null

        if (response.code != SUCCESS_CODE) {
            logger.warn("Failed to fetch current weather for location {}: code={}", locationId, response.code)
            return null
        }

        return response.now
    }

    private fun <T> executeGet(uri: String, responseType: Class<T>): T? {
        return try {
            webClientBuilder.build()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${buildJwt()}")
                .retrieve()
                .bodyToMono(responseType)
                .block()
        } catch (error: WebClientResponseException) {
            logger.warn("QWeather request failed: status={}, body={}", error.statusCode, error.responseBodyAsString)
            null
        } catch (error: Exception) {
            logger.warn("QWeather request failed for {}", uri, error)
            null
        }
    }

    private fun buildJwt(): String {
        val now = Instant.now()
        val header = objectMapper.writeValueAsBytes(
            mapOf(
                "alg" to "EdDSA",
                "kid" to credentialId,
            )
        )
        val payload = objectMapper.writeValueAsBytes(
            mapOf(
                "sub" to projectId,
                "iat" to now.epochSecond,
                "exp" to now.plus(1, ChronoUnit.HOURS).epochSecond,
            )
        )

        val encodedHeader = encodeBase64Url(header)
        val encodedPayload = encodeBase64Url(payload)
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(signingKey)
        signature.update(signingInput.toByteArray(StandardCharsets.UTF_8))

        return "$signingInput.${encodeBase64Url(signature.sign())}"
    }

    private fun loadPrivateKey(privateKeyPath: String): PrivateKey {
        val pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8)
        val normalizedPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(normalizedPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("Ed25519").generatePrivate(keySpec)
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun baseUrl(): String {
        val trimmedHost = apiHost.trim().trimEnd('/')
        return if (trimmedHost.startsWith("http://") || trimmedHost.startsWith("https://")) {
            trimmedHost
        } else {
            "https://$trimmedHost"
        }
    }

    private data class CityQuery(
        val location: String,
        val superiorAdm: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CityLookupResponse(
        val code: String = "",
        val location: List<CityLocation> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CurrentWeatherResponse(
        val code: String = "",
        val now: CurrentWeather = CurrentWeather(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CityLocation(
        val id: String = "",
        val name: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CurrentWeather(
        val text: String = "",
        val temp: String = "",
        val feelsLike: String = "",
        val humidity: String = "",
        val windDir: String = "",
        val windScale: String = "",
    )

    companion object {
        private const val CITY_LOOKUP_PATH = "/geo/v2/city/lookup"
        private const val CURRENT_WEATHER_PATH = "/v7/weather/now"
        private const val SUCCESS_CODE = "200"
    }
}
