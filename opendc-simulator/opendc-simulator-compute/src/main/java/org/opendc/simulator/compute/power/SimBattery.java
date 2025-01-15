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

package org.opendc.simulator.compute.power;

import org.opendc.simulator.compute.cpu.SimCpu;
import org.opendc.simulator.engine.*;

/**
 * A {@link SimPsu} implementation that estimates the power consumption based on CPU usage.
 */
public final class SimBattery  extends FlowNode implements FlowSupplier {
    private long lastUpdate;

    private double powerDemand = 0.0f;
    public double powerSupplied = 0.0f;
    private double totalEnergyUsage = 0.0f;

    private double chargeReceived = 0.0f;
    private double totalChargeReceived = 0.0f;

    private FlowEdge muxEdge;

    private double capacity;
    private double currentCapacity = 0.0f;

    private double chargeRate;
    private double minChargedValue;
    private double maxChargedValue;

    public enum STATE {
        CHARGING,
        IDLE,
        SUPPLYING
    }
    STATE state;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Determine whether the InPort is connected to a {@link SimCpu}.
     *
     * @return <code>true</code> if the InPort is connected to an OutPort, <code>false</code> otherwise.
     */
    public boolean isConnected() {
        return muxEdge != null;
    }

    /**
     * Return the power demand of the machine (in W) measured in the PSU.
     * <p>
     * This method provides access to the power consumption of the machine before PSU losses are applied.
     */
    public double getPowerDemand() {
        return this.powerDemand;
    }

    /**
     * Return the instantaneous power usage of the machine (in W) measured at the InPort of the power supply.
     */
    public double getPowerDraw() {
        return this.powerSupplied;
    }

    /**
     * Return the cumulated energy usage of the machine (in J) measured at the InPort of the powers supply.
     */
    public double getEnergyUsage() {
        return totalEnergyUsage;
    }

    public double getChargeReceived() {
        return chargeReceived;
    }

    public double getTotalChargeReceived() {
        return totalChargeReceived;
    }

    public int getStateInt() {
        if(state == STATE.CHARGING){
            return 2;
        }
        else if(state == STATE.SUPPLYING){
            return 1;
        }
        else{
            return 0;
        }
    }

    @Override
    public double getCapacity() {
        return this.capacity;
    }

    public double getCurrentCapacity() {
        return this.currentCapacity;
    }

    public double getMinChargedValue(){
        return this.minChargedValue;
    }

    public double getMaxChargedValue(){
        return this.maxChargedValue;
    }

    public double getChargeRate(){
        return this.chargeRate;
    }

    public STATE getBatteryState() { return state; }

    public void setPowerDemand(double powerDemand) { this.powerDemand = powerDemand; }

    public void setBatteryState(STATE newState) {
        this.state = newState;
    }

    public void setChargeReceived(double newSupply){
        this.chargeReceived = newSupply;
        updateCounters();
    }

    public void setCurrentCapacity(double newSupply){
        currentCapacity += newSupply;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimBattery(FlowGraph graph, double max_capacity, double chargeRate) {
        super(graph);

        this.capacity = max_capacity;
        this.minChargedValue = 0.2*max_capacity;
        this.maxChargedValue = 0.8*max_capacity;
        this.chargeRate = chargeRate;

        this.state = STATE.IDLE;

        lastUpdate = this.clock.millis();
    }

    public void close() {

        this.closeNode();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowNode related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        updateCounters();
        return Long.MAX_VALUE;
    }

    public void updateCounters() {
        updateCounters(clock.millis());
    }

    /**
     * Calculate the energy usage up until <code>now</code>.
     */
    public void updateCounters(long now) {
        long lastUpdate = this.lastUpdate;
        this.lastUpdate = now;

        long duration = now - lastUpdate;
        if (duration > 0) {
            double energyUsage = (this.powerSupplied * duration * 0.001);
            this.totalEnergyUsage += energyUsage;
            this.currentCapacity -= energyUsage;

            double energyReceived = (this.chargeReceived * duration * 0.001);
            this.totalChargeReceived += energyReceived;
            this.currentCapacity += energyReceived;
            if(this.currentCapacity > this.maxChargedValue){
                this.setBatteryState(STATE.IDLE);
            }
            else if(this.currentCapacity < this.minChargedValue){
                this.setBatteryState(STATE.IDLE);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleDemand(FlowEdge consumerEdge, double newPowerDemand) {
        this.powerDemand = newPowerDemand;

        this.pushSupply(consumerEdge, newPowerDemand);
        this.invalidate();
    }

    @Override
    public void pushSupply(FlowEdge consumerEdge, double newSupply) {
        this.powerSupplied = newSupply;
        //this.currentCapacity -= newSupply;
        consumerEdge.pushSupply(newSupply);
    }

    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.muxEdge = consumerEdge;
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        this.muxEdge = null;
    }
}
