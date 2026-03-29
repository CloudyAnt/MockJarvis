package cn.itscloudy.mockjarvis.service

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel
import com.alibaba.cloud.ai.graph.agent.ReactAgent
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.function.BiFunction

@Service
class ChatService(
    private val qWeatherService: QWeatherService,
) {

    private val weatherForecasterSystemPrompt: String = """
You are an expert weather forecaster, who speaks in puns.

You have access to these tools:

- get_city_current_weather: use this to get the weather for a specific city

If a user asks you for the weather, make sure you know the location(city).
Besides the weather, you can also provide some suggestions on what to wear, whether to carry an umbrella, 
whether it's suitable for outdoor exercise, etc.
"""

    @Value("\${spring.ai.dashscope.api-key}")
    lateinit var dashScopeApiKey: String

    fun queryWeather(city: String): String {
        val dashScopeApi = DashScopeApi.builder()
            .apiKey(dashScopeApiKey)
            .build()

        val chatModel: ChatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .build()


        class WeatherTool : BiFunction<String?, ToolContext?, String?> {
            override fun apply(city: String?, toolContext: ToolContext?): String {
                if (city.isNullOrBlank()) {
                    return "Cannot get current weather because the city is blank."
                }
                return qWeatherService.getCurrentWeather(city)
            }
        }

        val weatherTool: ToolCallback = FunctionToolCallback.builder("get_weather", WeatherTool())
            .description("Get weather for a given city")
            .inputType(String::class.java)
            .build()


        val agent = ReactAgent.builder()
            .name("weather_agent")
            .model(chatModel)
            .tools(weatherTool)
            .systemPrompt(weatherForecasterSystemPrompt)
            .saver(MemorySaver())
            .build()


        val response = agent.call("what is the weather in $city")
        return response.text ?: "No response"
    }

}