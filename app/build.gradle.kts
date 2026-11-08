import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// PDFium (o mesmo motor de PDF do Prumo para Windows), binários de github.com/bblanchon/pdfium-binaries.
// Versão fixa e conferida pelo SHA-256: o mesmo arquivo em qualquer computador e no GitHub Actions.
val pdfiumVersion = "8138"
val pdfiumAbis = mapOf(
    "arm64-v8a" to ("arm64" to "fc73c8020d7204e52f7a9d8153489b3cac2e9c5dc5d70d3a37a976a7398c1a86"),
    "armeabi-v7a" to ("arm" to "ee004a2ced1b321b7d9875f71ef9470692d00155b86262312b8c4c1b787c2124"),
    "x86_64" to ("x64" to "cf71ceb527d72a64b3e8bf36e9b15b326984f56d33bb8b74350a92ab8706ba87"),
)
val pdfiumDir = layout.buildDirectory.dir("pdfium/$pdfiumVersion").get().asFile

abstract class FetchPdfium : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val abis: MapProperty<String, String>        // ABI do Android -> nome no pacote
    @get:Input abstract val hashes: MapProperty<String, String>      // ABI do Android -> SHA-256
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        for ((abi, name) in abis.get()) {
            val target = File(out, abi)
            if (File(target, "lib/libpdfium.so").exists()) continue
            val tgz = File(temporaryDir, "pdfium-android-$name.tgz")
            val url = "https://github.com/bblanchon/pdfium-binaries/releases/download/chromium/${version.get()}/pdfium-android-$name.tgz"
            URI(url).toURL().openStream().use { input -> tgz.outputStream().use { input.copyTo(it) } }
            val sha = MessageDigest.getInstance("SHA-256").digest(tgz.readBytes()).joinToString("") { "%02x".format(it) }
            check(sha == hashes.get().getValue(abi)) { "PDFium $name: SHA-256 diferente do esperado ($sha)" }
            files.copy {
                from(archives.tarTree(archives.gzip(tgz)))
                into(target)
            }
        }
    }
}

val fetchPdfium = tasks.register<FetchPdfium>("fetchPdfium") {
    version.set(pdfiumVersion)
    abis.set(pdfiumAbis.mapValues { it.value.first })
    hashes.set(pdfiumAbis.mapValues { it.value.second })
    outputDir.set(pdfiumDir)
}

android {
    namespace = "br.com.prumopdf"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.prumopdf.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += pdfiumAbis.keys
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DPDFIUM_DIR=${pdfiumDir.absolutePath}")
            }
        }
    }

    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        // Chave de teste fixa (pública, só para instalar as versões de teste por cima umas das outras).
        // O Google Play usa outra chave, guardada nos segredos do GitHub (etapa 8).
        getByName("debug") {
            storeFile = rootProject.file("ci/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Os textos vêm dos catálogos em assets/locales; os values-xx só existem para o Android saber os idiomas.
        localeFilters += listOf("pt", "en", "es", "fr", "de", "ja", "zh-rCN", "zh-rTW", "nb", "sv", "da", "fi")
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.window)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
}

// O PDFium precisa estar baixado antes de o CMake configurar a parte nativa.
tasks.configureEach {
    if (name.contains("CMake") || name == "preBuild") dependsOn(fetchPdfium)
}

// Nomes de arquivo com acento nos testes: a JVM precisa de um idioma UTF-8 no sistema (como no Android).
tasks.withType<Test>().configureEach {
    environment("LC_ALL", "C.UTF-8")
    environment("LANG", "C.UTF-8")
}
