architectury {
    val enabledPlatforms: String by rootProject
    common(enabledPlatforms.split(","))
}

dependencies {
    modCompileOnly("tech.thatgravyboat:commonats:2.0")
}
