android {
    namespace = "com.yutt"
}

dependencies {
    val cloudstreamApiVersion = "-SNAPSHOT"
    compileOnly("com.github.recloudstream.cloudstream:library:$cloudstreamApiVersion")
    compileOnly("com.github.Blatzar:NiceHttp:0.4.11")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
}
