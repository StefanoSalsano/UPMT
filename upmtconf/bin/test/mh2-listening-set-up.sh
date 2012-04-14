#!/bin/bash

IFACE=$1
 
OUT_R_PORT=50000
OUT_L_PORT=60000

R_VIP=1.2.3.4
L_VIP=1.2.3.5

IN_R_PORT=50000
IN_L_PORT=60000

UPMT_K_HOME=../../../kernel/upmt
USAGE="./mh2-listening-set-up.sh iface" 


if [ $# -ne 1 ];
	then echo $USAGE; 
	exit 1;
fi

echo "checking if upmt.ko is loaded..."
if "lsmod | grep upmt";
	then echo "module found";
else 
	echo "loading module";
	sudo insmod $UPMT_K_HOME/upmt.ko;
fi
	

echo "configuring upmt0..."
sudo ifconfig upmt0 $L_VIP

echo "adding tsa..."
upmtconf -a tsa -i $IFACE -l $OUT_L_PORT 

echo "done"
