#!/bin/bash

R_IP=$2
IFACE=$1
 
OUT_L_PORT=50000
OUT_R_PORT=60000

L_VIP=1.2.3.4
R_VIP=1.2.3.5

IN_L_PORT=5000
IN_R_PORT=6000

UPMT_K_HOME=../../../kernel/upmt

USAGE="upmtconf iface remote_real_ip" 


if [ $# -ne 2 ];
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

echo "adding tun..."
upmtconf -a tun -i $IFACE -d $R_IP -l $OUT_L_PORT -r $OUT_R_PORT -n 1

echo "adding rule..."
upmtconf -a rule -p tcp -s $L_VIP -d $R_VIP -l $IN_L_PORT -r $IN_R_PORT -n 1

echo "done"
