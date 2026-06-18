description = "Localized Paper/Adventure feedback helpers for CraftKit consumers."

repositories {
    mavenLocal()
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.boosted.yaml)
    compileOnly(libs.networkplayersettings)

    testImplementation(libs.paper.api)
    testImplementation(libs.boosted.yaml)
}
