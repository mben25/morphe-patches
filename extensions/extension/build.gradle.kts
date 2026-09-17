extension {
    // Path of the compiled extension inside the patch bundle.
    // Referenced by exportImportSettingsPatch via extendWith("extensions/extension.mpe").
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.mtcapsule.extension"
    compileSdk = 36

    defaultConfig {
        // Matches COMPATIBILITY_MT_CAPSULE.targets minSdk in Constants.kt.
        minSdk = 32
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pulls in androidx.datastore.preferences.PreferencesProto (PreferenceMap/Value/StringSet),
    // the *shaded* protobuf-lite classes DataStore's Preferences implementation uses to
    // serialize its backing file. Using the real generated message classes instead of
    // hand-rolling a parser guarantees byte-for-byte wire compatibility with whatever the
    // app itself writes/reads - the plain proto3 map<string, Value> schema has been stable
    // since Preferences DataStore's 1.0.0 release, so pinning to that older, non-KMP
    // artifact keeps things to a single small dependency instead of the 1.1.x rewrite
    // (Okio + kotlinx-serialization-protobuf + coroutine core all needed just to read a file).
    // parseFrom()/toByteArray() on the generated classes are plain Java, so none of the
    // Kotlin suspend-fun (Continuation) machinery of the higher-level DataStore<Preferences>
    // API has to be reproduced here at all.
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")
}
