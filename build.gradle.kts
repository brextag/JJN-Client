
plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
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

javafx {
    version = "27.0.1"
    modules("javafx.controls")
}

application {
    mainClass.set("com.jjn.launcher.MicrosoftLoginTest")
}
