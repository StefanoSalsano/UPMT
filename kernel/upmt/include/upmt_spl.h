/*
 * upmt_spl.h
 *
 *  Created on: 13/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_SPL_H_
#define UPMT_SPL_H_

#include "upmt.h"
#include <linux/kernel.h> 		//this is for the u16 and u32 type
#include <linux/net.h>
#include <linux/list.h>

struct spl_entry{
	int n;
	u16 port;
	struct socket *sock;
	struct list_head list;
};

extern struct spl_entry *spl;	// Socket Port List
int spl_create(void);
void spl_erase(void);
struct spl_entry * search_port(u16);

/*
 * It returns:
 *  0 - port was free and now is reserved for upmt use
 *  1 - port is already reserved for upmt use
 * -1 - port is not free, used probably by an application
 * -2 - internal code error (memory error, socket creation error)
 */
	int 	reserve_port(u16/*, struct socket * */);
	struct socket * release_port(u16);

	//void 	release_ports(u16 port);

#endif /* UPMT_SPL_H_ */
