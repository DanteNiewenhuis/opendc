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

package org.opendc.trace.opendc

import org.opendc.trace.*
import org.opendc.trace.conv.*
import org.opendc.trace.opendc.parquet.ResourceState
import org.opendc.trace.util.parquet.LocalParquetReader

/**
 * A [TableReader] implementation for the OpenDC virtual machine trace format.
 */
internal class OdcVmResourceStateTableReader(private val reader: LocalParquetReader<ResourceState>) : TableReader {
    /**
     * The current record.
     */
    private var record: ResourceState? = null

    override fun nextRow(): Boolean {
        try {
            val record = reader.read()
            this.record = record

            return record != null
        } catch (e: Throwable) {
            this.record = null
            throw e
        }
    }

    override fun resolve(column: TableColumn<*>): Int = columns[column] ?: -1

    override fun isNull(index: Int): Boolean {
        check(index in 0..columns.size) { "Invalid column index" }
        return get(index) == null
    }

    override fun get(index: Int): Any? {
        val record = checkNotNull(record) { "Reader in invalid state" }

        return when (index) {
            COL_ID -> record.id
            COL_TIMESTAMP -> record.timestamp
            COL_DURATION -> record.duration
            COL_CPU_COUNT -> record.cpuCount
            COL_CPU_USAGE -> record.cpuUsage
            else -> throw IllegalArgumentException("Invalid column index $index")
        }
    }

    override fun getBoolean(index: Int): Boolean {
        throw IllegalArgumentException("Invalid column or type [index $index]")
    }

    override fun getInt(index: Int): Int {
        val record = checkNotNull(record) { "Reader in invalid state" }
        return when (index) {
            COL_CPU_COUNT -> record.cpuCount
            else -> throw IllegalArgumentException("Invalid column or type [index $index]")
        }
    }

    override fun getLong(index: Int): Long {
        throw IllegalArgumentException("Invalid column or type [index $index]")
    }

    override fun getDouble(index: Int): Double {
        val record = checkNotNull(record) { "Reader in invalid state" }
        return when (index) {
            COL_CPU_USAGE -> record.cpuUsage
            else -> throw IllegalArgumentException("Invalid column or type [index $index]")
        }
    }

    override fun close() {
        reader.close()
    }

    override fun toString(): String = "OdcVmResourceStateTableReader"

    private val COL_ID = 0
    private val COL_TIMESTAMP = 1
    private val COL_DURATION = 2
    private val COL_CPU_COUNT = 3
    private val COL_CPU_USAGE = 4

    private val columns = mapOf(
        RESOURCE_ID to COL_ID,
        RESOURCE_STATE_TIMESTAMP to COL_TIMESTAMP,
        RESOURCE_STATE_DURATION to COL_DURATION,
        RESOURCE_CPU_COUNT to COL_CPU_COUNT,
        RESOURCE_STATE_CPU_USAGE to COL_CPU_USAGE,
    )
}