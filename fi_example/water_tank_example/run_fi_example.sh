#!/bin/sh

echo "Generating Mabl specifications with FI"

java -jar ../maestro-fi.jar import sg1 ../FaultInject.mabl multimodelFI.json simulation-config.json -output generateFI/

echo "Simulating specifications"

java -jar ../maestro-fi.jar interpret generateFI/spec.mabl ../FaultInject.mabl -output targetFI

echo "Process results"

echo "Generating Mabl specifications with no FI"

java -jar ../maestro-fi.jar import sg1 ../FaultInject.mabl multimodelNoFI.json simulation-config.json -output generateNoFI/

echo "Simulating specifications"

java -jar ../maestro-fi.jar interpret generateNoFI/spec.mabl ../FaultInject.mabl -output targetNoFI

echo "Process results"

python plot.py