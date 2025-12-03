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

package org.opendc.simulator.compute.power.carbon.CarbonFragments;

import org.opendc.simulator.compute.power.carbon.CarbonFragment;
import org.opendc.simulator.compute.power.carbon.CarbonModel;

import java.util.HashMap;
import java.util.Map;

/**
 * An object holding the carbon intensity during a specific time frame.
 * Used by {@link CarbonModel}.
 */
public class CarbonEBAFragment implements CarbonFragment {
    private long startTime;
    private long endTime;
    private double carbonIntensity;

    public enum PowerSourceType {
        WND, // wind
        SUN, // solar
        WAT, // water
        OIL,
        NG, // natural gas
        COL, // coal
        NUC, // nuclear
        OTH // other sources
    }

    private final Map<Integer, Integer> powerSourceIntensities = Map.of(
        PowerSourceType.WND.ordinal(), 11,
        PowerSourceType.SUN.ordinal(), 41,
        PowerSourceType.WAT.ordinal(), 24,
        PowerSourceType.OIL.ordinal(), 650,
        PowerSourceType.NG.ordinal(), 490,
        PowerSourceType.COL.ordinal(), 820,
        PowerSourceType.NUC.ordinal(), 12,
        PowerSourceType.OTH.ordinal(), 230
    );

    private final HashMap<Integer, Long> availablePowers = new HashMap<>(
        Map.of(
            PowerSourceType.WND.ordinal(), 0L,
            PowerSourceType.SUN.ordinal(), 0L,
            PowerSourceType.WAT.ordinal(), 0L,
            PowerSourceType.OIL.ordinal(), 0L,
            PowerSourceType.NG.ordinal(),  0L,
            PowerSourceType.COL.ordinal(), 0L,
            PowerSourceType.NUC.ordinal(), 0L,
            PowerSourceType.OTH.ordinal(), 0L
        )
    );

    public CarbonEBAFragment(long startTime, long endTime, double carbonIntensity,
                             long windPower, long solarPower, long waterPower,
                             long oilPower, long naturalGasPower, long coalPower,
                             long nuclearPower, long otherPower) {
        this.setStartTime(startTime);
        this.setEndTime(endTime);
        this.setCarbonIntensity(carbonIntensity);

        this.setAvailablePower(PowerSourceType.WND, windPower);
        this.setAvailablePower(PowerSourceType.SUN, solarPower);
        this.setAvailablePower(PowerSourceType.WAT, waterPower);
        this.setAvailablePower(PowerSourceType.OIL, oilPower);
        this.setAvailablePower(PowerSourceType.NG, naturalGasPower);
        this.setAvailablePower(PowerSourceType.COL, coalPower);
        this.setAvailablePower(PowerSourceType.NUC, nuclearPower);
        this.setAvailablePower(PowerSourceType.OTH, otherPower);
    }

    public void setAvailablePower(PowerSourceType powerSource, long power) {
        availablePowers.put(powerSource.ordinal(), power);
    }

    public long getAvailablePower(PowerSourceType powerSource) {
        return availablePowers.get(powerSource.ordinal());
    }

    public Map<Integer, Long> getAvailablePowers() {
        return availablePowers;
    }

    public double getCarbonIntensity() {
        return carbonIntensity;
    }

    public void setCarbonIntensity(double carbonIntensity) {
        this.carbonIntensity = carbonIntensity;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
}
