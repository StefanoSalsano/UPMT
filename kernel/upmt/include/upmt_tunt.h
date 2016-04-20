
#ifndef __UPMT_TUNT_
#define __UPMT_TUNT_

#include "upmt.h"
#include <linux/kernel.h> //this is for the u16 and u32 type
#include <linux/list.h>

//u8 TUNT_BIT_HASH;
//u32 TUNT_PRIME_HASH;

extern struct tunt_table *tunt;
extern struct tunt_entry *default_tunnel;

struct tunt_entry {
	struct tun_param tp;
	struct keep_alive kp;
	struct list_head list;
};

struct tunt_table {
	u32 dim;
	u32 collisions;
	u32 overrides;
	struct tunt_entry **table;
	int *tid_trace;
};

int 	tunt_create(void);
void 	tunt_erase(void);

struct tunt_entry * tunt_set_default(struct tun_param *);
int 			tunt_del_default(void);

struct tunt_entry * tunt_insert(struct tun_param *, int);
struct tunt_entry *	tunt_search(const struct tun_param *);
struct tunt_entry * tunt_search_by_tid(const int);
struct tunt_entry * tunt_set_ka_by_tid(const int, const int);

int 	tunt_delete(const struct tun_param *);
void 	tunt_delete_by_tsa(const struct tun_local *);
int 	tunt_delete_by_tid(const int);
void	tunt_delete_by_ifindex(const int);
int 	tunt_count(void);
void 	tunt_fill_pointers(struct tun_param **);

#endif /*__UPMT_TUNT_*/
