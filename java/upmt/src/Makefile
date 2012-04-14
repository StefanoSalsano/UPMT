#IMPORTANT!!!!!
#Check that environment variable $JAVA_HOME is correctly set
#Run "make" as root

LIBNAME		:=upmtconf
CLASS		:=upmt.os.Module
JNIPATH		:=/usr/lib/

CLASSh		:=$(subst .,_,$(CLASS)).h
CLASSpath	:=$(subst .,/,$(CLASS))
CC			:= gcc

UPMTCONF	:=../../../upmtconf/
UPMT		:=../../../kernel/upmt/

default:
	javac ./$(CLASSpath).java
	javah -jni $(CLASS)
	$(CC) -o lib$(LIBNAME).so -shared -Wl,-soname,lib$(LIBNAME).so -I . -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/linux -I $(UPMT)/include  $(UPMTCONF)/upmtconf.c  $(UPMTCONF)/upmt_user.c $(UPMT)/upmt_stamp.c -static -lc -DCREATELIB

install:
	install -m 0755 lib$(LIBNAME).so $(JNIPATH)

clean:
	rm -rf $(CLASSh)
	rm -rf ./$(CLASSpath).class
	rm -rf lib$(LIBNAME).so

remove:
	rm -rf $(JNIPATH)/lib$(LIBNAME).so

