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

int upmt_nf_register(void);
void upmt_nf_unregister(void);

//static unsigned int process_packet_IN(struct sk_buff *);


#endif /* UPMT_NETFILTER_H_ */
