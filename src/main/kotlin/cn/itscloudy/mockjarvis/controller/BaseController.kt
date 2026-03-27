package cn.itscloudy.mockjarvis.controller

import cn.itscloudy.mockjarvis.service.ChatService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/v1")
class BaseController(val chatService: ChatService) {

    @GetMapping("/query_weather")
    fun queryWeather(@RequestParam(name = "city") city: String): String {
        return chatService.queryWeather(city)
    }

}