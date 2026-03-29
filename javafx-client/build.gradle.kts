plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "cn.itscloudy"
version = "0.0.1-SNAPSHOT"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/central") }
}

kotlin {
    jvmToolchain(17)
}

javafx {
    version = "17.0.14"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("cn.itscloudy.mockjarvis.client.MockJarvisFxAppKt")
}
