/*
 * upmt_pdft.h
 *
 *  Created on: Nov 23, 2011
 *      Author: Fabio Patriarca
 */

#ifndef UPMT_PDFT_H_
#define UPMT_PDFT_H_

#include "upmt.h"
#include <linux/kernel.h> //this is for the u16 and u32 type
#include <linux/list.h>

extern struct pdft_table *pdft;

struct pdft_entry {
	u32 ip;
	struct tunt_entry *te;
	struct list_head list;
};

struct pdft_table {
	u32 dim;
	u32 collisions;
	u32 overrides;
	struct pdft_entry **table;
};

int pdft_create(void);
void pdft_flush(void);
void pdft_erase(void);

struct pdft_entry * pdft_insert(u32, struct tunt_entry *);

struct pdft_entry * pdft_search(const u32);
struct tunt_entry *	pdft_get_tun(const u32);

int pdft_delete(const u32);

int pdft_count(void);
void pdft_fill_pointers(struct pdft_entry **);

#endif /* UPMT_PDFT_H_ */
