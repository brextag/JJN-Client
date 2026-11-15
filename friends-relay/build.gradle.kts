plugins {
    application
}

group = "com.jjn"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

application {
    mainClass.set("com.jjn.relay.JjnRelayServer")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.jjn.relay.JjnRelayServer"
    }
}
