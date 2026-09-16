extension {
    // Path of the compiled extension inside the patch bundle.
    // Referenced by googleMapsMicroGPatch via extendWith("extensions/maps.mpe").
    name = "extensions/maps.mpe"
}

android {
    namespace = "app.morphe.extension"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Provides Utils, Logger, StringRef, ResourceUtils, CustomDialog and the
    // settings base classes (BaseSettings/BooleanSetting/...) the extension builds on.
    implementation(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
}
