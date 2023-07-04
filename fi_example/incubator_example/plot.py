from pandas import DataFrame, read_csv
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import sys

print("Plotting results...")
path = sys.argv[1] + "/outputs.csv"

data = pd.read_csv(path)
fig, (ax1, ax2, ax3, ax4) = plt.subplots(4, 1)

ax1.plot(data["time"], data["{Plant}.p.T_bair_out"], label="Real_T_bair")
ax1.plot(data["time"], data["{KalmanFilter}.k.T_bair_out"], label="KalmanFilter_T_bair")
ax1.legend()

ax2.plot(data["time"], data["{Controller}.c.heater_on_out"], label="Controller_heater_on")
ax2.legend()

ax3.plot(data["time"], data["{KalmanFilter}.k.T_heater_out"], label="KalmanFilter_T_heater")
ax3.legend()

ax4.plot(data["time"], data["{Supervisor}.s.H_out"], label="Supervisor_H")
ax4.plot(data["time"], data["{Supervisor}.s.C_out"], label="Supervisor_C")
ax4.legend()

plt.savefig(sys.argv[1]+"_results.png")
#plt.show()