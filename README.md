# AppTestMigrator

A tool for automated test migration between mobile apps with similar functionality. The paper for the tool is available [here](https://ieeexplore.ieee.org/document/8952387).

## Requirements

Before running AppTestMigrator, you need to have Word2Vec and Neo4j be up and running.

To run Word2Vec as a local server, use the following command:
- python run_word2vec.py -d

To run Neo4j, you need to first install it from [here](https://neo4j.com/download/) (for experiments, we used Neo4j Desktop Version 1.0.14). You need to create an empty database and we assume the username and password are the default values, which both are "neo4j".

## Getting started

Now you should be able to run AppTestMigrator using the following command (make sure the apps exist under AppTestMigrator directory):
- ./run_AppTestMigrator.sh absolutePathToSourceApp absolutePathToTargetApp

## Experiments

You can download the apps and the tests we used for our experiments from [here](https://drive.google.com/file/d/1JaIeECKdEpClokDnDNV2miM3Ygpb7c0i/view). 

The experiments were ran using Genymotion 2.11.0 emulator with 8 processors and 16384MB base memory. Before running the experiments, please apply the following changes to the emulator to mitigate flakiness nature of the tests:
- Settings -> Developer options -> Window animation scale -> Animation off
- Settings -> Developer options -> Transition animation scale -> Animation off
- Settings -> Developer options -> Animator duration scale -> Animation off
- Settings -> Accessibility -> Touch & hold delay -> Long
- Settings -> Languages & input -> Spell checker -> Off

We assume that the apps support the following gradle tasks and the tests for the apps are running successfully:
- ./gradlew installDebug
- ./gradlew installDebugAndroidTest
- ./gradlew uninstallDebug
