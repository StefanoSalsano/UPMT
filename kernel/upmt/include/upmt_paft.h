#ifndef __UPMT_PAFT_
#define __UPMT_PAFT_

#include "upmt.h"
#include <linux/kernel.h> //this is for the u16 and u32 type
#include <linux/list.h>

extern struct paft_table *paft;

struct paft_entry {
	int rid;
	struct upmt_key key;
	struct tunt_entry *te;
	struct list_head list;
	char staticrule; /* 0=non static, 1=static */
};

struct paft_table {
	u32 dim;
	u32 collisions;
	u32 overrides;
	struct paft_entry **table;
	int *rid_trace;
};

int paft_create(void);
void paft_erase(void);
void paft_flush(void);
void paft_free_elements(void);
struct paft_entry * paft_insert(const struct upmt_key *, struct tunt_entry *, char);

int paft_insert_by_tid(const struct upmt_key *, int, char);

struct tunt_entry *	paft_get_tun(const struct upmt_key *);
struct paft_entry * paft_search(const struct upmt_key *);
struct paft_entry *	paft_search_by_rid(const int);

int paft_delete(const struct upmt_key *);
int paft_delete_by_rid(const int);
void paft_set_custom(unsigned int);

int paft_count(void);
void paft_fill_pointers(struct paft_entry **);

void paft_delete_by_tun(struct tunt_entry *);

#endif
