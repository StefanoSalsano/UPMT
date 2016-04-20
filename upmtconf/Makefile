##############################################
# EDIT THESE VARIABLES ACCORDING TO YOUR CONFIGURATION:

# if you want to build UPMT just type 'make'
# if you want to build UPMT for Android you have to define compiler just type 'make CC="arm-none-linux-gnueabi-gcc" for example'

##############################################

CC := arm-none-linux-gnueabi-gcc
CC := gcc

default:
	$(CC) -o upmtconf upmtconf.c upmt_user.c ../kernel/upmt/upmt_stamp.c -I../kernel/upmt/include -I. 

install:
	install -m 0755 upmtconf /usr/bin/

clean:
	rm -f *.o upmtconf
