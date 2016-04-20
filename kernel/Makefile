
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

KERNELDIR := /lib/modules/$(shell uname -r)/build
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
	sudo rm -rf /lib/modules/$(shell uname -r)/extra/upmt*
	$(MAKE) -C $(KERNELDIR) M=$(PWD) clean
	$(MAKE) -C upmt-conntracker/ plugins_clean

deinstall:
	sudo rm -rf /lib/modules/$(shell uname -r)/extra/upmt*
