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

@file:JvmName("CarbonEBAColumns")

package org.opendc.trace.conv

/**
 * A column containing the timestamp of the carbon intensity measurement.
 */
public const val CARBON_EBA_TIMESTAMP: String = "timestamp"

/**
 * A column containing the intensity of the carbon when sampled.
 */
public const val CARBON_EBA_INTENSITY: String = "carbon_intensity"

/**
 * A column containing the available wind power when sampled.
 */
public const val CARBON_EBA_WND: String = "WND"

/**
 * A column containing the available solar power when sampled.
 */
public const val CARBON_EBA_SUN: String = "SUN"

/**
 * A column containing the available water power when sampled.
 */
public const val CARBON_EBA_WAT: String = "WAT"

/**
 * A column containing the available oil power when sampled.
 */
public const val CARBON_EBA_OIL: String = "OIL"

/**
 * A column containing the available Natural Gas power when sampled.
 */
public const val CARBON_EBA_NG: String = "NG"

/**
 * A column containing the available Coal power when sampled.
 */
public const val CARBON_EBA_COL: String = "COL"

/**
 * A column containing the available Nuclear power when sampled.
 */
public const val CARBON_EBA_NUC: String = "NUC"

/**
 * A column containing the available Other power when sampled.
 */
public const val CARBON_EBA_OTH: String = "OTH"
