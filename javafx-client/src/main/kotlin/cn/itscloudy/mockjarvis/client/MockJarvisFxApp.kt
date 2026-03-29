package cn.itscloudy.mockjarvis.client

import javafx.application.Application
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class MockJarvisFxApp : Application() {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun start(stage: Stage) {
        val apiBaseUrlField = TextField(resolveDefaultBaseUrl()).apply {
            promptText = "Enter API base URL, for example http://localhost:8080"
        }
        val cityField = TextField().apply {
            promptText = "Enter a city, for example Hangzhou"
        }
        val queryButton = Button("Query Weather")
        val loadingIndicator = ProgressIndicator().apply {
            prefWidth = 24.0
            prefHeight = 24.0
            isVisible = false
            isManaged = false
        }
        val resultArea = TextArea().apply {
            isEditable = false
            isWrapText = true
            promptText = "The weather result will appear here."
        }
        val statusLabel = Label("Start the Spring Boot server first, then query the local API.")

        fun setLoadingState(loading: Boolean) {
            apiBaseUrlField.isDisable = loading
            cityField.isDisable = loading
            queryButton.isDisable = loading
            queryButton.text = if (loading) "Querying..." else "Query Weather"
            loadingIndicator.isVisible = loading
            loadingIndicator.isManaged = loading
            if (loading) {
                statusLabel.text = "Querying weather..."
            }
        }

        fun submitQuery() {
            val baseUrl = try {
                normalizeBaseUrl(apiBaseUrlField.text)
            } catch (error: IllegalArgumentException) {
                statusLabel.text = error.message ?: "Invalid service address."
                return
            }
            val city = cityField.text.trim()
            if (city.isBlank()) {
                statusLabel.text = "Please enter a city name."
                return
            }
            apiBaseUrlField.text = baseUrl

            val task = object : Task<String>() {
                override fun call(): String {
                    return queryWeather(baseUrl, city)
                }
            }

            task.setOnSucceeded {
                resultArea.text = task.value
                statusLabel.text = "Query completed."
                setLoadingState(false)
            }
            task.setOnFailed {
                val errorMessage = task.exception?.message ?: "Unknown error"
                resultArea.text = ""
                statusLabel.text = "Request failed: $errorMessage"
                setLoadingState(false)
            }

            setLoadingState(true)
            Thread(task, "mock-jarvis-weather-query").apply {
                isDaemon = true
                start()
            }
        }

        queryButton.setOnAction { submitQuery() }
        cityField.setOnAction { submitQuery() }

        val actionRow = HBox(10.0).apply {
            alignment = Pos.CENTER_LEFT
            children.addAll(queryButton, loadingIndicator)
        }

        val root = VBox(12.0).apply {
            padding = Insets(16.0)
            children.addAll(
                Label("API Base URL"),
                apiBaseUrlField,
                Label("City"),
                cityField,
                actionRow,
                Label("Result"),
                resultArea,
                statusLabel,
            )
        }
        VBox.setVgrow(resultArea, Priority.ALWAYS)

        stage.title = "Mock Jarvis JavaFX Client"
        stage.scene = Scene(root, 640.0, 420.0)
        stage.show()
    }

    private fun queryWeather(baseUrl: String, city: String): String {
        val encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/query_weather?city=$encodedCity"))
            .timeout(Duration.ofSeconds(90))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        if (response.statusCode() !in 200..299) {
            throw IOException("Server returned HTTP ${response.statusCode()}: ${response.body()}")
        }

        return response.body().ifBlank { "The server returned an empty response." }
    }

    private fun resolveDefaultBaseUrl(): String {
        val systemProperty = System.getProperty("mockjarvis.api.base-url")?.trim()
        if (!systemProperty.isNullOrEmpty()) {
            return systemProperty.trimEnd('/')
        }

        val envValue = System.getenv("MOCK_JARVIS_API_BASE_URL")?.trim()
        if (!envValue.isNullOrEmpty()) {
            return envValue.trimEnd('/')
        }

        return "http://localhost:8080"
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        require(normalizedBaseUrl.isNotEmpty()) {
            "Please enter a service address."
        }

        val uri = try {
            URI.create(normalizedBaseUrl)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Service address format is invalid.")
        }

        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
            "Service address must include protocol and host, for example http://localhost:8080"
        }

        return normalizedBaseUrl
    }
}

fun main() {
    Application.launch(MockJarvisFxApp::class.java)
}
