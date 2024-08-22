package org.opendc.compute.workload.greenifier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.compute.workload.VirtualMachine
import java.io.File

public class GreenifierWorkloadLoader(private val baseDir: File) {

    private fun readWorkloadFile(): GreenifierWorkloadSpec {
        return Json.decodeFromStream<GreenifierWorkloadSpec>(baseDir.resolve("workload.json").inputStream())
    }

    public fun loadWorkload():  List<VirtualMachine> {
        val workloadSpec = readWorkloadFile()

        println(workloadSpec)

        return listOf();
    }
}
