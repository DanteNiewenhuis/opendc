description = "OpenDC shared simulation data model"

plugins {
    `kotlin-library-conventions`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.jackson.annotations)
    api(projects.opendc.opendcCommon)
    api(projects.opendcSimulator.opendcSimulatorCompute)
    api(projects.opendcCompute.opendcComputeSimulator)
    implementation(projects.opendcTrace.opendcTraceApi)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.module.kotlin)
}
