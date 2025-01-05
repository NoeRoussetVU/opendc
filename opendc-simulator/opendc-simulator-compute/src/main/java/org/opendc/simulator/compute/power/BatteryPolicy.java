package org.opendc.simulator.compute.power;

public class BatteryPolicy {

    double carbonThreshold = 100.0f;

    public SimBattery.STATE carbonPolicy(SimBattery battery, double carbonIntensity) {
        SimBattery.STATE state;
        if (carbonIntensity > carbonThreshold) {
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
}
