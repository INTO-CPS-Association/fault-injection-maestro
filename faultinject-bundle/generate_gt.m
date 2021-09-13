%% Generate ground truth data
clear;
time = 0.1:0.1:19.9;
boolout = strings(1,199)';
realout = zeros(1,199)';
intout = zeros(1,199)';
stringout = strings(1,199)';

for i= 1:199
   boolout(i) = 'false';
   stringout(i) = 'bahamas';
end

% inject bool t=0.8, flip to true
boolout(80) = 'true';

% inject real 0.2<=t<0.5, by rule t+2*var_3
for i=2:4
    realout(i)= i*0.1 + 2*realout(i-1);
end

% inject int t>=10.0, by rule var_5+35
for i=100:199
    intout(i)= intout(i-1) + 35;
end

% inject string t=12.0 to halløj
stringout(120) = 'halloj';

writematrix([time', boolout, realout, intout, stringout],'output_ground_truth.csv')
