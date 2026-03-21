#!/bin/bash
set -e
echo "Waiting for master..."
until mysql -h mysql-master -u root -p050612 -e "SELECT 1" 2>/dev/null; do
  sleep 2
done
echo "Master ready, configuring replication..."
MASTER_STATUS=$(mysql -h mysql-master -u root -p050612 -N -e "SHOW MASTER STATUS")
LOG_FILE=$(echo $MASTER_STATUS | awk '{print $1}')
LOG_POS=$(echo $MASTER_STATUS | awk '{print $2}')
mysql -u root -p050612 -e "
  CHANGE MASTER TO
    MASTER_HOST='mysql-master',
    MASTER_USER='repl',
    MASTER_PASSWORD='repl',
    MASTER_LOG_FILE='$LOG_FILE',
    MASTER_LOG_POS=$LOG_POS;
  START SLAVE;
"
echo "Slave replication started."
