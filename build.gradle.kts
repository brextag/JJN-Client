
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
    implementation("net.java.dev.jna:jna-platform:5.16.0")
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
    mainClass.set("com.jjn.launcher.MainApp")
}

val clientModProject = project(":client-mod")
val clientMod26Project = project(":client-mod-26")
val bundledModsDir = layout.buildDirectory.dir("generated-resources/mods")

val copyClientMod = tasks.register<Copy>("copyClientMod") {
    dependsOn(clientModProject.tasks.named("remapJar"))
    from(clientModProject.tasks.named("remapJar"))
    into(bundledModsDir.map { it.dir("com/jjn/launcher/mods") })
    rename { "jjn-client-1.21.4.jar" }
}

val copyClientMod26 = tasks.register<Copy>("copyClientMod26") {
    dependsOn(clientMod26Project.tasks.named("jar"))
    from(clientMod26Project.tasks.named("jar"))
    into(bundledModsDir.map { it.dir("com/jjn/launcher/mods") })
    rename { "jjn-client-26.2.jar" }
}

sourceSets.main {
    resources.srcDir(bundledModsDir)
}

tasks.processResources {
    dependsOn(copyClientMod, copyClientMod26)
}

tasks.named("build") {
    dependsOn(tasks.named("installDist"))
}

tasks.register("packageExe") {
    group = "distribution"
    description = "Packages JJN Client as a standalone Windows app image (JJN Client.exe) using jpackage."
    dependsOn(tasks.named("installDist"))

    doLast {
        val distLibDir = layout.buildDirectory.dir("install/JJNLauncher/lib").get().asFile
        val inputDir = layout.buildDirectory.dir("jpackage-input").get().asFile
        val modDir = layout.buildDirectory.dir("jpackage-mods").get().asFile
        val destDir = layout.buildDirectory.dir("exe").get().asFile
        val appImageDir = destDir.resolve("JJN Client")

        inputDir.deleteRecursively()
        modDir.deleteRecursively()
        appImageDir.deleteRecursively()
        inputDir.mkdirs()
        modDir.mkdirs()
        destDir.mkdirs()

        distLibDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jar") }?.forEach { jar ->
            val target = if (jar.name.startsWith("javafx-")) {
                modDir.resolve(jar.name)
            } else {
                inputDir.resolve(jar.name)
            }
            jar.copyTo(target, overwrite = true)
        }

        val launcherService = project.extensions.getByType(JavaToolchainService::class.java)
        val launcher = launcherService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get()
        val jpackageExe = launcher.metadata.installationPath.asFile.resolve("bin/jpackage.exe")
        val iconFile = project.file("packaging/jjn-client.ico")

        val cmd = mutableListOf(
            jpackageExe.absolutePath,
            "--type", "app-image",
            "--name", "JJN Client",
            "--app-version", project.version.toString(),
            "--vendor", "JJN",
            "--description", "JJN Client Minecraft Launcher",
            "--input", inputDir.absolutePath,
            "--module-path", modDir.absolutePath,
            "--add-modules", "java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.unsupported,jdk.zipfs,javafx.controls",
            "--main-jar", "JJNLauncher-${project.version}.jar",
            "--main-class", "com.jjn.launcher.MainApp",
            "--java-options", "--enable-native-access=javafx.graphics,ALL-UNNAMED",
            "--dest", destDir.absolutePath
        )
        if (iconFile.isFile) {
            cmd.addAll(listOf("--icon", iconFile.absolutePath))
        }

        val proc = ProcessBuilder(cmd)
            .directory(project.projectDir)
            .inheritIO()
            .start()
        val code = proc.waitFor()
        if (code != 0) {
            throw GradleException("jpackage failed with exit code $code")
        }
    }
}
