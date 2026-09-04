// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            // Build-tool dependencies only; do not add these to the watch runtime.
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("CVE-2021-33813: avoid vulnerable XML processing in JDOM 2.0.6")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("CVE-2024-29371: bound decompression of compressed JWE content")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.18.0") {
                because("CVE-2025-48924: avoid uncontrolled recursion in ClassUtils.getClass")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
