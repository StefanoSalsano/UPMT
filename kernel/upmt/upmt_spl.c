/*
 * upmt_sock.c
 *
 *  Created on: 11/giu/2010
 *      Author: fabbox
 */

#include "include/upmt_spl.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/in.h>

struct spl_entry *spl;

struct spl_entry * search_port(u16 port){
	struct spl_entry *tmp;
	if(spl == NULL) return NULL;
	list_for_each_entry(tmp, &spl->list, list){
		if(tmp->port == port) return tmp;
	}
	return NULL;
}

int reserve_port(u16 port /*, struct socket *sock*/){
	struct spl_entry *tmp;
	struct sockaddr_in addr;

	tmp = search_port(port);
	if(tmp != NULL){   //there is another TSA which reserved the port, so we increase the counter
		tmp->n++;
		return 1;
	}

	tmp = (struct spl_entry *) kzalloc(sizeof(struct spl_entry), GFP_ATOMIC);
	if(tmp == NULL){
		printk("reserve_port - Error - Unable to allocating memory for new_entry");
		return -2;
	}
	tmp->n = 1;
	tmp->port = port;

	if(sock_create(AF_INET, SOCK_DGRAM, IPPROTO_UDP, &tmp->sock) < 0){
		printk("reserve_port - Error - Unable to create socket");
		kfree(tmp);
		return -2;
	}
	//tmp->sock = sock;

	memset(&addr, 0, sizeof(struct sockaddr));
	addr.sin_family      		= AF_INET;
	addr.sin_addr.s_addr      	= htonl(INADDR_ANY);
	addr.sin_port				= htons(port);

	if(tmp->sock->ops->bind(tmp->sock, (struct sockaddr *)&addr, sizeof(struct sockaddr)) < 0){
		printk("reserve_port - Error - Unable to bind port %u", port);
		kfree(tmp);
		return -1; //TODO check the errno variable to understand the origin of the error
	}

	list_add(&tmp->list, &spl->list);
	return 0;
}

struct socket * release_port(u16 port){
	struct spl_entry *tmp;
	struct socket *s = NULL;

	tmp = search_port(port);
	if(tmp != NULL){
		tmp->n--;
		if(tmp->n < 0) printk("\n !-WARNING-! - release_port - n with negative value");
		if(tmp->n <= 0){
			//sock_release(tmp->sock);
			list_del(&tmp->list);
			s = tmp->sock;
			kfree(tmp);
		}
	}
	return s;
}

/*void release_ports(u16 port){
	struct spl_entry *tmp;

	tmp = search_port(port);
	if(tmp != NULL){
		sock_release(tmp->sock);
		list_del(&tmp->list);
		kfree(tmp);
	}
}*/

int spl_create(void){
	spl = (struct spl_entry *) kzalloc(sizeof(struct spl_entry), GFP_ATOMIC);
	if(spl == NULL){
		printk("spl_create - Error - Unable to allocating memory for spl");
		return -1;
	}
	INIT_LIST_HEAD(&spl->list);
	return 0;
}

void spl_erase(void){
	struct spl_entry *tmp;
	struct list_head *pos, *q;

	if(spl == NULL) return;

	list_for_each_safe(pos, q, &spl->list){
		 tmp = list_entry(pos, struct spl_entry, list);
		 sock_release(tmp->sock);
		 list_del(pos);
		 kfree(tmp);
	}
	kfree(spl);
}
