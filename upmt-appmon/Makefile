##############################################
# EDIT THESE VARIABLES ACCORDING TO YOUR CONFIGURATION:

# if you want to build UPMT just type 'make'
# if you want to build UPMT for Android you have to define compiler just type 'make CC="arm-none-linux-gnueabi-gcc" for example'

##############################################

CC := arm-none-linux-gnueabi-gcc
CC := gcc

default:
	$(CC) -Wall -o upmt-appmon upmt-appmon.c cJSON.c -I../kernel/upmt -lm

install:
	install -m 0755 upmt-appmon /usr/bin/

clean:
	rm -f *.o upmt-appmon

remove:
	rm -f /usr/bin/upmt-appmon
