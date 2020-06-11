import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        val kotlinVersion = "1.3.71"
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    kotlin("jvm") version "1.3.71"
}

group = "name.anton3.layout-optimizer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()

    maven {
        url = URI("https://dl.bintray.com/kyonifer/maven")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("com.kyonifer:koma-core-ejml:0.12")

    val coroutinesVersion = "1.3.5"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
