/*
 * Copyright (c) 2022 AtLarge Research
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

package org.opendc.trace.formats.carbon.eba.parquet

import org.apache.hadoop.conf.Configuration
import org.apache.parquet.hadoop.api.InitContext
import org.apache.parquet.hadoop.api.ReadSupport
import org.apache.parquet.io.api.RecordMaterializer
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Types
import org.opendc.trace.conv.CARBON_EBA_WND
import org.opendc.trace.conv.CARBON_EBA_SUN
import org.opendc.trace.conv.CARBON_EBA_WAT
import org.opendc.trace.conv.CARBON_EBA_OIL
import org.opendc.trace.conv.CARBON_EBA_NG
import org.opendc.trace.conv.CARBON_EBA_COL
import org.opendc.trace.conv.CARBON_EBA_INTENSITY
import org.opendc.trace.conv.CARBON_EBA_NUC
import org.opendc.trace.conv.CARBON_EBA_OTH
import org.opendc.trace.conv.CARBON_EBA_TIMESTAMP

/**
 * A [ReadSupport] instance for [CarbonEBAFragment] objects.
 *
 * @param projection The projection of the table to read.
 */
internal class CarbonEBAReadSupport(private val projection: List<String>?) : ReadSupport<CarbonEBAFragment>() {
    /**
     * Mapping of table columns to their Parquet column names.
     */
    private val colMap =
        mapOf(
            CARBON_EBA_TIMESTAMP to "timestamp",
            CARBON_EBA_INTENSITY to "carbon_intensity",
            CARBON_EBA_WND to "WND",
            CARBON_EBA_SUN to "SUN",
            CARBON_EBA_WAT to "WAT",
            CARBON_EBA_OIL to "OIL",
            CARBON_EBA_NG to "NG",
            CARBON_EBA_COL to "COL",
            CARBON_EBA_NUC to "NUC",
            CARBON_EBA_OTH to "OTH",
        )

    override fun init(context: InitContext): ReadContext {
        val projectedSchema =
            if (projection != null) {
                Types.buildMessage()
                    .apply {
                        val fieldByName = CARBON_SCHEMA.fields.associateBy { it.name }

                        for (col in projection) {
                            val fieldName = colMap[col] ?: continue
                            addField(fieldByName.getValue(fieldName))
                        }
                    }
                    .named(CARBON_SCHEMA.name)
            } else {
                CARBON_SCHEMA
            }
        return ReadContext(projectedSchema)
    }

    override fun prepareForRead(
        configuration: Configuration,
        keyValueMetaData: Map<String, String>,
        fileSchema: MessageType,
        readContext: ReadContext,
    ): RecordMaterializer<CarbonEBAFragment> = CarbonEBARecordMaterializer(readContext.requestedSchema)
}
