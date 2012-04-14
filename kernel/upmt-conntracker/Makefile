
.PHONY: plugins plugins_install plugins_clean

obj-m		:= xt_UPMT.o xt_UPMT_ex.o compat_xtables.o

plugins:
	@echo -e ":: Building upmt-conntracker userspace iptables plugin\n"
	
	gcc -Wall -fPIC -o libxt_UPMT.o -c libxt_UPMT.c
	gcc -Wall -shared -o libxt_UPMT.so libxt_UPMT.o
	
plugins_install:
	install -m 0755 libxt_UPMT.so `pkg-config --variable=xtlibdir xtables`

plugins_clean:
	rm -f libxt_UPMT.{o,so}
