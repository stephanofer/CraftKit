description = "Database infrastructure defaults for HERA plugins."

dependencies {
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)
    implementation(libs.hikari)
    runtimeOnly(libs.mysql.connector)
}
