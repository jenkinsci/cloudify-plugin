#!/bin/bash -e

JAR_PATH=../target/jetty/webapp/WEB-INF/lib/cli-2.222.1.jar
ENDPOINT=http://local-jenkins:8080/jenkins

UPDATE_LIST=$( java -jar ${JAR_PATH} -s ${ENDPOINT} list-plugins | grep -e ')$' | awk '{ print $1 }' )

if [ ! -z "${UPDATE_LIST}" ]; then 
    echo Updating Jenkins Plugins: ${UPDATE_LIST}
    java -jar ${JAR_PATH} -s ${ENDPOINT} install-plugin ${UPDATE_LIST}
    java -jar ${JAR_PATH} -s ${ENDPOINT} safe-restart
fi
