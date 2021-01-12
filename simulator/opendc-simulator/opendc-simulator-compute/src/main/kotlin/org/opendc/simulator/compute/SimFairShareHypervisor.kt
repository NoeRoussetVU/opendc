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

package org.opendc.simulator.compute

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opendc.simulator.compute.interference.PerformanceInterferenceModel
import org.opendc.simulator.compute.model.ProcessingUnit
import org.opendc.simulator.compute.workload.SimResourceCommand
import org.opendc.simulator.compute.workload.SimWorkload
import org.opendc.simulator.compute.workload.SimWorkloadBarrier
import java.time.Clock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A [SimHypervisor] that distributes the computing requirements of multiple [SimWorkload] on a single
 * [SimBareMetalMachine] concurrently using weighted fair sharing.
 *
 * @param listener The hypervisor listener to use.
 */
public class SimFairShareHypervisor(private val listener: SimHypervisor.Listener? = null) : SimHypervisor {

    override fun onStart(ctx: SimExecutionContext) {
        val model = ctx.machine
        this.ctx = ctx
        this.commands = Array(model.cpus.size) { SimResourceCommand.Idle() }
        this.pCpus = model.cpus.indices.sortedBy { model.cpus[it].frequency }.toIntArray()
        this.maxUsage = model.cpus.sumByDouble { it.frequency }
        this.barrier = SimWorkloadBarrier(model.cpus.size)
    }

    override fun onStart(ctx: SimExecutionContext, cpu: Int): SimResourceCommand {
        return commands[cpu]
    }

    override fun onNext(ctx: SimExecutionContext, cpu: Int, remainingWork: Double): SimResourceCommand {
        totalRemainingWork += remainingWork
        val isLast = barrier.enter()

        // Flush the progress of the guest after the barrier has been reached.
        if (isLast && isDirty) {
            isDirty = false
            flushGuests()
        }

        return if (isDirty) {
            // Wait for the scheduler determine the work after the barrier has been reached by all CPUs.
            SimResourceCommand.Idle()
        } else {
            // Indicate that the scheduler needs to run next call.
            if (isLast) {
                isDirty = true
            }

            commands[cpu]
        }
    }

    override fun canFit(model: SimMachineModel): Boolean = true

    override fun createMachine(
        model: SimMachineModel,
        performanceInterferenceModel: PerformanceInterferenceModel?
    ): SimMachine = SimVm(model, performanceInterferenceModel)

    /**
     * The execution context in which the hypervisor runs.
     */
    private lateinit var ctx: SimExecutionContext

    /**
     * The commands to submit to the underlying host.
     */
    private lateinit var commands: Array<SimResourceCommand>

    /**
     * The active vCPUs.
     */
    private val vcpus: MutableList<VCpu> = mutableListOf()

    /**
     * The indices of the physical CPU ordered by their speed.
     */
    private lateinit var pCpus: IntArray

    /**
     * The maximum amount of work to be performed per second.
     */
    private var maxUsage: Double = 0.0

    /**
     * The current load on the hypervisor.
     */
    private var load: Double = 0.0

    /**
     * The total amount of remaining work (of all pCPUs).
     */
    private var totalRemainingWork: Double = 0.0

    /**
     * The total speed requested by the vCPUs.
     */
    private var totalRequestedSpeed = 0.0

    /**
     * The total amount of work requested by the vCPUs.
     */
    private var totalRequestedWork = 0.0

    /**
     * The total allocated speed for the vCPUs.
     */
    private var totalAllocatedSpeed = 0.0

    /**
     * The total allocated work requested for the vCPUs.
     */
    private var totalAllocatedWork = 0.0

    /**
     * The amount of work that could not be performed due to over-committing resources.
     */
    private var totalOvercommittedWork = 0.0

    /**
     * The amount of work that was lost due to interference.
     */
    private var totalInterferedWork = 0.0

    /**
     * A flag to indicate that the scheduler has submitted work that has not yet been completed.
     */
    private var isDirty: Boolean = false

    /**
     * The scheduler barrier.
     */
    private lateinit var barrier: SimWorkloadBarrier

    /**
     * Indicate that the workloads should be re-scheduled.
     */
    private fun shouldSchedule() {
        isDirty = true
        ctx.interruptAll()
    }

    /**
     * Schedule the work over the physical CPUs.
     */
    private fun doSchedule() {
        // If there is no work yet, mark all pCPUs as idle.
        if (vcpus.isEmpty()) {
            commands.fill(SimResourceCommand.Idle())
            ctx.interruptAll()
        }

        var duration: Double = Double.MAX_VALUE
        var deadline: Long = Long.MAX_VALUE
        var availableSpeed = maxUsage
        var totalRequestedSpeed = 0.0
        var totalRequestedWork = 0.0

        // Sort the vCPUs based on their requested usage
        // Profiling shows that it is faster to sort every slice instead of maintaining some kind of sorted set
        vcpus.sort()

        // Divide the available host capacity fairly across the vCPUs using max-min fair sharing
        val vcpuIterator = vcpus.listIterator()
        var remaining = vcpus.size
        while (vcpuIterator.hasNext()) {
            val vcpu = vcpuIterator.next()
            val availableShare = availableSpeed / remaining--

            when (val command = vcpu.command) {
                is SimResourceCommand.Idle -> {
                    // Take into account the minimum deadline of this slice before we possible continue
                    deadline = min(deadline, command.deadline)

                    vcpu.actualSpeed = 0.0
                }
                is SimResourceCommand.Consume -> {
                    val grantedSpeed = min(vcpu.allowedSpeed, availableShare)

                    // Take into account the minimum deadline of this slice before we possible continue
                    deadline = min(deadline, command.deadline)

                    // Ignore idle computation
                    if (grantedSpeed <= 0.0 || command.work <= 0.0) {
                        vcpu.actualSpeed = 0.0
                        continue
                    }

                    totalRequestedSpeed += command.limit
                    totalRequestedWork += command.work

                    vcpu.actualSpeed = grantedSpeed
                    availableSpeed -= grantedSpeed

                    // The duration that we want to run is that of the shortest request from a vCPU
                    duration = min(duration, command.work / grantedSpeed)
                }
                SimResourceCommand.Exit -> {
                    // Apparently the vCPU has exited, so remove it from the scheduling queue.
                    vcpuIterator.remove()
                }
            }
        }

        // Round the duration to milliseconds
        duration = ceil(duration * 1000) / 1000

        assert(deadline >= ctx.clock.millis()) { "Deadline already passed" }

        val totalAllocatedSpeed = maxUsage - availableSpeed
        var totalAllocatedWork = 0.0
        availableSpeed = totalAllocatedSpeed
        load = totalAllocatedSpeed / maxUsage

        // Divide the requests over the available capacity of the pCPUs fairly
        for (i in pCpus) {
            val maxCpuUsage = ctx.machine.cpus[i].frequency
            val fraction = maxCpuUsage / maxUsage
            val grantedSpeed = min(maxCpuUsage, totalAllocatedSpeed * fraction)
            val grantedWork = duration * grantedSpeed

            commands[i] =
                if (grantedWork > 0.0 && grantedSpeed > 0.0)
                    SimResourceCommand.Consume(grantedWork, grantedSpeed, deadline)
                else
                    SimResourceCommand.Idle(deadline)

            totalAllocatedWork += grantedWork
            availableSpeed -= grantedSpeed
        }

        this.totalRequestedSpeed = totalRequestedSpeed
        this.totalRequestedWork = totalRequestedWork
        this.totalAllocatedSpeed = totalAllocatedSpeed
        this.totalAllocatedWork = totalAllocatedWork

        ctx.interruptAll()
    }

    /**
     * Flush the progress of the vCPUs.
     */
    private fun flushGuests() {
        // Flush all the vCPUs work
        for (vcpu in vcpus) {
            vcpu.flush(interrupt = false)
        }

        // Report metrics
        listener?.onSliceFinish(
            this,
            totalRequestedWork.toLong(),
            (totalAllocatedWork - totalRemainingWork).toLong(),
            totalOvercommittedWork.toLong(),
            totalInterferedWork.toLong(),
            totalRequestedSpeed,
            totalAllocatedSpeed
        )
        totalRemainingWork = 0.0
        totalInterferedWork = 0.0
        totalOvercommittedWork = 0.0

        // Force all pCPUs to re-schedule their work.
        doSchedule()
    }

    /**
     * Interrupt all host CPUs.
     */
    private fun SimExecutionContext.interruptAll() {
        for (i in machine.cpus.indices) {
            interrupt(i)
        }
    }

    /**
     * A virtual machine running on the hypervisor.
     *
     * @property model The machine model of the virtual machine.
     * @property performanceInterferenceModel The performance interference model to utilize.
     */
    private inner class SimVm(
        override val model: SimMachineModel,
        val performanceInterferenceModel: PerformanceInterferenceModel? = null,
    ) : SimMachine {
        /**
         * A [StateFlow] representing the CPU usage of the simulated machine.
         */
        override val usage: MutableStateFlow<Double> = MutableStateFlow(0.0)

        /**
         *  A flag to indicate that the machine is terminated.
         */
        private var isTerminated = false

        /**
         * The current active workload.
         */
        private var cont: Continuation<Unit>? = null

        /**
         * The active CPUs of this virtual machine.
         */
        private var cpus: List<VCpu> = emptyList()

        /**
         * The execution context in which the workload runs.
         */
        val ctx = object : SimExecutionContext {
            override val machine: SimMachineModel
                get() = model

            override val clock: Clock
                get() = this@SimFairShareHypervisor.ctx.clock

            override fun interrupt(cpu: Int) {
                require(cpu < cpus.size) { "Invalid CPU identifier" }
                cpus[cpu].interrupt()
            }
        }

        /**
         * Run the specified [SimWorkload] on this machine and suspend execution util the workload has finished.
         */
        override suspend fun run(workload: SimWorkload) {
            require(!isTerminated) { "Machine is terminated" }
            require(cont == null) { "Run should not be called concurrently" }

            workload.onStart(ctx)

            return suspendCancellableCoroutine { cont ->
                this.cont = cont
                this.cpus = model.cpus.map { VCpu(this, it, workload) }

                for (cpu in cpus) {
                    // Register vCPU to scheduler
                    vcpus.add(cpu)

                    cpu.start()
                }

                // Re-schedule the work over the pCPUs
                shouldSchedule()
            }
        }

        /**
         * Terminate this VM instance.
         */
        override fun close() {
            isTerminated = true
        }

        /**
         * Update the usage of the VM.
         */
        fun updateUsage() {
            usage.value = cpus.sumByDouble { it.actualSpeed } / cpus.sumByDouble { it.model.frequency }
        }

        /**
         * This method is invoked when one of the CPUs has exited.
         */
        fun onCpuExit(cpu: Int) {
            // Check whether all other CPUs have finished
            if (cpus.all { it.hasExited }) {
                val cont = cont
                this.cont = null
                cont?.resume(Unit)
            }
        }

        /**
         * This method is invoked when one of the CPUs failed.
         */
        fun onCpuFailure(e: Throwable) {
            // In case the flush fails with an exception, immediately propagate to caller, cancelling all other
            // tasks.
            val cont = cont
            this.cont = null
            cont?.resumeWithException(e)
        }
    }

    /**
     * A CPU of the virtual machine.
     */
    private inner class VCpu(val vm: SimVm, val model: ProcessingUnit, val workload: SimWorkload) : Comparable<VCpu> {
        /**
         * The latest command processed by the CPU.
         */
        var command: SimResourceCommand = SimResourceCommand.Idle()

        /**
         * The latest timestamp at which the vCPU was flushed.
         */
        var latestFlush: Long = 0

        /**
         * The processing speed that is allowed by the model constraints.
         */
        var allowedSpeed: Double = 0.0

        /**
         * The actual processing speed.
         */
        var actualSpeed: Double = 0.0
            set(value) {
                field = value
                vm.updateUsage()
            }

        /**
         * A flag to indicate that the CPU is currently processing a command.
         */
        var isIntermediate: Boolean = false

        /**
         * A flag to indicate that the CPU has exited.
         */
        val hasExited: Boolean
            get() = command is SimResourceCommand.Exit

        /**
         * Process the specified [SimResourceCommand] for this CPU.
         */
        fun process(command: SimResourceCommand) {
            // Assign command as the most recent executed command
            this.command = command

            when (command) {
                is SimResourceCommand.Idle -> {
                    require(command.deadline >= ctx.clock.millis()) { "Deadline already passed" }

                    allowedSpeed = 0.0
                }
                is SimResourceCommand.Consume -> {
                    require(command.deadline >= ctx.clock.millis()) { "Deadline already passed" }

                    allowedSpeed = min(model.frequency, command.limit)
                }
                is SimResourceCommand.Exit -> {
                    allowedSpeed = 0.0
                    actualSpeed = 0.0

                    vm.onCpuExit(model.id)
                }
            }
        }

        /**
         * Start the CPU.
         */
        fun start() {
            try {
                isIntermediate = true
                latestFlush = ctx.clock.millis()

                process(workload.onStart(vm.ctx, model.id))
            } catch (e: Throwable) {
                fail(e)
            } finally {
                isIntermediate = false
            }
        }

        /**
         * Flush the work performed by the CPU.
         */
        fun flush(interrupt: Boolean) {
            val now = ctx.clock.millis()

            // Fast path: if the CPU was already flushed at at the current instant, no need to flush the progress.
            if (latestFlush >= now) {
                return
            }

            try {
                isIntermediate = true
                when (val command = command) {
                    is SimResourceCommand.Idle -> {
                        // Act like nothing has happened in case the vCPU did not reach its deadline or was not
                        // interrupted by the user.
                        if (interrupt || command.deadline <= now) {
                            process(workload.onNext(vm.ctx, model.id, 0.0))
                        }
                    }
                    is SimResourceCommand.Consume -> {
                        // Apply performance interference model
                        val performanceScore = vm.performanceInterferenceModel?.apply(load) ?: 1.0

                        // Compute the remaining amount of work
                        val remainingWork = if (command.work > 0.0) {
                            // Compute the fraction of compute time allocated to the VM
                            val fraction = actualSpeed / totalAllocatedSpeed

                            // Compute the work that was actually granted to the VM.
                            val processingAvailable = max(0.0, totalAllocatedWork - totalRemainingWork) * fraction
                            val processed = processingAvailable * performanceScore

                            val interferedWork = processingAvailable - processed
                            totalInterferedWork += interferedWork

                            max(0.0, command.work - processed)
                        } else {
                            0.0
                        }

                        // Act like nothing has happened in case the vCPU did not finish yet or was not interrupted by
                        // the user.
                        if (interrupt || remainingWork == 0.0 || command.deadline <= now) {
                            if (!interrupt) {
                                totalOvercommittedWork += remainingWork
                            }

                            process(workload.onNext(vm.ctx, model.id, remainingWork))
                        } else {
                            process(SimResourceCommand.Consume(remainingWork, command.limit, command.deadline))
                        }
                    }
                    SimResourceCommand.Exit ->
                        throw IllegalStateException()
                }
            } catch (e: Throwable) {
                fail(e)
            } finally {
                latestFlush = now
                isIntermediate = false
            }
        }

        /**
         * Interrupt the CPU.
         */
        fun interrupt() {
            // Prevent users from interrupting the CPU while it is constructing its next command, this will only lead
            // to infinite recursion.
            if (isIntermediate) {
                return
            }

            flush(interrupt = true)

            // Force the scheduler to re-schedule
            shouldSchedule()
        }

        /**
         * Fail the CPU.
         */
        fun fail(e: Throwable) {
            command = SimResourceCommand.Exit
            vm.onCpuFailure(e)
        }

        override fun compareTo(other: VCpu): Int = allowedSpeed.compareTo(other.allowedSpeed)
    }
}