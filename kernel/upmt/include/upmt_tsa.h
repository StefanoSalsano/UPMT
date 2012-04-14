/*
 * upmt_tsa.h
 *
 *  Created on: 13/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_TSA_H_
#define UPMT_TSA_H_

#include "upmt.h"
#include <linux/kernel.h> 		//this is for the u16 and u32 type
#include <linux/list.h>

struct tsa_entry{
	struct tun_local tl;
	struct list_head list;
};

extern struct tsa_entry *tsa;

struct tsa_entry * tsa_insert(struct tun_local *);
struct tsa_entry * tsa_search(struct tun_local *);
int tsa_delete(struct tun_local *);
struct socket * tsa_delete_by_ifindex(int);
int tsa_count(void);
void tsa_fill_pointers(struct tun_local **);
int tsa_create(void);
void tsa_erase(void);

#endif /* UPMT_TSA_H_ */
