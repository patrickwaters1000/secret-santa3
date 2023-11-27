#!/bin/bash

ROOT=$REPOS/secret-santa3/
JAR_NAME=secret-santa3-0.1.0-SNAPSHOT-standalone.jar

(cd $ROOT/front && npx webpack)

mv $ROOT/front/dist/main.js $ROOT/back/resources

(cd $ROOT/back && lein uberjar)

java -jar $ROOT/back/target/uberjar/$JAR_NAME \
     -n Patrick \
     -n Allen \
     -g 1
