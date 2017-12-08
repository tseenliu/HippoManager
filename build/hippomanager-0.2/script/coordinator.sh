#!/bin/bash

APP_HOME="$(cd "`dirname "$0"`"/..; pwd)"
nohup ${APP_HOME}/bin/boot coordinator ${APP_HOME}/config > ${APP_HOME}/log/coordinator.log 2>&1 &
