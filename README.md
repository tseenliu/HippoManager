# Hippo Manager
* This project aims to manage data intensive microservice effectively

## Packaging
```
        ./scripts/package.sh 0.2
```
Package file will be built to dist

## Config
* cluster.conf - for akka cluster
* coordinator.conf - for coordinator persistence
* reporter.conf - kafka setting
* service.conf - api & coordinator settings

## How to start
* unzip hippomanager-x.x.zip

### Start Coordinator
* ./bin/boot coordinator ${PWD}/config

### Start API
* ./bin/boot api ${PWD}/config