/*
 * upmt_mdl.c
 *
 *  Created on: 16/giu/2010
 *      Author: fabbox
 */

#include "include/upmt_mdl.h"
#include "include/upmt_util.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/slab.h>

struct mdl_entry *mdl;
/*
 * this is the counter of marks associated to each interface
 * every time a new mark is requested it is incremented by one
 */
unsigned int mdl_cmark;

struct mdl_entry * mdl_search(char *iname){
	struct mdl_entry *tmp;
	if(mdl == NULL) return NULL;
	list_for_each_entry(tmp, &mdl->list, list){
		//if(strcmp(tmp->iname, iname) == 0) return tmp;
		if(strcmp(tmp->md.iname, iname) == 0) return tmp;
	}
	return NULL;
}

struct mdl_entry * mdl_insert(char *iname){
	struct mdl_entry *tmp;
	if(mdl == NULL) return NULL;

	tmp = mdl_search(iname);
	if(tmp != NULL) return tmp;

	tmp = (struct mdl_entry *) kzalloc(sizeof(struct mdl_entry), GFP_ATOMIC);
	if(tmp == NULL){
		dmesge("mdl_insert - Unable to allocating memory for new_entry");
		return NULL;
	}
	memcpy(tmp->md.iname, iname, strlen(iname));
	tmp->md.mark = mdl_cmark;
	mdl_cmark++;

	list_add(&tmp->list, &mdl->list);
	return tmp;
}

int mdl_delete(char *iname){
	struct mdl_entry *tmp;
	if(mdl == NULL) return -1;

	tmp = mdl_search(iname);
	if(tmp == NULL) return -1;

	list_del(&tmp->list);
	kfree(tmp);
	return 0;
}

int	mdl_count(void){
	int N = 0;
	struct mdl_entry *tmp;
	list_for_each_entry(tmp, &mdl->list, list){
		N++;
	}
	return N;
}

void mdl_fill_pointers(struct mark_dev **data){
	int N = 0;
	struct mdl_entry *tmp;
	list_for_each_entry(tmp, &mdl->list, list){
		data[N] = &tmp->md;
		N++;
	}
}

int mdl_create(void){
	mdl = (struct mdl_entry *) kzalloc(sizeof(struct mdl_entry), GFP_ATOMIC);
	if(mdl == NULL){
		dmesge("mdl_create - Unable to allocating memory for mdl");
		return -1;
	}
	INIT_LIST_HEAD(&mdl->list);
	mdl_cmark = 100;
	return 0;
}

void mdl_erase(void){
	struct mdl_entry *tmp;
	struct list_head *pos, *q;

	if(mdl == NULL) return;

	list_for_each_safe(pos, q, &mdl->list){
		 tmp = list_entry(pos, struct mdl_entry, list);
		 list_del(pos);
		 kfree(tmp);
	}
	kfree(mdl);
}
