package cn.itscloudy.mockjarvis

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class MockJarvisApp

fun main(args: Array<String>) {
    SpringApplication.run(MockJarvisApp::class.java, *args)
}