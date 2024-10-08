package org.opendc.simulator.compute.machine;

public class PerformanceCounters {
    private long cpuActiveTime = 0;
    private long cpuIdleTime = 0;
    private long cpuStealTime = 0;
    private long cpuLostTime = 0;

    private float cpuCapacity = 0.0f;
    private float cpuDemand = 0.0f;
    private float cpuSupply = 0.0f;

    public long getCpuActiveTime() {
        return cpuActiveTime;
    }

    public void setCpuActiveTime(long cpuActiveTime) {
        this.cpuActiveTime = cpuActiveTime;
    }

    public void addCpuActiveTime(long cpuActiveTime) {
        this.cpuActiveTime += cpuActiveTime;
    }

    public long getCpuIdleTime() {
        return cpuIdleTime;
    }

    public void setCpuIdleTime(long cpuIdleTime) {
        this.cpuIdleTime = cpuIdleTime;
    }

    public void addCpuIdleTime(long cpuIdleTime) {
        this.cpuIdleTime += cpuIdleTime;
    }

    public long getCpuStealTime() {
        return cpuStealTime;
    }

    public void setCpuStealTime(long cpuStealTime) {
        this.cpuStealTime = cpuStealTime;
    }

    public void addCpuStealTime(long cpuStealTime) {
        this.cpuStealTime += cpuStealTime;
    }

    public long getCpuLostTime() {
        return cpuLostTime;
    }

    public void setCpuLostTime(long cpuLostTime) {
        this.cpuLostTime = cpuLostTime;
    }

    public float getCpuCapacity() {
        return cpuCapacity;
    }

    public void setCpuCapacity(float cpuCapacity) {
        this.cpuCapacity = cpuCapacity;
    }

    public float getCpuDemand() {
        return cpuDemand;
    }

    public void setCpuDemand(float cpuDemand) {
        this.cpuDemand = cpuDemand;
    }

    public float getCpuSupply() {
        return cpuSupply;
    }

    public void setCpuSupply(float cpuSupply) {
        this.cpuSupply = cpuSupply;
    }
}
