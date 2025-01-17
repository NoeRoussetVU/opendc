import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import statistics as st

df_host = pd.read_parquet("output/simple/raw-output/0/seed=0/host.parquet")
df_power = pd.read_parquet("output/simple/raw-output/0/seed=0/powerSource.parquet")
df_task = pd.read_parquet("output/simple/raw-output/0/seed=0/task.parquet")
df_service = pd.read_parquet("output/simple/raw-output/0/seed=0/service.parquet")
df_battery = pd.read_parquet("output/simple/raw-output/0/seed=0/battery.parquet")

fig, axs = plt.subplots(2)
fig.suptitle('Energy Usage and Carbon Emission')
axs[0].plot(df_power.timestamp, df_power.energy_usage, label="power source energy usage")
axs[0].plot(df_battery.timestamp, df_battery.energy_usage, label="battery energy usage")
axs[0].set_xlabel('time (ms)')
axs[0].set_ylabel('energy usage (J)')
axs[0].legend()
axs[1].plot(df_power.timestamp, df_power.carbon_emission, label="carbon emission")
axs[1].plot(df_power.timestamp, df_power.carbon_intensity, label="carbon intensity")
axs[1].set_xlabel('time (ms)')
axs[1].set_ylabel('carbon emission (g)')
axs[1].legend()
plt.show()
plt.close()

