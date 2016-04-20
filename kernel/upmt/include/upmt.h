/*
 * upmt.h
 *
 *  Created on: 13/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_H_
#define UPMT_H_

#ifdef __KERNEL__
	#include <linux/version.h>
	#include <linux/kernel.h>
	#include <linux/if.h>
	#include <linux/slab.h>
	#include <linux/vmalloc.h>
#else
	#include <net/if.h>
#endif

/*JNI*/#ifdef CREATELIB
/*JNI*/#include <stdlib.h>
/*JNI*/int __len, aretlen;
/*JNI*/char* _ret;
/*JNI*/char buffer[10000];
/*JNI*/#endif

extern int AN;
extern struct net_device *upmt_dev;

extern struct nsproxy *upmtns; // Initializing namespace (Sander)

#ifdef ANDROID
	#define JAVA_APPNAME "com.and.gui"
#else
	#define JAVA_APPNAME "java"
#endif

extern unsigned int HEADROOM;
extern unsigned int PIGGYROOM;
extern unsigned int ADDROOM;
extern unsigned int IP_HEADROOM;
extern unsigned int UDP_HEADROOM;

#define UPMT_DEVICE_NAME "upmt0"
#define UPMT_DEVICE_NAME1 "upmt1"
#define UPMT_LINUX_VERSION_CODE 132640

#define UPMT_S_MARK 0x5432
#define NO_UPMT_MARK 0xfafafafa

#define TUN_CLIENT_MODE	1
#define TUN_SERVER_MODE	2

//http://planetmath.org/encyclopedia/GoodHashTablePrimes.html
static const unsigned int primes[] =  { 389, 769, 1543, 3079, 6151, 12289, 24593, 49157,
										98317, 196613, 393241, 786433, 1572869, 3145739,
										6291469, 12582917, 25165843, 50331653, 100663319,
										201326611, 402653189, 805306457, 1610612741 };

struct upmt_key{
	unsigned int proto:8;
	unsigned int saddr:32;
	unsigned int daddr:32;
	unsigned int dport:16;
	unsigned int sport:16;
};

struct tun_local {
	int ifindex; //XXX CHANGE - this can't be the if_index of the real network device. 
				 //let's use the mark (which is a unique id) and leave the same name
	unsigned int port:16;
};

struct tun_remote {
	unsigned int addr:32;
	unsigned int port:16;
};

struct inat {
	unsigned int local:32;
	unsigned int remote:32;
};

struct tun_param {
	int 				tid;
	struct tun_local 	tl;
	struct tun_remote 	tr;
	struct inat 		in;
	struct mark_dev 	*md;
};

struct keep_alive_info{
	unsigned long client_id:32;
	int client_tid:32;

	/****************/

	unsigned char Sc[3];
	unsigned char Rc[3];

	unsigned char Ss[3];
	unsigned char Rs[3];

	/***************/

	unsigned long pSc;
	unsigned long pRc;

	unsigned long pSs;
	unsigned long pRs;
};

struct keep_alive {
	int state;
	int toSend;
	int computeLOSS;

	unsigned long sent;
	unsigned long recv;
	unsigned long tstamp_sent;
	unsigned long tstamp_recv;

	//LOSS
	//Client
	//unsigned long pSc;
	//unsigned long pRc;
	unsigned long pSc_last;
	unsigned long pRs_last;
	unsigned long pSs_tmp;
	unsigned long pRc_tmp;

	//Server
	//unsigned long pSs;
	//unsigned long pRs;
	unsigned long pSc_tmp;
	unsigned long pRs_tmp;
	unsigned long pSs_last;
	unsigned long pRc_last;

	struct keep_alive_info info;
};

struct mark_dev {
	char iname[IFNAMSIZ];
	unsigned int mark:32;
};

#ifdef __KERNEL__
	extern u32 an_mark;
#endif

#define VERBOSE_MODE
extern int verbose;

/*
 * 0 - no verbose
 * 1 - print incoming/outgoing packets
 * 2 - print encapsulated/decapsulated packet
 * 3 - print inner part of encapsulated packets
 * 4 - print the packet after internal natting/denatting
 */

//#define PRINT_INTERRUPT_CONTEXT

#define vzalloc(size) __vmalloc((size), GFP_KERNEL | __GFP_HIGHMEM | __GFP_ZERO, PAGE_KERNEL);

#endif /* UPMT_H_ */
