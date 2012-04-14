/*
 * upmt_encap.c
 *
 *  Created on: 14/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_encap.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"
#include "include/upmt_stamp.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>
#include <linux/ip.h>
#include <linux/tcp.h>
#include <linux/udp.h>
#include <linux/inetdevice.h>
#include <net/udp.h>
#include <net/checksum.h>
#include <net/route.h>
#include <linux/netdevice.h>
#include <linux/in_route.h>
#include <net/flow.h>

static void set_ip_header(struct sk_buff *skb, const struct iphdr *old_iph, u32 saddr, u32 daddr){
	struct iphdr *iph;
	iph = ip_hdr(skb);
	memcpy(iph, old_iph, sizeof(struct iphdr));
	iph->protocol 	= IPPROTO_UDP;
	iph->saddr 		= saddr;
	iph->daddr 		= daddr;
	iph->tot_len 	= htons(ntohs(old_iph->tot_len) + HEADROOM);
	iph->frag_off 	= htons(IP_DF); //TODO fragmentation problem
	iph->check = 0;
	skb->ip_summed = CHECKSUM_NONE;
}

static void set_udp_header(struct sk_buff *skb, const struct iphdr *old_iph, const u16 sport, const u16 dport){
	unsigned short len;
	struct udphdr *udph;

	udph = get_UDP_header(skb);

	len = ntohs(old_iph->tot_len) + UDP_HEADROOM;
	udph->source = htons(sport);
	udph->dest = htons(dport);
	//udph->len = htons(len);
	udph->len = htons(ntohs(old_iph->tot_len) + UDP_HEADROOM);

	udph->check = 0;
	compute_UDP_checksum(skb);
}

void inat_packet(struct sk_buff *skb, const struct tun_param *tp){
	struct iphdr *iph;
	iph = get_IP_header(skb);
	iph->saddr = tp->in.local;

	iph->check = 0;
	iph->check = ip_fast_csum((unsigned char *)iph, iph->ihl);

	compute_TRANSPORT_checksum(skb);
}

void denat_packet(struct sk_buff *skb){
	struct in_device *pin_dev;
	struct iphdr *iph;
	//u32 naddr;

	iph = get_IP_header(skb);
	pin_dev = (struct in_device *) upmt_dev->ip_ptr;
	if(pin_dev == NULL){
		printk("\n\t denat_packet - error - pin_dev ---> NULL");
		return;
	}
	if(pin_dev->ifa_list == NULL){
		printk("\n\t denat_packet - error - pin_dev->ifa_list ---> NULL");
		return;
	}
	/*if(pin_dev->ifa_list->ifa_address == NULL){
		printk("\n\t denat_packet - error - pin_dev->ifa_list->ifa_address ---> NULL");
		return;
	}*/
	//printk("\n\n IP of upmt_dev: %u \n\t ---> ", pin_dev->ifa_list->ifa_address);
	//print_ip_address(pin_dev->ifa_list->ifa_address);
	iph->daddr = pin_dev->ifa_list->ifa_address;

	//naddr = get_dev_ip_address(upmt_dev, NULL, 0);
	//iph->daddr = naddr;

	iph->check = 0;
	iph->check = ip_fast_csum((unsigned char *)iph, iph->ihl);

	compute_TRANSPORT_checksum(skb);
}

void mark_packet(struct sk_buff *skb){
	if(an_mark != 0) skb->mark = an_mark;
}

int decap(struct sk_buff *skb){

	skb_pull(skb, HEADROOM);
	skb_reset_network_header(skb);

	skb->dev = upmt_dev;

	mark_packet(skb);

	return 0;
}

int encap(struct sk_buff *skb, const struct tun_param *tp){
	struct sk_buff *new_skb;
	struct iphdr *old_iph;
	struct net_device *dev; 
	int ret = 0;

	u32 saddr, daddr;
	u16 sport, dport;

	struct rtable *rt;

	old_iph = get_IP_header(skb);

	daddr = tp->tr.addr;

	sport = tp->tl.port;
	dport = tp->tr.port;

	dev = dev_get_by_index(&init_net, tp->tl.ifindex);
	if (!dev) 
		return -1;

	{
		struct flowi fl = {
				.mark = tp->md->mark,
				.oif = tp->tl.ifindex, //XXX can we get rid of this?
				.nl_u = {
					.ip4_u = {
						.daddr 	= daddr,
						.tos 	= RT_TOS(old_iph->tos)
					}
				},
				.proto 	= IPPROTO_IP
		};

		if (ip_route_output_key(dev_net(dev), &rt, &fl)) {
			printk("upmt: encap - ip_route_output_key - error");
			ret = -1;
			goto end;
		}
	}

	saddr = rt->rt_src;
	if (skb_headroom(skb) < HEADROOM || skb_shared(skb) || (skb_cloned(skb) && !skb_clone_writable(skb, 0))) {
		new_skb = skb_realloc_headroom(skb, HEADROOM);
		if (!new_skb) {
			printk("upmt: can not realloc skb room");
			ret = -1;
			goto end;
		}
		if (skb->sk) skb_set_owner_w(new_skb, skb->sk);
		dev_kfree_skb(skb);
		skb = new_skb;
		old_iph = ip_hdr(skb);
	}

	skb_push(skb, HEADROOM);
	skb_reset_network_header(skb);

	memset(&(IPCB(skb)->opt), 0, sizeof(IPCB(skb)->opt));
	IPCB(skb)->flags &= ~(IPSKB_XFRM_TUNNEL_SIZE | IPSKB_XFRM_TRANSFORMED | IPSKB_REROUTED);
	skb_dst_drop(skb);
	skb_dst_set(skb, &rt->u.dst);

	set_ip_header(skb, old_iph, saddr, daddr);

	set_udp_header(skb, old_iph, sport, dport);

	nf_reset(skb);

	ip_select_ident(ip_hdr(skb), &rt->u.dst, NULL);

	skb->mark = tp->md->mark; //useless...

end:
	dev_put(dev);
	return ret;
}

MODULE_LICENSE("GPL");
