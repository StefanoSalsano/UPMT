
##############################################
# EDIT THESE VARIABLES ACCORDING TO YOUR CONFIGURATION:

# if you want to build UPMT with security extensions, decomment the following line
#EXTRA_CFLAGS := -DUPMT_S

# if you want to build UPMT with rme extensions, decomment the following line
#EXTRA_CFLAGS := -DRME
##############################################

obj-y := upmt/ upmt-conntracker/

EXTRA_CFLAGS="-I $(PWD)/upmt/include"
INCLUDES="-I $(PWD)/upmt/include"

KERNELDIR := /lib/modules/2.6.35.4-upmt/build
PWD:= $(shell pwd)

.PHONY: default install clean

default:
	$(MAKE) -C $(KERNELDIR) EXTRA_CFLAGS=${INCLUDES} M=$(PWD) modules
	$(MAKE) -C upmt-conntracker/ plugins

install:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) modules_install
	$(MAKE) -C upmt-conntracker/ plugins_install
	depmod -a

clean:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) clean
	$(MAKE) -C upmt-conntracker/ plugins_clean
