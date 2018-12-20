#!/usr/bin/env bash

PASSWORD=$1
USER=$2
IP=$3

sshpass -p ${PASSWORD} ssh -o StrictHostKeyChecking=no ${USER}@${IP}
    "service iofog-agent stop"
shpass -p ${PASSWORD} scp -o StrictHostKeyChecking=no daemon/target/iofog-agent-daemon-jar-with-dependencies.jar
    ${USER}@${IP}:/usr/bin/iofog-agentd.jar
sshpass -p ${PASSWORD} scp -o StrictHostKeyChecking=no client/target/iofog-agent-client-jar-with-dependencies.jar
    $${USER}@${IP}:/usr/bin/iofog-agent.jar
sshpass -p ${PASSWORD} ssh -o StrictHostKeyChecking=no ${USER}@${IP}
    "service iofog-agent start"