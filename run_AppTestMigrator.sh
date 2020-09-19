#!/bin/bash

sourceapp=$1
targetapp=$2

if [ ! -d "$sourceapp" ]; then
  echo "Source app does not exist!"
  exit 1
fi

if [ ! -d "$targetapp" ]; then
  echo "Target app does not exist!"
  exit 1
fi

root=$(pwd)

targetAppProjectName=${targetapp##*/}
if [ -z "$targetAppProjectName" ]; then
  targetAppProjectName=$(echo "$targetapp" | awk -F / '{ print $(NF-1) }')
fi

cd AppTestMigrator

echo "Running source app's tests and extracting scenarios from source app..."

javac -d bin -sourcepath src -cp "./lib/*" src/app/test/migrator/ExtractScenariosRunner.java

gradlepath=$(which gradle)
adbpath=$(which adb)

java -cp ./bin:./lib/* app.test.migrator.ExtractScenariosRunner $sourceapp $targetapp $gradlepath $adbpath > ../log.txt 2>&1

RESULT=$?

if [ $RESULT -eq 0 ]; then

  moduleName=$(grep -r "module name=" ../log.txt | cut -d= -f2-)

  cd $targetapp

  echo "" >> gradle.properties
  echo "org.gradle.jvmargs=-Xmx1536M" >> gradle.properties

  if [ -d $root/AppTestMigrator/source-scenarios ]; then

    mkdir -p ./$moduleName/src/main/assets; cp -r $root/AppTestMigrator/UIAutomator-UIHierarchies ./$moduleName/src/main/assets
    cp -r $root/AppTestMigrator/Espresso-UIHierarchies ./$moduleName/src/main/assets
    cp -r $root/AppTestMigrator/source-scenarios ./$moduleName/src/main/assets
    cp $root/AppTestMigrator/image_dict ./$moduleName/src/main/assets/
    cp $root/AppTestMigrator/inputType_dict ./$moduleName/src/main/assets/
    mkdir -p ./$moduleName/libs; cp -r $root/Matching/app/libs/* ./$moduleName/libs

    echo "Installing the target app"

    ./gradlew installDebug

    RESULT=$?

    if [ $RESULT -eq 0 ]; then
      apk=( $(find $targetapp/$moduleName/build/outputs -type f -name "*.apk" ! -name '*unaligned.apk' ! -name '*androidTest.apk') )
      apkpath=${apk[0]}
      activitytolaunch=`aapt dump badging $apkpath | grep -m 1 launchable-activity | awk -F' ' '{ print $2 }' | awk -F"'" '{print $2}' | awk -F"'" '{print $1}'`
      frompackage="app.test.migrator.matching"
      topackage=$(echo $activitytolaunch | awk 'BEGIN{FS=OFS="."} NF--')
      testApplicationId=( $(grep -r "testApplicationId" ./$moduleName/build.gradle | awk -F' ' '{ print $3 }' | sed 's/"//g') )
      applicationId=( $(grep -r "testApplicationId" $targetapp/$moduleName/build.gradle | awk -F' ' '{ print $3 }' | sed 's/"//g') )
      if [ -z "$applicationId" ]; then
        applicationId=( $(grep -r "applicationId" $targetapp/$moduleName/build.gradle | awk -F' ' '{ print $3 }' | sed 's/"//g') )
      fi
      resourcepackage=($(grep -r "applicationId" ./$moduleName/build.gradle | awk -F' ' '{ print $3 }' | sed 's/"//g' | sed "s/'/ /g"))

      echo "Generating static window transition graph for the target app..."

      if [ -f $root/gator-3.3/dbs/$targetAppProjectName/db.json ]; then
        cp $root/gator-3.3/dbs/$targetAppProjectName/db.json $root
      else
        cd $root/gator-3.3/AndroidBench
        python runGatorOnApk.py $apkpath -client WTGClient
        cp db.json $root
      fi

      dbpath=$root"/db.json"
      if [ -f $dbpath ]; then
        QUERY1="MATCH (n) DETACH DELETE n"
        QUERY2="call apoc.load.json('file:///$dbPath') YIELD value AS row WITH row, row.graph.nodes AS nodes UNWIND nodes AS node CALL apoc.create.node(node.labels, node.properties) YIELD node AS n SET n.id = node.id WITH row UNWIND row.graph.relationships AS rel MATCH (a) WHERE a.id = rel.startNode MATCH (b) WHERE b.id = rel.endNode CALL apoc.create.relationship(a, rel.type, rel.properties, b) YIELD rel AS r RETURN a,b,r"
        QUERY3="MATCH (a)-[r]->(b) WITH a, properties(r) as pr, collect(r) as rels, b WHERE size(rels) > 1 UNWIND tail(rels) as rel DELETE rel"
        POST_DATA="{\"statements\":[{\"statement\": \"$QUERY1\"},{\"statement\": \"$QUERY2\"},{\"statement\": \"$QUERY3\"}]}"
        curl -i -H 'accept:application/json' -H 'content-type:application/json' -H 'Authorization:Basic bmVvNGo6bmVvNGo' -d "$POST_DATA" -XPOST "http://localhost:7474/db/data/transaction/commit" > /dev/null
      fi

      echo "Matching Scenarios from source app to target app..."

      cd $targetapp

      mkdir -p ./$moduleName/src/androidTest; cp -r ./$moduleName/src/androidTest $root/AppTestMigrator/resources/androidTest

      packagepath="${topackage//.//}"
      androidtestpackage=./$moduleName/src/androidTest/java/$packagepath
      utilpath=$androidtestpackage/util
      matchingpath=$root/Matching/app/src/androidTest/java/app/test/migrator/matching

      #rm -r ./$moduleName/src/androidTest/*
      mkdir -p $androidtestpackage; cp -r $matchingpath/* $androidtestpackage
      mkdir -p $utilpath; cp -r $matchingpath/util/* $utilpath
      mkdir -p $utilpath/uiautomator; cp -r $matchingpath/util/uiautomator/* $utilpath/uiautomator
      find $androidtestpackage -type f -name "*.java" -exec sed -i '' 's;'"$frompackage"';'"$topackage"';' {} +

      RESULT=$?
      if [ $RESULT -eq 0 ]; then
        scenarios=( ./$moduleName/src/main/assets/source-scenarios/* )
        for scenario in "${scenarios[@]}"; do
          scenarioname="${scenario##*/}"
          if [[ ! $scenarioname =~ "EventMatching" && ! $scenarioname =~ "AssertionMatching" ]]; then
            scenarionumber=$((${scenarioname##*_} - 1))
            echo "************************test name: "$scenarioname
            cd $targetapp
            if [ $scenarionumber -gt 0 ]; then
	      echo "AppTestMigrator_${scenarioname%_*}_$scenarionumber.java"
	      find ./$moduleName/src/androidTest/ -name "AppTestMigrator_${scenarioname%_*}_$scenarionumber.java" -exec rm -f {} \;
            fi
            ./gradlew uninstallDebug
            rm -fr ./build
            rm -fr ./$moduleName/build
            ./gradlew installDebug
            ./gradlew InstallDebugAndroidTest
            adb shell am instrument -w -e arg $activitytolaunch -e module app -e scenario $scenarioname -e mode EventMatching $applicationId.test/android.support.test.runner.AndroidJUnitRunner
            cd $root/AppTestMigrator
            adb pull /sdcard/target-tests/ .
            cp -r $root/AppTestMigrator/target-tests $targetapp/$moduleName/src/main/assets
            while grep -q "nextEvent:" $root"/AppTestMigrator/target-tests/AppTestMigrator_"$scenarioname".txt"; do
              cd $targetapp
              ./gradlew uninstallDebug
              rm -fr ./build
              rm -fr ./$moduleName/build
              ./gradlew installDebug
              adb shell am instrument -w -e arg $activitytolaunch -e module app -e scenario $scenarioname -e mode EventMatching $applicationId.test/android.support.test.runner.AndroidJUnitRunner

              cd $root/AppTestMigrator
              adb pull /sdcard/target-tests/ .
              cp -r $root/AppTestMigrator/target-tests $targetapp/$moduleName/src/main/assets
            done
            RESULT=$?
            if [ $RESULT -eq 0 ]; then
              cd $root/AppTestMigrator
              echo "Generating events for scenario "$scenarioname
              adb pull /sdcard/target-tests/ .
              if [ -d "target-tests" ]; then
                javac -d bin -sourcepath src -cp "./lib/*" src/app/test/migrator/TestCodeGenerationRunner.java
                java -cp ./bin:./lib/* app.test.migrator.TestCodeGenerationRunner $targetapp $activitytolaunch $topackage $resourcepackage $scenarioname $adbpath "EventMatching"
              fi
              cd $targetapp
              mkdir -p ./$moduleName/src/main/assets/migrated-events; find ./$moduleName/src/androidTest/java/ -name 'AppTestMigrator_*.java' -exec cp {} ./$moduleName/src/main/assets/migrated-events \;
              ./gradlew uninstallDebug
              rm -fr ./build
              rm -fr ./$moduleName/build
              ./gradlew installDebug	
	      find ./$moduleName/src/androidTest/ -name "AppTestMigrator_$scenarioname.java" -exec rm -f {} \;
              ./gradlew InstallDebugAndroidTest
              adb shell am instrument -w -e arg $activitytolaunch -e module app -e scenario $scenarioname -e mode AssertionMatching $applicationId.test/android.support.test.runner.AndroidJUnitRunner
              RESULT=$?
              if [ $RESULT -eq 0 ]; then
                cd $root/AppTestMigrator
                echo "Generating assertions for scenario "$scenarioname
                adb pull /sdcard/target-tests/ .
                if [ -d "target-tests" ]; then
                  javac -d bin -sourcepath src -cp "./lib/*" src/app/test/migrator/TestCodeGenerationRunner.java
                  java -cp ./bin:./lib/* app.test.migrator.TestCodeGenerationRunner $targetapp $activitytolaunch $topackage $resourcepackage $scenarioname $adbpath "AssertionMatching"
                fi
              fi
            fi
          fi
        done

        RESULT=$?
        if [ $RESULT -eq 0 ]; then
          echo "Cleaning up..."

          cd $targetapp
          ./gradlew uninstallDebug
          eventMatchingPath=( $(find ./$moduleName/src/androidTest -type f -name "EventMatching.java") )
          rm -r $eventMatchingPath
          assertionMatchingPath=( $(find ./$moduleName/src/androidTest -type f -name "AssertionMatching.java") )
          rm -r $assertionMatchingPath
          utilPath=( $(find ./$moduleName/src/androidTest -type d -name "util") )
          rm -r $utilPath
          rsync --remove-source-files -av $root/AppTestMigrator/resources/androidTest ./$moduleName/src/
          rm -r ./$moduleName/src/main/assets/Espresso-UIHierarchies
          rm -r ./$moduleName/src/main/assets/UIAutomator-UIHierarchies
          rm -r ./$moduleName/src/main/assets/source-scenarios
          rm -r ./$moduleName/src/main/assets/migrated-events
          rm -r ./$moduleName/src/main/assets/target-tests
          rm -f ./$moduleName/src/main/assets/image_dict
          rm -f ./$moduleName/src/main/assets/inputType_dict
          rm -r $root/AppTestMigrator/resources/androidTest 
          rm -r $root/AppTestMigrator/resources/AndroidManifest.xml
          rm -r $root/AppTestMigrator/resources/build.gradle

          cd $root/AppTestMigrator

          rm -f $root/log.txt
          rm -f $root/db.json
          rm -f $root/AppTestMigrator/log
          rm -f $root/AppTestMigrator/image_dict
          rm -f $root/AppTestMigrator/inputType_dict
          rm -fr $root/AppTestMigrator/screenshots
          rm -fr $root/AppTestMigrator/source-scenarios
          rm -fr $root/AppTestMigrator/target-tests
          rm -fr $root/AppTestMigrator/TargetAppUIHierarchies
          rm -fr $root/AppTestMigrator/UIAutomator-UIHierarchies
          rm -fr $root/AppTestMigrator/Espresso-UIHierarchies

          adb shell rm -r /sdcard/log
          adb shell rm -r /sdcard/UIAutomator-UIHierarchies
          adb shell rm -r /sdcard/Espresso-UIHierarchies
          adb shell rm -r /sdcard/TargetAppUIHierarchies
          adb shell rm -r /sdcard/target-tests
          adb shell rm -r /sdcard/screenshots
        else
          echo "Unable to run instrumentation tests for the source app. Please check the log file for more information."
        fi
      else
        echo "Unable to install AppTestMigrator."
      fi
    else
      echo "Unable to install the target app."
    fi
  else
    echo "Unable to find scenarios for the target app."
  fi
else
  echo "Unable to run the source app's tests. For more information, check the log file."
fi
