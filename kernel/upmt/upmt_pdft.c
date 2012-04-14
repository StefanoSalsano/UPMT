/*
 * upmt_pdft.c
 *
 *  Created on: Nov 23, 2011
 *      Author: Fabio Patriarca
 */

/*#include "include/upmt.h"
#include "include/upmt_pdft.h"*/

#include "include/upmt_pdft.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"
#include "include/upmt_tunt.h"
#include "include/upmt_locks.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>


#define PDFT_BIT_HASH		16	/*min 8 - max 30*/
#define INITIAL_PDFT_DIM	(1<<PDFT_BIT_HASH)
u32 PDFT_PRIME_HASH;

struct pdft_table *pdft;

int pdft_create(){
	u32 i;

	pdft = (struct pdft_table *) kzalloc(sizeof(struct pdft_table), GFP_KERNEL);
	if(pdft == NULL){
		printk("Error - Unable to allocate memory for pdft table");
		return -1;
	}

	pdft->dim = INITIAL_PDFT_DIM;
	PDFT_PRIME_HASH = primes[PDFT_BIT_HASH - 8];  //choose the nearest good prime for a better distribution

	pdft->table = (struct pdft_entry **) vzalloc(sizeof(struct pdft_entry *) * pdft->dim);
	if(pdft->table == NULL){
		printk("Error - Unable to allocate memory for pdft table");
		return -1;
	}

	for(i=0; i<pdft->dim; i++){
		pdft->table[i] = NULL;
	}

	pdft->collisions = 0;
	pdft->overrides = 0;

	printk("UPMT - pdft table created");

	return 0;
}

void pdft_flush(){
	u32 i;
	struct pdft_entry *tmp;
	struct list_head *pos, *q;

	for(i=0; i<pdft->dim; i++){
		if(pdft->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &pdft->table[i]->list){
			tmp = list_entry(pos, struct pdft_entry, list);
			list_del(pos);
			kfree(tmp);
		}
	}

	pdft->dim = INITIAL_PDFT_DIM;
	for(i=0; i<pdft->dim; i++){
		pdft->table[i] = NULL;
	}
	pdft->collisions = 0;
	pdft->overrides = 0;
}

void pdft_erase(void){
	pdft_flush();
	vfree(pdft->table);
	kfree(pdft);
	printk("UPMT - pdft table erased");
}



/*
 *
 */

static u32 pdft_hash_from_key(u32 ip){
	u32 hash = ip * 13 * 29;
	hash = hash % PDFT_PRIME_HASH;
	hash = hash % pdft->dim;
	return hash;
}

/*
 *
 */

static struct pdft_entry * pdft_insert_entry(const struct pdft_entry *e){
	struct pdft_entry *tmp;
	u32 hash = pdft_hash_from_key(e->ip);

	if(pdft->table[hash] == NULL){
		pdft->table[hash] = (struct pdft_entry *) kzalloc(sizeof(struct pdft_entry), GFP_ATOMIC);
		if(pdft->table[hash] == NULL){
			printk("pdft_insert - Error - Unable to allocate memory for first new_entry");
			return NULL;
		}
		INIT_LIST_HEAD(&pdft->table[hash]->list);
	}

	// if key is found in the synonym list, update it
	list_for_each_entry(tmp, &pdft->table[hash]->list, list){
		//if(&e->ip == &tmp->ip){
		if(e->ip == tmp->ip){
			tmp->te = e->te;
			pdft->overrides++;
			return tmp;
		}
	}

	// otherwise add it
	tmp = (struct pdft_entry *) kzalloc(sizeof(struct pdft_entry), GFP_ATOMIC);
	if(tmp == NULL){
		printk("pdft_insert - Error - Unable to allocate memory for new_entry");
		return NULL;
	}

	tmp->ip = e->ip;
	tmp->te = e->te;

	list_add(&tmp->list, &pdft->table[hash]->list);
	return tmp;
}

struct pdft_entry * pdft_insert(u32 ip, struct tunt_entry *te){
	struct pdft_entry e;
	e.ip = ip;
	e.te = te;
	return pdft_insert_entry(&e);
}

struct pdft_entry * pdft_search(const u32 ip){
	struct pdft_entry *tmp;
	u32 hash = pdft_hash_from_key(ip);

	tmp = NULL;

	if(pdft->table[hash] == NULL) return NULL;

	list_for_each_entry(tmp, &pdft->table[hash]->list, list){
		if(ip == tmp->ip) return tmp;
	}
	return NULL;
}

struct tunt_entry * pdft_get_tun(const u32 ip){
	struct pdft_entry *e = pdft_search(ip);
	if(e == NULL) return NULL;
	else return e->te;
}

/*int pdft_delete(const u32 ip){
	return 0;
}*/

int pdft_delete(const u32 ip){
	struct pdft_entry *tmp;
	struct list_head *pos, *q;

	u32 hash = pdft_hash_from_key(ip);
	if(pdft->table[hash] == NULL) return -1;
	list_for_each_safe(pos, q, &pdft->table[hash]->list){
		 tmp = list_entry(pos, struct pdft_entry, list);
		 list_del(pos);
		 kfree(tmp);
		 return 0;
	}
	return -1;
}

int pdft_count(void){
	int N = 0;
	struct pdft_entry *tmp;
	u32 i;

	for(i=0; i<pdft->dim; i++){
		if(pdft->table[i] == NULL) continue;
		list_for_each_entry(tmp, &pdft->table[i]->list, list){
			N++;
		}
	}
	return N;
}

void pdft_fill_pointers(struct pdft_entry **pea){
	int N = 0;
	u32 i;
	struct pdft_entry *tmp;

	for(i=0; i<pdft->dim; i++){
		if(pdft->table[i] == NULL) continue;
		list_for_each_entry(tmp, &pdft->table[i]->list, list){
			pea[N] = tmp;
			N++;
		}
	}
}

MODULE_LICENSE("GPL");
