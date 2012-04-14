/*
 * upmt_stamp.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_stamp.h"

#ifdef __KERNEL__
	#include <linux/kernel.h>
	#include <linux/module.h>
	#include <linux/ip.h>
#else
	#include <stdio.h>
#endif

/*JNI*/#ifdef CREATELIB
/*JNI*/#include <string.h>
/*JNI*/#define printf(...) do{ sprintf(buffer,__VA_ARGS__); __len = strnlen(buffer, 10000); if (__len > 0) { aretlen = aretlen + __len; _ret = realloc(_ret, aretlen); strncat(_ret, buffer, __len);} } while(0)
/*JNI*/#define exit(x) do{} while(0)
/*JNI*/#endif

void print_ip_address(unsigned int addr){
	#ifdef __KERNEL__
		printk("%d.", ((unsigned char *)&addr)[0]);
		printk("%d.", ((unsigned char *)&addr)[1]);
		printk("%d.", ((unsigned char *)&addr)[2]);
		printk("%d",  ((unsigned char *)&addr)[3]);
	#else
		printf("%d.", ((unsigned char *)&addr)[0]);
		printf("%d.", ((unsigned char *)&addr)[1]);
		printf("%d.", ((unsigned char *)&addr)[2]);
		printf("%d",  ((unsigned char *)&addr)[3]);
	#endif
}

void print_upmt_key(const struct upmt_key *k){
	#ifdef __KERNEL__
		printk("\n\t proto:\t %d", k->proto);
		//printk("\n\t saddr:\t %d.%d.%d.%d", NIPQUAD(k->saddr));
		//printk("\n\t daddr:\t %d.%d.%d.%d", NIPQUAD(k->daddr));
		printk("\n\t saddr:\t "); print_ip_address(k->saddr);
		printk("\n\t daddr:\t "); print_ip_address(k->daddr);
		printk("\n\t sport:\t %d", k->sport);
		printk("\n\t dport:\t %d", k->dport);
	#else
		printf("\n\t proto:\t %d", k->proto);
		printf("\n\t saddr:\t "); print_ip_address(k->saddr);
		printf("\n\t daddr:\t "); print_ip_address(k->daddr);
		printf("\n\t sport:\t %d", k->sport);
		printf("\n\t dport:\t %d", k->dport);
	#endif
}

void print_upmt_tun_id(unsigned int tid){
	#ifdef __KERNEL__
		printk("\n\t TID:\t\t%d", tid);
	#else
		printf("\n\t TID:\t\t%d", tid);
	#endif

}

void print_upmt_tun_local(const struct tun_local *a){
	#ifdef __KERNEL__
		printk("\n\t ifin:\t\t%d", a->ifindex);
		printk("\n\t port:\t\t%u", a->port);
	#else
		printf("\n\t ifin:\t\t%d", a->ifindex);
		printf("\n\t port:\t\t%u", a->port);
	#endif
}

void print_upmt_tun_remote(const struct tun_remote *a){
	#ifdef __KERNEL__
		//printk("\n\t addr:\t %d.%d.%d.%d", NIPQUAD(a->addr));
		printk("\n\t addr:\t\t"); print_ip_address(a->addr);
		printk("\n\t port:\t\t%u", a->port);
	#else
		printf("\n\t addr:\t\t"); print_ip_address(a->addr);
		printf("\n\t port:\t\t%u", a->port);
	#endif
}

void print_upmt_inat(const struct inat *a){
	#ifdef __KERNEL__
		//printk("\n\t addr:\t %d.%d.%d.%d", NIPQUAD(a->addr));
		printk("\n\t local: \t\t"); print_ip_address(a->local);
		printk("\n\t remote:\t\t"); print_ip_address(a->remote);
	#else
		printf("\n\t local: \t\t"); print_ip_address(a->local);
		printf("\n\t remote:\t\t"); print_ip_address(a->remote);
	#endif
}

static void print_tid(int tid){
	#ifdef __KERNEL__
		printk("\n\t TID:\t\t%u", tid);
	#else
		printf("\n\t TID:\t\t%u", tid);
	#endif
}

void print_upmt_tun_param(const struct tun_param *tr){
	print_tid(tr->tid);
	print_upmt_tun_local(&tr->tl);
	print_upmt_tun_remote(&tr->tr);
	print_upmt_inat(&tr->in);
}

#ifdef __KERNEL__
MODULE_LICENSE("GPL");
#endif
