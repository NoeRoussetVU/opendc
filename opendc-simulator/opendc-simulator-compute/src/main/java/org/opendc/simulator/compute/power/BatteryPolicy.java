package org.opendc.simulator.compute.power;

import org.opendc.simulator.engine.FlowGraph;

import java.util.List;
import java.util.Objects;

public class BatteryPolicy {

    private String policy;
    private double policyThreshold;

    public BatteryPolicy(String policy, double policyTheshold) {
        this.policy = policy;
        this.policyThreshold = policyTheshold;
    }

    public SimBattery.STATE mainPolicy(SimBattery battery, double carbonIntensity, double powerDemand){
        if(Objects.equals(policy, "carbon")){
            return carbonPolicy(battery, carbonIntensity);
        }
        else if(Objects.equals(policy, "power")){
            return powerDemandPolicy(battery, powerDemand);
        }
        else if(Objects.equals(policy, "cyclic")){
            return cyclicPolicy(battery);
        }
        else{
            return SimBattery.STATE.IDLE;
        }
    }

    public SimBattery.STATE carbonPolicy(SimBattery battery, double carbonIntensity) {
        SimBattery.STATE state;
        if (carbonIntensity > policyThreshold) {
            if (battery.getCurrentCapacity() > battery.getMinChargedValue()) {
                state = SimBattery.STATE.SUPPLYING;
            } else {
                state = SimBattery.STATE.IDLE;
            }
        } else {
            if (battery.getCurrentCapacity() < battery.getMaxChargedValue()) {
                state = SimBattery.STATE.CHARGING;
            } else {
                state = SimBattery.STATE.IDLE;
            }
        }
        return state;
    }

    public SimBattery.STATE powerDemandPolicy(SimBattery battery, double powerDemand) {
        SimBattery.STATE state;
        if (powerDemand > policyThreshold) {
            if (battery.getCurrentCapacity() > battery.getMinChargedValue()) {
                state = SimBattery.STATE.SUPPLYING;
            } else {
                state = SimBattery.STATE.IDLE;
            }
        } else {
            if (battery.getCurrentCapacity() < battery.getMaxChargedValue()) {
                state = SimBattery.STATE.CHARGING;
            } else {
                state = SimBattery.STATE.IDLE;
            }
        }
        return state;
    }

    public SimBattery.STATE cyclicPolicy(SimBattery battery) {
        SimBattery.STATE state;
        if (battery.getCurrentCapacity() < battery.getMinChargedValue()) {
            state = SimBattery.STATE.CHARGING;
        }
        else if (battery.getCurrentCapacity() > battery.getMaxChargedValue()){
            state = SimBattery.STATE.SUPPLYING;
        }
        else if(battery.getBatteryState() == SimBattery.STATE.IDLE){
            state = SimBattery.STATE.SUPPLYING;
        }
        else {
            state = battery.getBatteryState();
        }
        return state;
    }
}
