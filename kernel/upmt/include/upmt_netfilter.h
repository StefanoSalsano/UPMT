/*
 * upmt_netfilter.h
 *
 *  Created on: 16/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_NETFILTER_H_
#define UPMT_NETFILTER_H_

/*#ifndef CONFIG_NETFILTER
#define CONFIG_NETFILTER
#endif*/
//extern int in_counter;
//extern struct nf_hook_ops nf_ops_in;

int upmt_ph_register(struct net_device*);
void upmt_ph_unregister(struct net_device*);
void upmt_ph_unregister_all(void);

// Registered packet type list (Sander)
struct pt_list{
	struct list_head list;
	struct packet_type pt;
	};

extern struct pt_list registered_pt;

//static unsigned int process_packet_IN(struct sk_buff *);


#endif /* UPMT_NETFILTER_H_ */
