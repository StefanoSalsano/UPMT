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
#include <linux/netdevice.h>
#include <linux/utsname.h>
#include <linux/if_packet.h>

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

#include "include/upmt_util.h"
#include "include/upmt_stamp.h"

#include "include/upmt_ka.h"

int in_counter = 0;
struct pt_list registered_pt; // List of registere packet type/handlers (Sander)

/*static void check_VIPA(struct sk_buff *skb){
	struct upmt_key k;
	unsigned int vipa;

	vipa = in_aton("10.0.0.2");
	set_upmt_key_from_skb(&k, skb, HEADROOM);
	if(k.saddr != vipa) return;
	
	dmesg("RECEIVED ENCAPSULATED MESSAGE FROM VIPA 10.0.0.2");
	print_upmt_key(&k);
	print_packet_information(skb, 0);
	print_packet_information(skb, HEADROOM);
	dmesg("END");
}*/

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
	struct net_device* dev;
	//struct sk_buff *karskb;
	unsigned long flag;

	int detunneled = 0;
	int piggy = -1;

#ifdef PRINT_INTERRUPT_CONTEXT
	dmesg("process_packet_IN - packet received");
	check_context("process_packet_IN");
#endif
	iph = get_IP_header(skb);
	if(iph == NULL){
		dmesge("process_packet_IN - iph - NULL");
		return NF_ACCEPT; 
	}
	if(iph->protocol != IPPROTO_UDP) return NF_ACCEPT;

	write_lock_irqsave(&bul_mutex, flag);
	dev = search_dev_by_ip(iph->daddr);
	write_unlock_irqrestore(&bul_mutex, flag);

	if(dev == NULL) return NF_ACCEPT;

	if(skb_linearize(skb)) dmesg("Received skbuff is paged but linearize was unsuccesful. UDP or TCP ports may be wrong and packet discarded.");

#ifdef VERBOSE_MODE
	if(verbose >= 1){
		dmesg_lbl("IN", "Begin");
		print_packet_information(skb, 0);
	}
#endif

	//check_context("process_packet_IN");
	write_lock_irqsave(&bul_mutex, flag);

	set_tun_local_from_skb(&tp.tl, skb);
	set_tun_remote_from_skb(&tp.tr, skb);

	if(tsa_search(&tp.tl) != NULL){


#ifdef VERBOSE_MODE
		if(verbose >= 2){
			dmesg_lbl("IN", "External");
			print_packet_information(skb, 0);
		}
#endif


		if(check_IP_checksum(skb, 0) < 0) 					goto out; //check if the checksum of the IP packet is correct
		if(check_UDP_checksum(skb, 0) < 0) 					goto out; //check if the checksum of the UDP packet is correct

		piggy = isPiggy(skb);
		if(piggy < 0) if(check_IP_checksum(skb, HEADROOM) < 0) 			goto out; //check if the checksum of encapsulated IP packet is correct


#ifdef VERBOSE_MODE
		if(verbose >= 2){
			dmesg_lbl("IN", "Internal");
			if(piggy == 0) dmesg(" -----------> PIGGY");
			if(piggy == 0) print_packet_information(skb, HEADROOM +  PIGGYROOM);
			else print_packet_information(skb, HEADROOM);
		}
#endif

		te = tunt_search(&tp);
		if(te == NULL){

			//if(is_keep_alive_pkt(skb, headroom) == 0) goto drop;

			tp.in.local = 0;
			tp.in.remote = 0;
			{
				struct net_device *dev;
				struct mdl_entry *mde;
				dev = dev_get_by_index(upmtns->net_ns, tp.tl.ifindex);
				if(dev == NULL) goto out;
				mde = mdl_search(dev->name);
				dev_put(dev);
				if(mde == NULL) goto out;
				tp.md = &mde->md;
			}

			te = tunt_insert(&tp, 0);
			//if(is_keep_alive_pkt(skb, HEADROOM) != 0) te = tunt_insert(&tp, 0);
			//else if(te == NULL) goto out;
		}

		/*if(te->kp.state == KEEP_ALIVE_ON)*/	te->kp.info.pRc++;
		/*if(te->kp.state == KEEP_ALIVE_OFF)*/	te->kp.info.pRs++;

		decap(skb, te);
		
		detunneled = 1;
#ifdef VERBOSE_MODE
		if(verbose >= 2){
			dmesg_lbl("IN", "After Decap");
			print_packet_information(skb, 0);
		}
#endif

		iph = get_IP_header(skb);
		if(te->tp.in.local == iph->daddr){
			denat_packet(skb);
#ifdef VERBOSE_MODE
			if(verbose >= 4){
				dmesg_lbl("IN", "After De_iNAT");
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

		if(manage_keep_alive(te, skb) == 0){
			goto drop;
		}

		set_upmt_key_from_remote_skb(&k, skb, 0);

		pe = paft_search(&k);
		if (pe == NULL) paft_insert(&k, te, 0);
		else if (pe->staticrule == 0)
			pe->te = te; // ---> handover
	}

out:
	//kfree_skb(skb);
	write_unlock_irqrestore(&bul_mutex, flag);
	return NF_ACCEPT;

drop:
	//kfree_skb(skb);
	write_unlock_irqrestore(&bul_mutex, flag);
	return NF_DROP;
}



static int upmt_tunnel_rcv(struct sk_buff *skb,
					struct net_device *in,
					struct packet_type *pt,
					struct net_device *out) {
	// According to the packet type I choose what to do
	// Incoming and outgoing packets are managed differently (Sander)
	switch( skb->pkt_type )
	{
	    case PACKET_HOST: // incoming packets
	    	goto incoming;
	    case PACKET_BROADCAST: // incoming broadcasts
	    	goto incoming;
	    case PACKET_MULTICAST: // incoming multicasts
	    	goto incoming;
	    case PACKET_OTHERHOST: // incoming otherhosts
	    	goto out; // I do nothing
	    case PACKET_OUTGOING: // outgoing any type
	    	// Here I could manage outgoing packets

			#ifdef VERBOSE_MODE
			if(verbose >= 2){
				dmesg("OUT (testing handler)");
				print_packet_information(skb, 0);
			}
			#endif

			goto out;
	    case PACKET_LOOPBACK: // loopback
	    	goto out;
	    case PACKET_FASTROUTE: // fast route
	    	goto out;
	    default :
	    	// I should not get here
	    	goto out;
	}

incoming :
	in_counter++;
	if(in_counter % 10000 == 0) printk("Receiving packet %d...\n", in_counter);
	//return process_packet_IN(skb);
	process_packet_IN(skb);
	// I should not get here
out:
	kfree_skb(skb);
	return 0;
}

int upmt_ph_register(struct net_device *devtoreg) {
	struct pt_list * tmp = (struct pt_list *) kmalloc(sizeof(struct pt_list), GFP_ATOMIC);
	tmp->pt.type = htons(ETH_P_IP);
	tmp->pt.func = upmt_tunnel_rcv;
	tmp->pt.dev = devtoreg;
	dmesg("Adding interface handler for device with ifindex %d", devtoreg->ifindex);
	list_add(&(tmp->list), &(registered_pt.list)); // Adding the new packet type to the registered handlers (Sander)
	dev_add_pack(&(tmp->pt));
	return 0;
}

void upmt_ph_unregister(struct net_device *devtounreg) {
	struct list_head *pos;
	struct list_head *q;
	struct pt_list *tmp;
	list_for_each_safe(pos,q,&(registered_pt.list)){
		tmp= list_entry(pos, struct pt_list, list);
		if ((tmp->pt.dev)->ifindex==devtounreg->ifindex) {
			dmesg("Releasing interface handler for device with ifindex %d", devtounreg->ifindex);
			dev_remove_pack(&(tmp->pt));
			list_del(pos);
			kfree(tmp);
		}
	}
}

// Unregistering every packet handler (used when unloading the upmt module) (Sander)
void upmt_ph_unregister_all() {
	struct list_head *pos;
	struct list_head *q;
	struct pt_list *tmp;
	list_for_each_safe(pos,q,&(registered_pt.list)){
		tmp= list_entry(pos, struct pt_list, list);
		dmesg("Releasing interface handler for device with ifindex %d", (tmp->pt.dev)->ifindex);
		dev_remove_pack(&(tmp->pt));
		list_del(pos);
		kfree(tmp);
	}
}


MODULE_LICENSE("GPL");
