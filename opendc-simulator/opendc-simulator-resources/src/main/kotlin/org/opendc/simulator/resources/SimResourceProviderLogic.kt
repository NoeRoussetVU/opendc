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

package org.opendc.simulator.resources

/**
 * The logic of a resource provider.
 */
public interface SimResourceProviderLogic {
    /**
     * This method is invoked when the resource is reported to idle until the specified [deadline].
     *
     * @param ctx The context in which the provider runs.
     * @param deadline The deadline that was requested by the resource consumer.
     * @return The instant at which to resume the consumer.
     */
    public fun onIdle(ctx: SimResourceControllableContext, deadline: Long): Long

    /**
     * This method is invoked when the resource will be consumed until the specified amount of [work] was processed
     * or [deadline] is reached.
     *
     * @param ctx The context in which the provider runs.
     * @param work The amount of work that was requested by the resource consumer.
     * @param limit The limit on the work rate of the resource consumer.
     * @param deadline The deadline that was requested by the resource consumer.
     * @return The instant at which to resume the consumer.
     */
    public fun onConsume(ctx: SimResourceControllableContext, work: Double, limit: Double, deadline: Long): Long

    /**
     * This method is invoked when the progress of the resource consumer is materialized.
     *
     * @param ctx The context in which the provider runs.
     * @param work The amount of work that was requested by the resource consumer.
     * @param willOvercommit A flag to indicate that the remaining work is overcommitted.
     */
    public fun onUpdate(ctx: SimResourceControllableContext, work: Double, willOvercommit: Boolean) {}

    /**
     * This method is invoked when the resource consumer has finished.
     */
    public fun onFinish(ctx: SimResourceControllableContext)

    /**
     * Compute the amount of work that was consumed over the specified [duration].
     *
     * @param work The total size of the resource consumption.
     * @param speed The speed of the resource provider.
     * @param duration The duration from the start of the consumption until now.
     * @return The amount of work that was consumed by the resource provider.
     */
    public fun getConsumedWork(ctx: SimResourceControllableContext, work: Double, speed: Double, duration: Long): Double {
        return if (duration > 0L) {
            return (duration / 1000.0) * speed
        } else {
            work
        }
    }
}
