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

package org.opendc.compute.simulator.telemetry.table

import org.opendc.simulator.compute.power.SimPowerManager
import java.time.Duration
import java.time.Instant

/**
 * An aggregator for task metrics before they are reported.
 */
public class PowerManagerTableReaderImpl(
    powerManager: SimPowerManager,
    private val startTime: Duration = Duration.ofMillis(0),
) : PowerManagerTableReader {
    override fun copy(): PowerManagerTableReader {
        val newPowerManagerTable =
            PowerManagerTableReaderImpl(
                powerManager,
            )
        newPowerManagerTable.setValues(this)

        return newPowerManagerTable
    }

    override fun setValues(table: PowerManagerTableReader) {
        _timestamp = table.timestamp
        _timestampAbsolute = table.timestampAbsolute

        _hostsConnected = table.hostsConnected
        _powerDraw = table.powerDraw
        _energyUsage = table.energyUsage
    }

    private val powerManager = powerManager

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

    /**
     * Record the next cycle.
     */
    override fun record(now: Instant) {
        _timestamp = now
        _timestampAbsolute = now + startTime

        _hostsConnected = 0

        powerManager.updateCounters()
        _powerDraw = powerManager.powerDraw
        _energyUsage = powerManager.energyUsage
    }

    /**
     * Finish the aggregation for this cycle.
     */
    override fun reset() {
        previousEnergyUsage = _energyUsage

        _hostsConnected = 0
        _powerDraw = 0.0
        _energyUsage = 0.0
    }
}
