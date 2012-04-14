
default:
	gcc -o upmtconf upmtconf.c upmt_user.c ../kernel/upmt/upmt_stamp.c -I../kernel/upmt/include -I. 

install:
	install -m 0755 upmtconf /usr/bin/

clean:
	rm -f *.o upmtconf
