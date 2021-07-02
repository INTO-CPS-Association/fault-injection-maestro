
T=readtable("outputs_fault.csv");
T2 =readtable("outputs_normal.csv");

figure
plot(T.time, T.x_wt__wtInstance_level)

figure
plot(T2.time, T2.x_wt__wtInstance_level)