/*
 * Copyright (c) 2021 AtLarge Research
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

package org.opendc.compute.workload

import java.io.File
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

public enum class WorkloadLoaderTypeEnum {
    ComputeWorkload,
    Greenifier
}

public fun getWorkloadLoader(type: WorkloadLoaderTypeEnum): ComputeWorkloadLoaderNew {
    return when(type) {
        WorkloadLoaderTypeEnum.ComputeWorkload -> FragmentWorkloadLoader()
        WorkloadLoaderTypeEnum.Greenifier -> GreenifierWorkloadLoader()
    }
}


/**
 * A helper class for loading compute workload traces into memory.
 *
 * @param baseDir The directory containing the traces.
 */
public abstract class ComputeWorkloadLoaderNew {
    /**
     * The cache of workloads.
     */
    private val cache = ConcurrentHashMap<String, SoftReference<List<VirtualMachine>>>()

    public abstract fun get(pathToFolder: File): List<VirtualMachine>

//    /**
//     * Load the trace with the specified [name] and [format].
//     */
//    public fun get(pathToFolder: File): List<VirtualMachine> {
//
//
//
//        val trace = Trace.open(pathToFile, "carbon")
//
//        return parseCarbon(trace)
//    }

//    /**
//     * Load the trace with the specified [name] and [format].
//     */
//    public fun get(
//        name: String,
//        format: String,
//    ): List<VirtualMachine> {
//        val ref =
//            cache.compute(name) { key, oldVal ->
//                val inst = oldVal?.get()
//                if (inst == null) {
//                    val path = baseDir.resolve(key)
//
//                    logger.info { "Loading trace $key at $path" }
//
//                    val trace = Trace.open(path, format)
//                    val fragments = parseFragments(trace)
//                    val interferenceModel = parseInterferenceModel(trace)
//                    val vms = parseMeta(trace, fragments, interferenceModel)
//
//                    SoftReference(vms)
//                } else {
//                    oldVal
//                }
//            }
//
//        return checkNotNull(ref?.get()) { "Memory pressure" }
//    }

    /**
     * Clear the workload cache.
     */
    public fun reset() {
        cache.clear()
    }
}

