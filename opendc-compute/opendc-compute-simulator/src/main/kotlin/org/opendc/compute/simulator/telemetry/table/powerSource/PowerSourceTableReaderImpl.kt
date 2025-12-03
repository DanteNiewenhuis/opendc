/*
 * Copyright (c) 2024 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.compute.simulator.telemetry.table.powerSource

import org.opendc.simulator.compute.power.SimPowerSource
import org.opendc.simulator.compute.power.carbon.CarbonFragments.CarbonEBAFragment
import java.time.Duration
import java.time.Instant

/**
 * An aggregator for task metrics before they are reported.
 */
public class PowerSourceTableReaderImpl(
    private val powerSource: SimPowerSource,
    private val startTime: Duration = Duration.ofMillis(0),
) : PowerSourceTableReader {
    override fun copy(): PowerSourceTableReader {
        val newPowerSourceTable =
            PowerSourceTableReaderImpl(
                powerSource,
            )
        newPowerSourceTable.setValues(this)

        return newPowerSourceTable
    }

    override fun setValues(table: PowerSourceTableReader) {
        _timestamp = table.timestamp
        _timestampAbsolute = table.timestampAbsolute

        _hostsConnected = table.hostsConnected
        _powerDraw = table.powerDraw
        _energyUsage = table.energyUsage
        _carbonIntensity = table.carbonIntensity
        _carbonEmission = table.carbonEmission

        _wndPower = table.wndPower
        _sunPower = table.sunPower
        _watPower = table.watPower
        _oilPower = table.oilPower
        _ngPower = table.ngPower
        _colPower = table.colPower
        _nucPower = table.nucPower
        _othPower = table.othPower

        _wndCoverage = table.wndCoverage
        _sunCoverage = table.sunCoverage
        _watCoverage = table.watCoverage
        _oilCoverage = table.oilCoverage
        _ngCoverage = table.ngCoverage
        _colCoverage = table.colCoverage
        _nucCoverage = table.nucCoverage
        _othCoverage = table.othCoverage
        _wndSunCoverage = table.wndSunCoverage
    }

    public override val powerSourceInfo: PowerSourceInfo =
        PowerSourceInfo(
            powerSource.name,
            powerSource.clusterName,
            "XXX",
            powerSource.capacity,
        )

    private var _timestamp = Instant.MIN
    override val timestamp: Instant
        get() = _timestamp

    private var _timestampAbsolute = Instant.MIN
    override val timestampAbsolute: Instant
        get() = _timestampAbsolute

    override val hostsConnected: Int
        get() = _hostsConnected
    private var _hostsConnected: Int = 0

    override val powerDraw: Double
        get() = _powerDraw
    private var _powerDraw = 0.0

    override val energyUsage: Double
        get() = _energyUsage - previousEnergyUsage
    private var _energyUsage = 0.0
    private var previousEnergyUsage = 0.0

    override val carbonIntensity: Double
        get() = _carbonIntensity
    private var _carbonIntensity = 0.0

    override val carbonEmission: Double
        get() = _carbonEmission - previousCarbonEmission
    private var _carbonEmission = 0.0
    private var previousCarbonEmission = 0.0

    override val wndPower: Long
        get() = _wndPower
    private var _wndPower = 0L

    override val sunPower: Long
        get() = _sunPower
    private var _sunPower = 0L

    override val watPower: Long
        get() = _watPower
    private var _watPower = 0L

    override val oilPower: Long
        get() = _oilPower
    private var _oilPower = 0L

    override val ngPower: Long
        get() = _ngPower
    private var _ngPower = 0L

    override val colPower: Long
        get() = _colPower
    private var _colPower = 0L

    override val nucPower: Long
        get() = _nucPower
    private var _nucPower = 0L

    override val othPower: Long
        get() = _othPower
    private var _othPower = 0L

    override val wndCoverage: Double
        get() = _wndCoverage - previousWndCoverage
    private var _wndCoverage = 0.0
    private var previousWndCoverage = 0.0

    override val sunCoverage: Double
        get() = _sunCoverage - previousSunCoverage
    private var _sunCoverage = 0.0
    private var previousSunCoverage = 0.0

    override val watCoverage: Double
        get() = _watCoverage - previousWatCoverage
    private var _watCoverage = 0.0
    private var previousWatCoverage = 0.0

    override val oilCoverage: Double
        get() = _oilCoverage - previousOilCoverage
    private var _oilCoverage = 0.0
    private var previousOilCoverage = 0.0

    override val ngCoverage: Double
        get() = _ngCoverage - previousNgCoverage
    private var _ngCoverage = 0.0
    private var previousNgCoverage = 0.0

    override val colCoverage: Double
        get() = _colCoverage - previousColCoverage
    private var _colCoverage = 0.0
    private var previousColCoverage = 0.0

    override val nucCoverage: Double
        get() = _nucCoverage - previousNucCoverage
    private var _nucCoverage = 0.0
    private var previousNucCoverage = 0.0

    override val othCoverage: Double
        get() = _othCoverage - previousOthCoverage
    private var _othCoverage = 0.0
    private var previousOthCoverage = 0.0

    override val wndSunCoverage: Double
        get() = _wndSunCoverage - previousWndSunCoverage
    private var _wndSunCoverage = 0.0
    private var previousWndSunCoverage = 0.0


    /**
     * Record the next cycle.
     */
    override fun record(now: Instant) {
        _timestamp = now
        _timestampAbsolute = now + startTime

        _hostsConnected = 0

        powerSource.updateCounters()
        _powerDraw = powerSource.powerDraw
        _energyUsage = powerSource.energyUsage
        _carbonIntensity = powerSource.carbonIntensity
        _carbonEmission = powerSource.carbonEmission

        val availablePower = powerSource.availablePowers

        _wndPower = availablePower[CarbonEBAFragment.PowerSourceType.WND.ordinal] ?: 0L
        _sunPower = availablePower[CarbonEBAFragment.PowerSourceType.SUN.ordinal] ?: 0L
        _watPower = availablePower[CarbonEBAFragment.PowerSourceType.WAT.ordinal] ?: 0L
        _oilPower = availablePower[CarbonEBAFragment.PowerSourceType.OIL.ordinal] ?: 0L
        _ngPower = availablePower[CarbonEBAFragment.PowerSourceType.NG.ordinal] ?: 0L
        _colPower = availablePower[CarbonEBAFragment.PowerSourceType.COL.ordinal] ?: 0L
        _nucPower = availablePower[CarbonEBAFragment.PowerSourceType.NUC.ordinal] ?: 0L
        _othPower = availablePower[CarbonEBAFragment.PowerSourceType.OTH.ordinal] ?: 0L

        _wndCoverage = powerSource.wndCoverage
        _sunCoverage = powerSource.sunCoverage
        _watCoverage = powerSource.watCoverage
        _oilCoverage = powerSource.oilCoverage
        _ngCoverage = powerSource.ngCoverage
        _colCoverage = powerSource.colCoverage
        _nucCoverage = powerSource.nucCoverage
        _othCoverage = powerSource.othCoverage

        _wndSunCoverage = powerSource.wndSunCoverage
    }

    /**
     * Finish the aggregation for this cycle.
     */
    override fun reset() {
        previousEnergyUsage = _energyUsage
        previousCarbonEmission = _carbonEmission

        _hostsConnected = 0
        _powerDraw = 0.0
        _energyUsage = 0.0
        _carbonIntensity = 0.0
        _carbonEmission = 0.0

        _wndPower = 0L
        _sunPower = 0L
        _watPower = 0L
        _oilPower = 0L
        _ngPower = 0L
        _colPower = 0L
        _nucPower = 0L
        _othPower = 0L

        _wndCoverage = 0.0
        _sunCoverage = 0.0
        _watCoverage = 0.0
        _oilCoverage = 0.0
        _ngCoverage = 0.0
        _colCoverage = 0.0
        _nucCoverage = 0.0
        _othCoverage = 0.0

        _wndSunCoverage = 0.0
    }
}
