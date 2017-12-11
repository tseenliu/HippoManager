#!/bin/bash

# set ENV
ENV=local
if [ -z ${1+x} ]; then
echo "ENV is set to '$ENV'";
else
ENV=$1
echo "ENV is set to '$1'";
fi

APP_HOME="$(cd "`dirname "$0"`"/..; pwd)"
nohup ${APP_HOME}/bin/boot coordinator ${APP_HOME}/config/${ENV} > ${APP_HOME}/log/coordinator.log 2>&1 &
