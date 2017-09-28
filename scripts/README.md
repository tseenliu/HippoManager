# HIPPO Manager

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