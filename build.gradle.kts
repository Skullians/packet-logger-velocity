plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.7.3"
    id("com.gradleup.shadow") version "8.3.5"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}

group = "me.tech"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
//    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")

    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

bukkit {
    main = "me.tech.packetlogger.PacketLoggerPlugin"
    authors = listOf("DebitCardz").sorted()
    apiVersion = "1.20"

    depend = listOf("ProtocolLib")
}