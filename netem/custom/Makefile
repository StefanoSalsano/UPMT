
obj-m		:= sch_netem.o

KERNELDIR := /lib/modules/$(shell uname -r)/build
MODULEDIR := /lib/modules/$(shell uname -r)/kernel/net/sched/
PWD:= $(shell pwd)

modules:
	$(MAKE) -C $(KERNELDIR) M=$(PWD) modules

install:
	sudo cp sch_netem.ko $(MODULEDIR)
	sudo depmod -a

.PHONY: clean

clean:
	rm -rf *.o *~ core .depend .*.cmd *.ko *.mod.c *.markers *.symvers *.order .*.o.d .tmp_versions
