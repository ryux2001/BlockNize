import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.desktop.currentOs)
    implementation(libs.json.java)
    testImplementation("junit:junit:4.13.2")
}

tasks.test { useJUnit() }

compose.desktop {
    application {
        mainClass = "com.blocknize.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            modules("java.net.http", "jdk.accessibility")
            packageName = "BlockNize"
            packageVersion = "0.4.0"
        }
    }
}
