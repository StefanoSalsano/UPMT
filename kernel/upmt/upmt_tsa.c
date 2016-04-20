/*
 * upmt_tsa.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_tsa.h"
#include "include/upmt_spl.h"
#include "include/upmt_util.h"
#include "include/upmt_stamp.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/slab.h>

struct tsa_entry *tsa;

struct tsa_entry * tsa_insert(struct tun_local *tl){
	struct tsa_entry *tmp;

	tmp = NULL;

	list_for_each_entry(tmp, &tsa->list, list){
		if(umpt_tun_local_equals(&tmp->tl, tl) == 0) return tmp;
	}

	tmp = (struct tsa_entry *) kzalloc(sizeof(struct tsa_entry), GFP_ATOMIC);
	if(tmp == NULL){
		dmesge("tsa_insert - Unable to allocating memory for new_entry");
		return NULL;
	}

	tun_local_copy(&tmp->tl, tl);
	list_add(&tmp->list, &tsa->list);

	return tmp;
}

struct tsa_entry * tsa_search(struct tun_local *tl){
	struct tsa_entry *tmp;

	tmp = NULL;
	list_for_each_entry(tmp, &tsa->list, list){
		//print_upmt_tun_local(&tmp->tl);
		if(umpt_tun_local_equals(&tmp->tl, tl) == 0) return tmp;
	}
	return NULL;
}

int tsa_delete(struct tun_local *tl){
	struct tsa_entry *tmp;
	struct list_head *pos, *q;

	tmp = NULL;

	list_for_each_safe(pos, q, &tsa->list){
		tmp = list_entry(pos, struct tsa_entry, list);
		if(umpt_tun_local_equals(&tmp->tl, tl) == 0){
			//print_upmt_tun_local(&tmp->tl);
			list_del(pos);
			kfree(tmp);
			return 0;
		}
	}
	return -1;
}

struct socket * tsa_delete_by_ifindex(int ifindex){
	struct tsa_entry *tmp;
	struct list_head *pos, *q;
	struct socket * s = NULL;

	list_for_each_safe(pos, q, &tsa->list){
		tmp = list_entry(pos, struct tsa_entry, list);
		if(tmp->tl.ifindex == ifindex){
			//print_upmt_tun_local(&tmp->tl);
			s = release_port(tmp->tl.port);
			list_del(pos);
			kfree(tmp);
		}
	}

	return s;
}

int	tsa_count(void){
	int N = 0;
	struct tsa_entry *tmp;

	list_for_each_entry(tmp, &tsa->list, list){
		N++;
	}
	return N;
}

void tsa_fill_pointers(struct tun_local **data){
	int N = 0;
	struct tsa_entry *tmp;
	list_for_each_entry(tmp, &tsa->list, list){
		data[N] = &tmp->tl;
		N++;
	}
}

int tsa_create(void){
	tsa = (struct tsa_entry *) kzalloc(sizeof(struct tsa_entry), GFP_ATOMIC);
	if(tsa == NULL){
		dmesge("tsa_create - unable to allocate memory");
		return -1;
	}
	INIT_LIST_HEAD(&tsa->list);
	return 0;
}

void tsa_erase(void){
	struct tsa_entry *tmp;
	struct list_head *pos, *q;

	list_for_each_safe(pos, q, &tsa->list){
		 tmp = list_entry(pos, struct tsa_entry, list);
		 //print_upmt_tun_local(&tmp->tl);
		 list_del(pos);
		 kfree(tmp);
	}
	kfree(tsa);
}

MODULE_LICENSE("GPL");
