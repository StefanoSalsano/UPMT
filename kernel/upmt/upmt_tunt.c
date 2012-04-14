/*
 * upmt_tunt.c
 *
 *  Created on: 14/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_tunt.h"
#include "include/upmt_paft.h"
#include "include/upmt_util.h"
#include "include/upmt_stamp.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/slab.h>

u8 TUNT_BIT_HASH;
u32 TUNT_PRIME_HASH;

struct tunt_table *tunt;
struct tunt_entry *default_tunnel;

struct tunt_entry * tunt_set_default(struct tun_param *tp){
	tp->tid = 0;
	if(default_tunnel == NULL) default_tunnel = (struct tunt_entry *) kzalloc(sizeof(struct tunt_entry), GFP_ATOMIC);
	if(default_tunnel == NULL){
		printk("\n error - tunt_set_default - unable to alloc memory");
		return NULL;
	}
	tun_param_copy(&default_tunnel->tp, tp);
	return default_tunnel;
}

int tunt_del_default(){
	if(default_tunnel == NULL) return -1;
	kfree(default_tunnel);
	default_tunnel = NULL;
	return 0;
}

int is_same_tun(const struct tun_param *a, const struct tun_param *b){

	if(umpt_tun_local_equals(&a->tl, &b->tl) != 0) return 1;
	if(umpt_tun_remote_equals(&a->tr, &b->tr) != 0) return 1;
	return 0;

	/*int res;
	printk("\n\n ------------------------------------ ");
	print_upmt_tun_param(a);
	print_upmt_tun_param(b);

	res = memcmp(&a->tl, &b->tl, (sizeof(struct tun_local)));
	printk("\n\t ---> RES1: %d ", res);

	res = memcmp(&a->tr, &b->tr, (sizeof(struct tun_remote)));
	printk("\n\t ---> RES2: %d ", res);

	if(memcmp(&a->tl, &b->tl, (sizeof(struct tun_local))) != 0) return 1;
	return memcmp(&a->tr, &b->tr, (sizeof(struct tun_remote)));*/
}

static int get_new_tid(void){
	u32 i;
	for(i=1; i<tunt->dim+1; i++){
		if(tunt->tid_trace[i] == 0){
			tunt->tid_trace[i] = 1;
			return i;
		}
	}
	return -1;
}

static int is_available_tid(int tid){
	if(tid <= 0) 		return -1;
	if(tid > tunt->dim) return -1;
	if(tunt->tid_trace[tid] == 0) return 0;
	else return -1;
}

static u32 tunt_hash_from_key(const struct tun_local *l){
	u32 hash = (l->port * 13 * 29) + l->ifindex;
	hash = hash % TUNT_PRIME_HASH;
	hash = hash % tunt->dim;
	return hash;
}

static struct tunt_entry * tunt_insert_entry(const struct tun_param *tp){
	int tid;
	struct tunt_entry *tmp;
	u32 hash = tunt_hash_from_key(&tp->tl);

	if(tunt->table[hash] == NULL){
		tunt->table[hash] = (struct tunt_entry *) kzalloc(sizeof(struct tunt_entry), GFP_ATOMIC);
		if(tunt->table[hash] == NULL){
			printk("tunt_insert - Error - Unable to allocating memory for first new_entry");
			return NULL;
		}
		INIT_LIST_HEAD(&tunt->table[hash]->list);
	}

	list_for_each_entry(tmp, &tunt->table[hash]->list, list){
		if(is_same_tun(tp, &tmp->tp) == 0){
			tid = tmp->tp.tid;
			tun_param_copy(&tmp->tp, tp);
			tmp->tp.tid = tid;
			if(tp->tid != tid) tunt->tid_trace[tp->tid] = 0;
			tunt->overrides++;
			return tmp;
		}
	}

	tmp = (struct tunt_entry *) kzalloc(sizeof(struct tunt_entry), GFP_ATOMIC);
	if(tmp == NULL){
		printk("tunt_insert - Error - Unable to allocating memory for new_entry");
		return NULL;
	}
	tun_param_copy(&tmp->tp, tp);
	list_add(&tmp->list, &tunt->table[hash]->list);
	//tunt->collisions++;
	return tmp;
}

struct tunt_entry * tunt_insert(struct tun_param *tp, int tid){
	if(is_available_tid(tid) < 0) 	tid = get_new_tid();
	tunt->tid_trace[tid] = 1;
	tp->tid = tid;

	return tunt_insert_entry(tp);
}

struct tunt_entry * tunt_search(const struct tun_param *tp){
	struct tunt_entry *tmp;
	u32 hash = tunt_hash_from_key(&tp->tl);

	if(tunt->table[hash] == NULL) return NULL;

	list_for_each_entry(tmp, &tunt->table[hash]->list, list){
		if(is_same_tun(tp, &tmp->tp) == 0) return tmp;
	}
	return NULL;
}

struct tunt_entry * tunt_search_by_tid(const int tid){
	struct tunt_entry *tmp;
	u32 i;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_entry(tmp, &tunt->table[i]->list, list){
			if(tmp->tp.tid == tid) return tmp;
		}
	}
	return NULL;
}

int tunt_delete(const struct tun_param *tp){
	struct tunt_entry *tmp;
	struct list_head *pos, *q;

	u32 hash = tunt_hash_from_key(&tp->tl);
	if(tunt->table[hash] == NULL) return -1;
	list_for_each_safe(pos, q, &tunt->table[hash]->list){
		tmp = list_entry(pos, struct tunt_entry, list);
		if(is_same_tun(tp, &tmp->tp) == 0){
			paft_delete_by_tun(tmp);
			tunt->tid_trace[tmp->tp.tid] = 0;
			list_del(pos);
			kfree(tmp);
			return 0;
		 }
	}
	return -1;
}

void tunt_delete_by_tsa(const struct tun_local *tl){
	struct tunt_entry *tmp;
	struct list_head *pos, *q;

	u32 hash = tunt_hash_from_key(tl);
	if(tunt->table[hash] == NULL) return;
	list_for_each_safe(pos, q, &tunt->table[hash]->list){
		tmp = list_entry(pos, struct tunt_entry, list);
		if(umpt_tun_local_equals(tl, &tmp->tp.tl) == 0){
			paft_delete_by_tun(tmp);
			tunt->tid_trace[tmp->tp.tid] = 0;
			list_del(pos);
			kfree(tmp);
		 }
	}
}

int tunt_delete_by_tid(const int tid){
	struct tunt_entry *tmp;
	struct list_head *pos, *q;
	u32 i;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &tunt->table[i]->list){
			tmp = list_entry(pos, struct tunt_entry, list);
			if(tmp->tp.tid == tid){
				paft_delete_by_tun(tmp);
				tunt->tid_trace[tmp->tp.tid] = 0;
				list_del(pos);
				kfree(tmp);
				return 0;
			}
		}
	}
	return -1;
}

void tunt_delete_by_ifindex(const int ifindex){
	struct tunt_entry *tmp;
	struct list_head *pos, *q;
	u32 i;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &tunt->table[i]->list){
			tmp = list_entry(pos, struct tunt_entry, list);
			//if(tmp->tp.tid == tid){
			if(tmp->tp.tl.ifindex == ifindex){
				paft_delete_by_tun(tmp);
				tunt->tid_trace[tmp->tp.tid] = 0;
				list_del(pos);
				kfree(tmp);
			}
		}
	}
}

int tunt_count(void){
	int N = 0;
	struct tunt_entry *tmp;
	u32 i;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_entry(tmp, &tunt->table[i]->list, list){
			N++;
		}
	}
	if(default_tunnel != NULL) N++;
	return N;
}

void tunt_fill_pointers(struct tun_param **tpa){
	int N = 0;
	u32 i;
	struct tunt_entry *tmp;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_entry(tmp, &tunt->table[i]->list, list){
			tpa[N] = &tmp->tp;
			N++;
		}
	}
	tpa[N] = &default_tunnel->tp;
}

int tunt_create(void){
	u32 i;
	tunt = (struct tunt_table *) kzalloc(sizeof(struct tunt_table), GFP_ATOMIC);
	if(tunt == NULL){
		printk("Error - Unable to allocating memory for tunt table");
		return -1;
	}

	TUNT_BIT_HASH = 8;
	tunt->dim = 1;
	tunt->dim = tunt->dim<<TUNT_BIT_HASH;
	TUNT_PRIME_HASH = primes[TUNT_BIT_HASH - 8];  //choose the nearest good prime for a better distribution

	tunt->table = (struct tunt_entry **)kzalloc(sizeof(struct tunt_entry *) * tunt->dim, GFP_ATOMIC);

	if(tunt->table == NULL){
		printk("Error - Unable to allocating memory for tunt table");
		return -1;
	}

	for(i=0; i<tunt->dim; i++){
		tunt->table[i] = NULL;
	}
	tunt->collisions = 0;
	tunt->overrides = 0;

	tunt->tid_trace = (int *)kzalloc(sizeof(int) * (tunt->dim+1), GFP_ATOMIC);
	if(tunt->tid_trace == NULL){
		printk("Error - Unable to allocating memory for tid_trace");
		return -1;
	}
	memset(tunt->tid_trace, 0, tunt->dim+1);
	default_tunnel = NULL;
	return 0;
}

void tunt_erase(void){
	u32 i;
	struct tunt_entry *tmp;
	struct list_head *pos, *q;

	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &tunt->table[i]->list){
			tmp = list_entry(pos, struct tunt_entry, list);
			 list_del(pos);
			 kfree(tmp);
		}
	}

	kfree(tunt->tid_trace);
	kfree(tunt->table);
	kfree(tunt);
	kfree(default_tunnel);
}

MODULE_LICENSE("GPL");
