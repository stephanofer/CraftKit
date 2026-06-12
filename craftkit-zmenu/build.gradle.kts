description = "zMenu integration helpers for CraftKit consumers."

repositories {
    maven {
        name = "groupez"
        url = uri("https://repo.groupez.dev/releases")
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.zmenu.api)
}
