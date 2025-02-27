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

@file:JvmName("TopologyFactories")

package org.opendc.compute.topology

import org.opendc.compute.topology.specs.BatteryJSONSpec
import org.opendc.compute.topology.specs.ClusterJSONSpec
import org.opendc.compute.topology.specs.ClusterSpec
import org.opendc.compute.topology.specs.HostJSONSpec
import org.opendc.compute.topology.specs.HostSpec
import org.opendc.compute.topology.specs.PowerSourceSpec
import org.opendc.compute.topology.specs.BatterySpec
import org.opendc.compute.topology.specs.TopologySpec
import org.opendc.simulator.compute.cpu.getPowerModel
import org.opendc.simulator.compute.models.CpuModel
import org.opendc.simulator.compute.models.MachineModel
import org.opendc.simulator.compute.models.MemoryUnit
import java.io.File
import java.io.InputStream
import java.util.SplittableRandom
import java.util.UUID
import java.util.random.RandomGenerator

/**
 * A [TopologyReader] that is used to read the cluster definition file.
 */
private val reader = TopologyReader()

/**
 * Construct a topology from the specified [pathToFile].
 */
public fun clusterTopology(
    pathToFile: String,
    random: RandomGenerator = SplittableRandom(0),
): List<ClusterSpec> {
    return clusterTopology(File(pathToFile), random)
}

/**
 * Construct a topology from the specified [file].
 */
public fun clusterTopology(
    file: File,
    random: RandomGenerator = SplittableRandom(0),
): List<ClusterSpec> {
    val topology = reader.read(file)
    return topology.toClusterSpec(random)
}

/**
 * Construct a topology from the specified [input].
 */
public fun clusterTopology(
    input: InputStream,
    random: RandomGenerator = SplittableRandom(0),
): List<ClusterSpec> {
    val topology = reader.read(input)
    return topology.toClusterSpec(random)
}

/**
 * Helper method to convert a [TopologySpec] into a list of [HostSpec]s.
 */
private fun TopologySpec.toClusterSpec(random: RandomGenerator): List<ClusterSpec> {
    return clusters.map { cluster ->
        cluster.toClusterSpec(random)
    }
}

/**
 * Helper method to convert a [ClusterJSONSpec] into a list of [HostSpec]s.
 */
private var clusterId = 0

private fun ClusterJSONSpec.toClusterSpec(random: RandomGenerator): ClusterSpec {
    val hostSpecs =
        hosts.flatMap { host ->
            (
                List(host.count) {
                    host.toHostSpec(
                        clusterId,
                        random,
                    )
                }
            )
        }
    val powerSourceSpec =
        PowerSourceSpec(
            UUID(random.nextLong(), (clusterId).toLong()),
            totalPower = this.powerSource.totalPower,
            carbonTracePath = this.powerSource.carbonTracePath,
        )
    val batterySpec =
        BatterySpec(
            UUID(random.nextLong(), clusterId.toLong()),
            capacity = this.battery.capacity,
            policy = this.battery.policy,
            policyThreshold = this.battery.policyThreshold,
            chargeRate = this.battery.chargeRate
        )
    clusterId++
    return ClusterSpec(this.name, hostSpecs, powerSourceSpec, batterySpec)
}

/**
 * Helper method to convert a [HostJSONSpec] into a [HostSpec]s.
 */
private var hostId = 0
private var globalCoreId = 0

private fun HostJSONSpec.toHostSpec(
    clusterId: Int,
    random: RandomGenerator,
): HostSpec {
    val units =
        List(cpu.count) {
            CpuModel(
                globalCoreId++,
                cpu.coreCount,
                cpu.coreSpeed.toMHz(),
            )
        }

    val unknownMemoryUnit = MemoryUnit(memory.vendor, memory.modelName, memory.memorySpeed.toMHz(), memory.memorySize.toMiB().toLong())
    val machineModel =
        MachineModel(
            units,
            unknownMemoryUnit,
        )

    val powerModel =
        getPowerModel(powerModel.modelType, powerModel.power.toWatts(), powerModel.maxPower.toWatts(), powerModel.idlePower.toWatts())

    var hostName: String
    if (name == null) {
        hostName = "Host-$hostId"
    } else {
        hostName = name
    }

    val hostSpec =
        HostSpec(
            UUID(random.nextLong(), (hostId).toLong()),
            hostName,
            mapOf("cluster" to clusterId),
            machineModel,
            powerModel,
        )
    hostId++

    return hostSpec
}
