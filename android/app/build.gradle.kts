plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}
val signingPath=System.getenv("APD_KEYSTORE")
android {
    namespace="com.jade.ai_photography_director"
    compileSdk=36
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    defaultConfig { applicationId="com.jade.ai_photography_director";minSdk=26;targetSdk=35;versionCode=flutter.versionCode;versionName=flutter.versionName }
    signingConfigs {
        create("privateRelease") {
            if(signingPath!=null) {
                storeFile=file(signingPath);storePassword=System.getenv("APD_STORE_PASSWORD")
                keyAlias=System.getenv("APD_KEY_ALIAS");keyPassword=System.getenv("APD_KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { signingConfig=signingConfigs.getByName("privateRelease");isMinifyEnabled=false;isShrinkResources=false } }
    androidResources { noCompress+="tflite" }
    testOptions { unitTests.isIncludeAndroidResources=true; unitTests.isReturnDefaultValues=true }
}
kotlin { compilerOptions { jvmTarget=org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
gradle.taskGraph.whenReady {
    if(allTasks.any { it.name.contains("Release",ignoreCase=true) } && signingPath==null) {
        throw GradleException("Release requires APD_KEYSTORE, APD_STORE_PASSWORD, APD_KEY_ALIAS and APD_KEY_PASSWORD")
    }
}
flutter { source="../.." }
dependencies {
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.heifwriter:heifwriter:1.1.0")
    implementation("androidx.camera:camera-video:1.6.2")
    implementation("androidx.camera.media3:media3-effect:1.0.0-alpha04")
    implementation("androidx.media3:media3-effect:1.9.0")
    implementation("androidx.camera:camera-extensions:1.6.2")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation("com.google.mlkit:face-detection:16.1.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
