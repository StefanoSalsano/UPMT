
EXTRA_CFLAGS := #-DUPMT_S #-DRME

obj-m		:= upmt.o
upmt-objs 	:= upmt_main.o upmt_stamp.o upmt_util.o upmt_tunt.o upmt_paft.o upmt_tsa.o upmt_genl.o upmt_netfilter.o upmt_dev.o upmt_encap.o upmt_locks.o upmt_spl.o upmt_mdl.o upmt_s.o upmt_pdft.o

KERNELDIR := /lib/modules/$(shell uname -r)/build
PWD:= $(shell pwd)

modules:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) modules

rme:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) modules EXTRA_CFLAGS=-DRME

install:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) modules_install;

.PHONY: clean

clean:
	rm -rf *.o *~ core .depend .*.cmd *.ko *.mod.c *.markers *.symvers *.order .*.o.d .tmp_versions
