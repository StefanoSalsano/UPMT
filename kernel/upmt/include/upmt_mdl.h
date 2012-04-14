/*
 * upmt_mdl.h
 *
 *  Created on: 16/giu/2010
 *      Author: fabbox
 */

#ifndef UPMT_MDL_H_
#define UPMT_MDL_H_

#include "upmt.h"
#include <linux/kernel.h> 		//this is for the u16 and u32 type
#include <linux/list.h>

struct mdl_entry{
	struct mark_dev md;
	struct list_head list;
};

extern struct mdl_entry *mdl; //Mark Device List

	int 	mdl_create(void);
	void	mdl_erase(void);
struct mdl_entry * mdl_search(char *);
struct mdl_entry * mdl_insert(char *);
	int 	mdl_delete(char *);

int 	mdl_count(void);
void 	mdl_fill_pointers(struct mark_dev **);

//struct mde_entry * search_port//16);

#endif /* UPMT_MDL_H_ */
