package org.opendc.compute.topology.specs

import java.util.UUID

public data class BatterySpec(
    val uid: UUID,
    val capacity: Float,
    val policy: String,
    val policyThreshold: Float,
    val chargeRate: Float
)
