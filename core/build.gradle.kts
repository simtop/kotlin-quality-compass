plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    commonTestImplementation(kotlin("test"))
}
