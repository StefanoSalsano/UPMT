#ifdef UPMT_S

#ifndef __KERNEL__
#define __KERNEL__
#endif

#ifndef MODULE
#define MODULE
#endif

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/skbuff.h>
#include <linux/ip.h>
#include <linux/udp.h>
#include <linux/tcp.h>
#include <linux/types.h>

#include "include/upmt_paft.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"


struct nf_hook_ops *hook_out;
struct nf_hook_ops *hook_in;
struct nf_hook_ops *hook_fwd;

unsigned int 
upmt_set_app_flow(unsigned int hooknum, struct sk_buff *skb, const struct net_device *in,
			  const struct net_device *out, int (*okfn)(struct sk_buff*)) {

	struct iphdr * iph;
	struct udphdr * l4h;
	
	iph = (struct iphdr *)skb->data;
	l4h = (struct udphdr *)(skb->data + iph->ihl*4);

	if (!((iph->protocol == IPPROTO_UDP) || (iph->protocol == IPPROTO_TCP))) return NF_ACCEPT;

	/* set skbuf app_flow fields */
	skb->upmt_flow.ip_proto = iph->protocol;
	skb->upmt_flow.ip_src = iph->saddr;
	skb->upmt_flow.ip_dst = iph->daddr;

	//copy UDP or TCP ports (same offset from iph)
	skb->upmt_flow.src_port = l4h->source;
	skb->upmt_flow.dst_port = l4h->dest;

	return NF_ACCEPT;
}

unsigned int 
upmt_update_paft(unsigned int hooknum, struct sk_buff *skb, const struct net_device *in,
			  const struct net_device *out, int (*okfn)(struct sk_buff*)) {
	struct iphdr * iph;
	struct udphdr * l4h;
	struct paft_entry *pe;
	struct upmt_key k;	
	struct tunt_entry *te = (struct tunt_entry *)skb->tun_entry;
	unsigned long fl;

	iph = (struct iphdr *)skb->data;
	l4h = (struct udphdr *)(skb->data + iph->ihl*4);

	if ( (skb->mark == UPMT_S_MARK) && (iph->protocol != IPPROTO_ESP))  {
		bul_write_lock_irqsave(fl);
		set_upmt_key_from_remote_skb(&k, skb, 0);
		pe = paft_search(&k);
		if(pe == NULL)  {
			paft_insert(&k, te, 0);
		}
		else pe->te = te; // ---> handover
		bul_write_unlock_irqrestore(fl);
	}

	return NF_ACCEPT;
}

int upmt_s_init(void) {
	int err = 0;

	printk("UPMT-S extension initialized\n");

	//UPMT-S hook for OUTgoing packets
	hook_out = kzalloc(sizeof(struct nf_hook_ops), GFP_KERNEL);
	
	hook_out->hook 		= upmt_set_app_flow;
	hook_out->pf 		= PF_INET;
	hook_out->hooknum	= NF_INET_POST_ROUTING;
	hook_out->priority	= NF_IP_PRI_FIRST;
	
	if ((err = nf_register_hook(hook_out)))
		kfree(hook_out);

	//UPMT-S hook for INcoming packets - for local processes
	hook_in = kzalloc(sizeof(struct nf_hook_ops), GFP_KERNEL);
	
	hook_in->hook 		= upmt_update_paft;
	hook_in->pf 		= PF_INET;
	hook_in->hooknum	= NF_INET_LOCAL_IN;
	hook_in->priority	= NF_IP_PRI_FIRST;
	
	if ((err = nf_register_hook(hook_in)))
		kfree(hook_in);

	//UPMT-S hook for INcoming packets - to forward
	hook_fwd = kzalloc(sizeof(struct nf_hook_ops), GFP_KERNEL);
	
	hook_fwd->hook 		= upmt_update_paft;
	hook_fwd->pf 		= PF_INET;
	hook_fwd->hooknum	= NF_INET_FORWARD;
	hook_fwd->priority	= NF_IP_PRI_FIRST;
	
	if ((err = nf_register_hook(hook_fwd)))
		kfree(hook_fwd);

	return err;
}

void upmt_s_fini(void) {
	nf_unregister_hook(hook_out);
	nf_unregister_hook(hook_in);
	nf_unregister_hook(hook_fwd);

	printk("UPMT-S extension unloaded");
}

MODULE_LICENSE("GPL");
#endif
