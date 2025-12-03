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

package org.opendc.trace.formats.carbon.eba

import org.opendc.trace.TableColumn
import org.opendc.trace.TableColumnType
import org.opendc.trace.TableReader
import org.opendc.trace.TableWriter
import org.opendc.trace.conv.CARBON_EBA_COL
import org.opendc.trace.conv.CARBON_EBA_INTENSITY
import org.opendc.trace.conv.CARBON_EBA_NG
import org.opendc.trace.conv.CARBON_EBA_NUC
import org.opendc.trace.conv.CARBON_EBA_OIL
import org.opendc.trace.conv.CARBON_EBA_OTH
import org.opendc.trace.conv.CARBON_EBA_SUN
import org.opendc.trace.conv.CARBON_EBA_TIMESTAMP
import org.opendc.trace.conv.CARBON_EBA_WAT
import org.opendc.trace.conv.CARBON_EBA_WND
import org.opendc.trace.conv.TABLE_CARBON_EBA
import org.opendc.trace.formats.carbon.eba.parquet.CarbonEBAReadSupport
import org.opendc.trace.spi.TableDetails
import org.opendc.trace.spi.TraceFormat
import org.opendc.trace.util.parquet.LocalParquetReader
import java.nio.file.Path

/**
 * A [TraceFormat] implementation for the Carbon Intensity trace.
 */
public class CarbonEBATraceFormat : TraceFormat {
    override val name: String = "carbon_intensity"

    override fun create(path: Path) {
        throw UnsupportedOperationException("Writing not supported for this format")
    }

    override fun getTables(path: Path): List<String> = listOf(TABLE_CARBON_EBA)

    override fun getDetails(
        path: Path,
        table: String,
    ): TableDetails {
        return when (table) {
            TABLE_CARBON_EBA ->
                TableDetails(
                    listOf(
                        TableColumn(CARBON_EBA_TIMESTAMP, TableColumnType.Instant),
                        TableColumn(CARBON_EBA_INTENSITY, TableColumnType.Double),
                        TableColumn(CARBON_EBA_WND, TableColumnType.Long),
                        TableColumn(CARBON_EBA_SUN, TableColumnType.Long),
                        TableColumn(CARBON_EBA_WAT, TableColumnType.Long),
                        TableColumn(CARBON_EBA_OIL, TableColumnType.Long),
                        TableColumn(CARBON_EBA_NG, TableColumnType.Long),
                        TableColumn(CARBON_EBA_COL, TableColumnType.Long),
                        TableColumn(CARBON_EBA_NUC, TableColumnType.Long),
                        TableColumn(CARBON_EBA_OTH, TableColumnType.Long),
                    ),
                )
            else -> throw IllegalArgumentException("Table $table not supported")
        }
    }

    override fun newReader(
        path: Path,
        table: String,
        projection: List<String>?,
    ): TableReader {
        return when (table) {
            TABLE_CARBON_EBA -> {
                val reader = LocalParquetReader(path, CarbonEBAReadSupport(projection))
                CarbonEBATableReader(reader)
            }
            else -> throw IllegalArgumentException("Table $table not supported")
        }
    }

    override fun newWriter(
        path: Path,
        table: String,
    ): TableWriter {
        throw UnsupportedOperationException("Writing not supported for this format")
    }
}
