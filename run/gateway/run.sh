#!/bin/sh

GATEWAY_HOME=../../ee-gateway

APP_HOME=../../simple-chat-app

#################################

SCRIPTPATH=$( cd $(dirname $0) ; pwd -P )

GATEWAY_HOME=${SCRIPTPATH}/${GATEWAY_HOME}

APP_HOME=${SCRIPTPATH}/${APP_HOME}

export GATEWAY_OPTS="-DGATEWAY_CONFIG_DIRECTORY=${SCRIPTPATH}/conf -DGATEWAY_CONFIG=${SCRIPTPATH}/conf/simple-chat-gateway-config.xml -DGATEWAY_LOG_DIRECTORY=${SCRIPTPATH}/log -DGATEWAY_WEB_DIRECTORY=${APP_HOME}"

${GATEWAY_HOME}/bin/gateway.start