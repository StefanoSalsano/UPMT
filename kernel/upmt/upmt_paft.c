/*
 * upmt_paft.c
 *
 *  Created on: 14/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_paft.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"
#include "include/upmt_tunt.h"
#include "include/upmt_locks.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>
#include <linux/vmalloc.h>

#define PAFT_BIT_HASH		16	/*min 8 - max 30*/
#define INITIAL_PAFT_DIM	(1<<PAFT_BIT_HASH)
u32 PAFT_PRIME_HASH;

struct paft_table *paft;

static int get_new_rid(void){
	u32 i;
	for(i=1; i<paft->dim+1; i++){
		if(paft->rid_trace[i] == 0){
			paft->rid_trace[i] = 1;
			return i;
		}
	}
	return -1;
}

/*static int is_available_rid(int rid){
	if(paft->rid_trace[rid] == 0) return 0;
	else return -1;
}*/

static u32 paft_hash_from_key(const struct upmt_key *k){
	u32 hash = (((k->saddr << 17) | (k->daddr >> 15)) ^ k->daddr) + (k->sport * 17) + (k->dport * 13 * 29);
	hash = hash + k->proto;
	hash = hash % PAFT_PRIME_HASH;
	hash = hash % paft->dim;
	return hash;
}

static void paft_entry_copy(struct paft_entry *a, const struct paft_entry *b){
	upmt_key_copy(&a->key, &b->key);
	a->rid = b->rid;
	a->te = b->te;
	a->staticrule = b->staticrule;
}

static struct paft_entry * paft_insert_entry(const struct paft_entry *e){
	struct paft_entry *tmp;
	u32 hash = paft_hash_from_key(&e->key);

	if(paft->table[hash] == NULL){
		paft->table[hash] = (struct paft_entry *) kzalloc(sizeof(struct paft_entry), GFP_ATOMIC);
		if(paft->table[hash] == NULL){
			printk("paft_insert - Error - Unable to allocate memory for first new_entry");
			return NULL;
		}
		INIT_LIST_HEAD(&paft->table[hash]->list);
	}
	
	// if key is found in the synonym list, update it
	list_for_each_entry(tmp, &paft->table[hash]->list, list){
		if(umpt_key_equals(&e->key, &tmp->key) == 0){
			tmp->te = e->te;
			tmp->staticrule = e->staticrule;
			if(e->rid != tmp->rid) paft->rid_trace[e->rid] = 0;
			paft->overrides++;
			return tmp;
		}
	}

	// otherwise add it
	tmp = (struct paft_entry *) kzalloc(sizeof(struct paft_entry), GFP_ATOMIC);
	if(tmp == NULL){
		printk("paft_insert - Error - Unable to allocate memory for new_entry");
		return NULL;
	}
	paft_entry_copy(tmp, e);
	list_add(&tmp->list, &paft->table[hash]->list);
	//paft->collisions++;
	return tmp;
}

struct paft_entry * paft_insert(const struct upmt_key *k, struct tunt_entry *te, char staticrule){
	//is_available_rid
	struct paft_entry e;
	upmt_key_copy(&e.key, k);
	e.te = te;
	e.rid = get_new_rid();
	e.staticrule = staticrule;
	return paft_insert_entry(&e);
}

struct paft_entry * paft_search(const struct upmt_key *k){
	struct paft_entry *tmp;
	u32 hash = paft_hash_from_key(k);

	tmp = NULL;

	if(paft->table[hash] == NULL) return NULL;

	list_for_each_entry(tmp, &paft->table[hash]->list, list){
		if(umpt_key_equals(k, &tmp->key) == 0) return tmp;
	}
	return NULL;
}

struct paft_entry * paft_search_by_rid(const int rid){
	struct paft_entry *tmp;
	u32 i;

	tmp = NULL;
	
	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_entry(tmp, &paft->table[i]->list, list){
			if(tmp->rid == rid) return tmp;
		}
	}
	return NULL;
}

struct tunt_entry * paft_get_tun(const struct upmt_key *k){
	struct paft_entry *e = paft_search(k);
	if(e == NULL) return NULL;
	else return e->te;
}

int paft_delete(const struct upmt_key *k){
	struct paft_entry *tmp;
	struct list_head *pos, *q;

	u32 hash = paft_hash_from_key(k);
	if(paft->table[hash] == NULL) return -1;
	list_for_each_safe(pos, q, &paft->table[hash]->list){
		 tmp = list_entry(pos, struct paft_entry, list);
		 paft->rid_trace[tmp->rid] = 0;
		 list_del(pos);
		 kfree(tmp);
		 return 0;
	}
	return -1;
}
EXPORT_SYMBOL(paft_delete);

int paft_delete_by_rid(const int rid){
	struct paft_entry *tmp;
	struct list_head *pos, *q;
	u32 i;

	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &paft->table[i]->list){
			tmp = list_entry(pos, struct paft_entry, list);
			if(tmp->rid == rid){
				paft->rid_trace[rid] = 0;
				list_del(pos);
				kfree(tmp);
				return 0;
			}
		}
	}
	return -1;
}

void paft_delete_by_tun(struct tunt_entry *te){
	struct paft_entry *tmp;
	struct list_head *pos, *q;
	u32 i;

	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &paft->table[i]->list){
			tmp = list_entry(pos, struct paft_entry, list);
			if(tmp->te == te) {
				paft->rid_trace[tmp->rid] = 0;
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	return;
}
int paft_count(void){
	int N = 0;
	struct paft_entry *tmp;
	u32 i;

	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_entry(tmp, &paft->table[i]->list, list){
			N++;
		}
	}
	return N;
}

void paft_fill_pointers(struct paft_entry **pea){
	int N = 0;
	u32 i;
	struct paft_entry *tmp;

	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_entry(tmp, &paft->table[i]->list, list){
			pea[N] = tmp;
			N++;
		}
	}
}

int paft_create(){
	u32 i;
	
	paft = (struct paft_table *) kzalloc(sizeof(struct paft_table), GFP_KERNEL);
	if(paft == NULL){
		printk("Error - Unable to allocate memory for paft table");
		return -1;
	}

	paft->dim = INITIAL_PAFT_DIM;
	PAFT_PRIME_HASH = primes[PAFT_BIT_HASH - 8];  //choose the nearest good prime for a better distribution

	//paft->table = (struct paft_entry **)kzalloc(sizeof(struct paft_entry *) * paft->dim, GFP_KERNEL);
	paft->table = (struct paft_entry **) vzalloc(sizeof(struct paft_entry *) * paft->dim);
	if(paft->table == NULL){
		printk("Error - Unable to allocate memory for rt_table");
		return -1;
	}

	for(i=0; i<paft->dim; i++){
		paft->table[i] = NULL;
	}

	paft->collisions = 0;
	paft->overrides = 0;

	//paft->rid_trace = (int *) kzalloc(sizeof(int) * (paft->dim+1), GFP_KERNEL);
	paft->rid_trace = (int *) vzalloc(sizeof(int) * (paft->dim+1));
	if(paft->rid_trace == NULL){
		printk("Error - Unable to allocate memory for rid_trace");
		return -1;
	}
	memset(paft->rid_trace, 0, paft->dim+1);

	return 0;
}

void paft_free_elements(void) {
	u32 i;
	struct paft_entry *tmp;
	struct list_head *pos, *q;

	for(i=0; i<paft->dim; i++){
		if(paft->table[i] == NULL) continue;
		list_for_each_safe(pos, q, &paft->table[i]->list){
			tmp = list_entry(pos, struct paft_entry, list);
			list_del(pos);
			kfree(tmp);
		}
	}
}

void paft_erase(void){
	paft_free_elements();
	vfree(paft->rid_trace);
	vfree(paft->table);
	kfree(paft);
}

void paft_flush()
{
	int i;
	
	paft_free_elements();
	
	paft->dim = INITIAL_PAFT_DIM;
	for(i=0; i<paft->dim; i++){
		paft->table[i] = NULL;
	}
	paft->collisions = 0;
	paft->overrides = 0;
	memset(paft->rid_trace, 0, paft->dim+1);
}

int paft_insert_by_tid(const struct upmt_key *key, int tid, char staticrule) {
	int res = 0;
	struct tunt_entry *te;
	
	if((key->proto != IPPROTO_TCP)&&(key->proto != IPPROTO_UDP)) {
		res = -1; // Transport protocol is wrong or not specified
		goto end;
	}

	bul_write_lock();

	te = tunt_search_by_tid(tid);
	if(te == NULL) {
		res = -2; // Tunnel does not exist
		goto end;
	}

	if(paft_insert(key, te, staticrule) == NULL) {
		res = -3; // Error while inserting paft rule (type 'dmesg' for details)
		goto end;
	}

end:
	bul_write_unlock();
	return res;
}
EXPORT_SYMBOL(paft_insert_by_tid);

MODULE_LICENSE("GPL");
