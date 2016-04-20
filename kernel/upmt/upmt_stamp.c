/*
 * upmt_stamp.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_stamp.h"
//#include "include/upmt_conntracker_appmon.h"

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
		printk("\t proto:\t %d", k->proto);
		//printk("\n\t saddr:\t %d.%d.%d.%d", NIPQUAD(k->saddr));
		//printk("\n\t daddr:\t %d.%d.%d.%d", NIPQUAD(k->daddr));
		printk("\n\t saddr:\t "); print_ip_address(k->saddr);
		printk("\n\t daddr:\t "); print_ip_address(k->daddr);
		printk("\n\t sport:\t %d", k->sport);
		printk("\n\t dport:\t %d", k->dport);
		printk("\n");
	#else
		printf("\t proto:\t %d", k->proto);
		printf("\n\t saddr:\t "); print_ip_address(k->saddr);
		printf("\n\t daddr:\t "); print_ip_address(k->daddr);
		printf("\n\t sport:\t %d", k->sport);
		printf("\n\t dport:\t %d", k->dport);
		printf("\n");
	#endif
}

void print_upmt_key_line(const struct upmt_key *k){
	#ifdef __KERNEL__
		printk("proto: %d", k->proto);
		printk(" | saddr: "); print_ip_address(k->saddr);
		printk(" | daddr: "); print_ip_address(k->daddr);
		printk(" | sport: %u", k->sport);
		printk(" | dport: %u", k->dport);
		printk("\n");
	#else
		printf("proto: %d", k->proto);
		printf(" | saddr: "); print_ip_address(k->saddr);
		printf(" | daddr: "); print_ip_address(k->daddr);
		printf(" | sport: %u", k->sport);
		printf(" | dport: %u", k->dport);
		printf("\n");
	#endif
}

/*void print_info_tun_data_line(const struct info_tun_data *k){
	#ifdef __KERNEL__
		printk("tid: %d", k->tid);
		printk(" | ifname: %s", k->ifname);
		printk(" | daddr: "); print_ip_address(k->daddr);
		printk(" | rtt: %lu", k->rtt);
		printk(" | loss: %lu", k->loss);
		printk(" | ewmartt: %lu", k->ewmartt);
		printk(" | ewmaloss: %lu", k->ewmaloss);
		printk("\n");
	#else
		printf("tid: %d", k->tid);
		printf(" | ifname: %s", k->ifname);
		printf(" | daddr: "); print_ip_address(k->daddr);
		printf(" | rtt: %lu", k->rtt);
		printf(" | loss: %lu", k->loss);
		printf(" | ewmartt: %lu", k->ewmartt);
		printf(" | ewmaloss: %lu", k->ewmaloss);
		printf("\n");
	#endif
}*/

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

void print_upmt_tun_param_line(const struct tun_param *tr){
	#ifdef __KERNEL__
		printk("TID %d", tr->tid);
		printk(" | ifin: %d", tr->tl.ifindex);
		printk(" | sport: %u", tr->tl.port);
		printk(" | daddr: "); print_ip_address(tr->tr.addr);
		printk(" | dport: %u", tr->tr.port);
		printk("\n");
	#else
		printf("TID %d", tr->tid);
		printf(" | ifin: %d", tr->tl.ifindex);
		printf(" | sport: %u", tr->tl.port);
		printf(" | daddr: "); print_ip_address(tr->tr.addr);
		printf(" | dport: %u", tr->tr.port);
		printf("\n");
#endif
}

#ifdef __KERNEL__
MODULE_LICENSE("GPL");
#endif
