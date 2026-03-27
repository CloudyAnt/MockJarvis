import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("java")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}
group = "cn.itscloudy"
version = "0.0.1-SNAPSHOT"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/central") }
}

var alibabaAiVersion = "1.1.2.2"
dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("com.alibaba.cloud.ai:spring-ai-alibaba-agent-framework:$alibabaAiVersion")
    implementation("com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope:$alibabaAiVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
}