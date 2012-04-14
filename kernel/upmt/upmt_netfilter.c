/*
 * upmt_netfilter.c
 *
 *  Created on: 14/apr/2010
 *      Author: fabbox
 */

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/skbuff.h>
#include <linux/ip.h>
#include <linux/tcp.h>
#include <linux/udp.h>
#include <linux/inetdevice.h>

#include "include/upmt_netfilter.h"
#include "include/upmt.h"
#include "include/upmt_paft.h"
#include "include/upmt_tunt.h"
#include "include/upmt_tsa.h"
#include "include/upmt_mdl.h"
#include "include/upmt_encap.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"
#include "include/upmt_pdft.h"

int in_counter;
static struct nf_hook_ops nf_ops_in;

#if 0
static void __denat(struct sk_buff *skb) {
	struct iphdr * ip = ip_hdr(skb);
	__u8 * l4hdr = skb->data + (ip->ihl*4);
	struct in_device *indev;
	unsigned short l4len = ntohs(ip->tot_len) - (ip->ihl*4);
	__sum16 *l4csum;
	__wsum csum;

	//SNAT with the first primary address...
	rcu_read_lock();
	indev = __in_dev_get_rcu(upmt_dev);
	for_primary_ifa(indev){ 
		ip->daddr = ifa->ifa_address;
		break;
	}endfor_ifa(indev);
	rcu_read_unlock();

	ip->check = 0;
	ip->check = ip_fast_csum((unsigned char *)ip, ip->ihl);

	switch(ip->protocol){
		case IPPROTO_UDP:
			l4csum = &((struct udphdr *)l4hdr)->check; 
			break;
		case IPPROTO_TCP:
			l4csum = &((struct tcphdr *)l4hdr)->check;
			break;
		case IPPROTO_ICMP:
			//do nothing.. netfilter will masquerade the ICMP packet
		default:
			goto csum_done;
	}

	*l4csum = 0;
	csum = csum_partial(l4hdr, l4len, 0);
	*l4csum = csum_tcpudp_magic(ip->saddr, ip->daddr, l4len, ip->protocol, csum);
csum_done:
	return;
}
#endif

static unsigned int process_packet_IN(struct sk_buff *skb){
	struct iphdr *iph;
	struct tun_param tp;
	struct upmt_key k;
	struct paft_entry *pe;
	struct tunt_entry *te;

	int detunneled = 0;
#ifdef PRINT_INTERRUPT_CONTEXT
	printk("\n\n\tpacket received");
	check_context();
#endif
	iph = get_IP_header(skb);
	if(iph->protocol != IPPROTO_UDP) return NF_ACCEPT;

	if(skb_linearize(skb)) printk("UPMT: received skbuf is paged but linearize was unsuccesful. UDP or TCP ports may be wrong and packet discarded.\n");

	set_tun_local_from_skb(&tp.tl, skb);
	set_tun_remote_from_skb(&tp.tr, skb);

	//so, the sender is in the tsa_list ?

	bul_write_lock();

	if(tsa_search(&tp.tl) != NULL){

#ifdef VERBOSE_MODE
		if(verbose > 0) printk("\n");
		if(verbose >= 1){
			printk("\n\tIN--------------------Incoming---------------------------");
			print_packet_information(skb, 0);
		}
		if(verbose >= 3){
			printk("\n\tIN--------------------Internal---------------------------");
			print_packet_information(skb, HEADROOM);
		}
#endif

		if(check_IP_checksum(skb, 0) < 0) 					goto out; //check if the checksum of the IP packet is correct
		if(check_UDP_checksum(skb, 0) < 0) 					goto out; //check if the checksum of the UDP packet is correct
		if(check_IP_checksum(skb, HEADROOM) < 0) 			goto out; //check if the checksum of encapsulated IP packet is correct

		//te = tunt_search(&tp.tl);
		te = tunt_search(&tp);
		if(te == NULL){
			tp.in.local = 0;
			tp.in.remote = 0;
			{
				struct net_device *dev;
				struct mdl_entry *mde;
				dev = dev_get_by_index(&init_net, tp.tl.ifindex);
				if(dev == NULL) goto out;
				mde = mdl_search(dev->name);
				dev_put(dev);
				if(mde == NULL) goto out;
				tp.md = &mde->md;
			}

			te = tunt_insert(&tp, 0);
		}

		decap(skb);
		
		detunneled = 1;
#ifdef VERBOSE_MODE
		if(verbose >= 2){
			printk("\n\tIN-------------------After Decap-------------------------");
			print_packet_information(skb, 0);
		}
#endif

		iph = get_IP_header(skb);
		if(te->tp.in.local == iph->daddr){
			denat_packet(skb);
#ifdef VERBOSE_MODE
			if(verbose >= 4){
				printk("\n\tOUT-------------------After DENAT-------------------------");
				print_packet_information(skb, 0);
			}
#endif
		}

		mark_packet(skb); //maybe this must be escluded in vpn case

#ifdef UPMT_S
		//MARCO - if IPsec is used is impossible to 
		//update the PAFT. Let's do it later...
		if (iph->protocol == IPPROTO_ESP) {
			skb->tun_entry = te;
			skb->mark = UPMT_S_MARK;
			goto out;
		}
#endif
		set_upmt_key_from_remote_skb(&k, skb, 0);

		pe = paft_search(&k);
		if (pe == NULL) paft_insert(&k, te, 0);
		else if (pe->staticrule == 0)
			pe->te = te; // ---> handover
	}
out:
	bul_write_unlock();

	//if (detunneled)
	//	__dnat(skb);

	return NF_ACCEPT;
}



static unsigned int upmt_tunnel_rcv(unsigned int hooknum,
                  struct sk_buff *skb,
                  const struct net_device *in,
                  const struct net_device *out,
                  int (*okfn)(struct sk_buff*)) {
	in_counter++;
	return process_packet_IN(skb);
	//return NF_ACCEPT;
}

int upmt_nf_register() {
	int err;

	nf_ops_in.hook                   =       upmt_tunnel_rcv;
	nf_ops_in.pf                     =       PF_INET;
	nf_ops_in.hooknum                =       NF_INET_PRE_ROUTING;
	nf_ops_in.priority               =       NF_IP_PRI_FIRST;

	err = nf_register_hook(&nf_ops_in);
	in_counter = 0;
	return err;
}

void upmt_nf_unregister() {
	nf_unregister_hook(&nf_ops_in);
}

MODULE_LICENSE("GPL");
