import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firma de release (PLAN.md Parte 40, grupo 1): android/keystore.properties
// (gitignoreado) o, si no existe, variables de entorno PDV_KEYSTORE_* (CI).
// Sin ninguno de los dos, debug compila igual y el empaquetado de release
// falla con un mensaje claro (ver tasks al final del archivo).
val releaseSigning: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        mapOf(
            "storeFile" to rootProject.file(props.getProperty("storeFile")).path,
            "storePassword" to props.getProperty("storePassword"),
            "keyAlias" to props.getProperty("keyAlias"),
            "keyPassword" to props.getProperty("keyPassword"),
        )
    } else {
        System.getenv("PDV_KEYSTORE_FILE")?.let { storeFile ->
            mapOf(
                "storeFile" to storeFile,
                "storePassword" to System.getenv("PDV_KEYSTORE_PASSWORD"),
                "keyAlias" to System.getenv("PDV_KEY_ALIAS"),
                "keyPassword" to System.getenv("PDV_KEY_PASSWORD"),
            )
        }
    }
}

android {
    namespace = "com.pdv.pos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pdv.pos"
        minSdk = 26
        targetSdk = 36
        // Esquema de versionado en CLAUDE.md seccion 7: versionName SemVer
        // MAJOR.MINOR.PATCH; versionCode = MAJOR*10000 + MINOR*100 + PATCH.
        // Ambos se suben a mano en el commit que prepara cada release.
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // MigrationTestHelper lee los JSON de esquema exportados como assets
    // del APK de pruebas instrumentadas (M-7, PLAN.md Parte 39).
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gatea el HttpLoggingInterceptor de red (M-8,
        // NetworkModule). Desde AGP 8 la generacion de BuildConfig es opt-in.
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    lint {
        // local.properties es generado por Android Studio con rutas Windows sin
        // escapar y no esta versionado (.gitignore); PropertyEscape lo marca
        // como error en todo entorno Windows sin que el proyecto pueda arreglarlo.
        disable += "PropertyEscape"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Convencion de nombres de builds (CLAUDE.md sec. 3): {app}-{proposito}-{tipo-build}-v{version}.
// Se actualiza el nombre en cada Parte que produzca un nuevo build de prueba.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("pos-hello-debug-v0.1.apk")
            }
        }
    }
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set(output.versionName.map { "pos-pdv-release-v$it.apk" })
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.bcrypt)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}

if (releaseSigning == null) {
    tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
        doFirst {
            throw GradleException(
                "Falta la firma de release: crea android/keystore.properties " +
                    "(ver CLAUDE.md seccion 7) o define PDV_KEYSTORE_FILE, " +
                    "PDV_KEYSTORE_PASSWORD, PDV_KEY_ALIAS y PDV_KEY_PASSWORD.",
            )
        }
    }
}
