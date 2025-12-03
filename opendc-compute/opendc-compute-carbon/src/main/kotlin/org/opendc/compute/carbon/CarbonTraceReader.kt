package org.opendc.compute.carbon

import org.opendc.compute.carbon.eba.getCarbonEBAFragments
import org.opendc.compute.carbon.opendc.getCarbonOpenDCFragments
import org.opendc.simulator.compute.power.carbon.CarbonFragment

public enum class CarbonTraceType {
    OpenDC,
    EBA,
}


public fun getCarbonFragments(pathToFile: String?, traceType: CarbonTraceType = CarbonTraceType.OpenDC): List<CarbonFragment>? {
    if (pathToFile == null) {
        return null;
    }

    return when(traceType) {
        CarbonTraceType.OpenDC -> getCarbonOpenDCFragments(pathToFile)
        CarbonTraceType.EBA -> getCarbonEBAFragments(pathToFile)
    }
}
