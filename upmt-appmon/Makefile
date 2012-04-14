
default:
	gcc -Wall -o upmt-appmon upmt-appmon.c cJSON.c -I../kernel/upmt -lm

install:
	install -m 0755 upmt-appmon /usr/bin/

clean:
	rm -f *.o upmt-appmon

remove:
	rm -f /usr/bin/upmt-appmon
