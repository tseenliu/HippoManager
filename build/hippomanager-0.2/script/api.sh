#!/bin/bash

APP_HOME="$(cd "`dirname "$0"`"/..; pwd)"
nohup ${APP_HOME}/bin/boot api ${APP_HOME}/config > ${APP_HOME}/log/api.log 2>&1 &
