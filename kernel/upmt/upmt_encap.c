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
#include "include/upmt_ka.h"

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

static void set_ip_header(struct sk_buff *skb, const struct iphdr *old_iph, u32 saddr, u32 daddr, unsigned int piggyroom){
	struct iphdr *iph;
	iph = ip_hdr(skb);
	memcpy(iph, old_iph, sizeof(struct iphdr));
	iph->protocol 	= IPPROTO_UDP;
	iph->saddr 		= saddr;
	iph->daddr 		= daddr;
	iph->tot_len 	= htons(ntohs(old_iph->tot_len) + HEADROOM + piggyroom);
	iph->frag_off 	= htons(IP_DF); //TODO fragmentation problem
	iph->check = 0;
	skb->ip_summed = CHECKSUM_NONE;
}

static void set_udp_header(struct sk_buff *skb, const struct iphdr *old_iph, const u16 sport, const u16 dport, unsigned int piggyroom){
	struct udphdr *udph;

	udph = get_UDP_header(skb);

	udph->source = htons(sport);
	udph->dest = htons(dport);
	//udph->len = htons(len);
	udph->len = htons(ntohs(old_iph->tot_len) + UDP_HEADROOM + piggyroom);

	udph->check = 0;
	compute_UDP_checksum(skb);
}

static int CUSTOM_check_IP_checksum(struct sk_buff *skb, unsigned int offset){
	__sum16 ck;
	struct iphdr *iph;
	int ok;
	skb_pull(skb, offset);
	iph = get_IP_header(skb);

	//printk(" ---> checksum: %u\n", iph->check);

	if(iph->check == 0) return -1;

	ck = iph->check;
	iph->check = 0;
	iph->check = ip_fast_csum((unsigned char *)iph, iph->ihl);
	if(iph->check != ck){
		//printk("\n NO CHECK %u | %u \n", iph->check, ck);
		ok = -1;
		iph->check = ck;
	}
	else{
		//printk("\n YES CHECK %u | %u \n", iph->check, ck);
		ok = 0;
	}
	skb_push(skb, offset);
	return ok;
}

int isPiggy(struct sk_buff *skb){
	unsigned int lene, leni;

	struct iphdr *iph;

	if(is_ka_pkt(skb, HEADROOM) == 0) return -1;

	iph = get_IP_header(skb);
	lene = ntohs(iph->tot_len);

	//printk("PIGGY VERIFICATION\n");
	//printk(" ---> External len: %u\n", lene);

	if(lene < (ADDROOM + IP_HEADROOM)){
		//printk("Packet is too short \n");
		goto endno;
	}

	skb_pull(skb, HEADROOM);
	iph = get_IP_header(skb);
	leni = ntohs(iph->tot_len);
	//printk(" ---> Internal len: %u\n", leni);

	if((lene - leni) == HEADROOM){
		skb_push(skb, HEADROOM);
		goto endno;
	}

	skb_pull(skb, PIGGYROOM);
	iph = get_IP_header(skb);
	leni = ntohs(iph->tot_len);
	//printk(" ---> Internal len: %u\n", leni);
	skb_push(skb, ADDROOM);

	if((lene - leni) == ADDROOM){
		goto endsi;
	}
	else goto endno;

	goto endno;

endsi:
	return 0;
endno:
	return -1;
}

int _isPiggy(struct sk_buff *skb){
	unsigned int len;

	struct iphdr *iph;

	if(is_ka_pkt(skb, HEADROOM) == 0) return -1;

	iph = ip_hdr(skb);
	len = ntohs(iph->tot_len);

	//printk("*****************************\n");
	//printk("PIGGY VERIFICATION\n");
	//print_packet_information2(skb, 0);

	if(len < (ADDROOM + IP_HEADROOM)){
		//printk("Packet is too short \n");
		goto endno;
	}

	//print_packet_information2(skb, HEADROOM);
	//print_packet_information2(skb, ADDROOM);

	if(CUSTOM_check_IP_checksum(skb, HEADROOM) >= 0){
		//printk("IP checksum (HEADROOM) SUCCESSFUL\n");
		goto endno;
	}
	//else printk("IP checksum (HEADROOM) FAILED\n");

	if(CUSTOM_check_IP_checksum(skb, ADDROOM) >= 0){
		//printk("IP checksum (ADDROOM) SUCCESSFUL\n");
		goto endsi;
	}
	//else printk("IP checksum (ADDROOM) FAILED\n");

	goto endno;

endsi:
	return 0;
endno:
	return -1;
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
		dmesge("denat_packet - pin_dev = NULL");
		return;
	}
	if(pin_dev->ifa_list == NULL){
		dmesge("denat_packet - pin_dev->ifa_list = NULL");
		return;
	}
	/*if(pin_dev->ifa_list->ifa_address == NULL){
		dmesg("denat_packet - error - pin_dev->ifa_list->ifa_address ---> NULL");
		return;
	}*/
	//dmesg("IP of upmt_dev: %u ---> ", pin_dev->ifa_list->ifa_address);
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

int decap(struct sk_buff *skb, struct tunt_entry *te){
	struct ka_payload *kap;

	if(isPiggy(skb) == 0){
		skb_pull(skb, HEADROOM);
		kap = (struct ka_payload *) skb->data;

		//if(kap->type == KEEP_ALIVE_REQUEST)		printk("Receiving PIGGY Keep-Alive request...\n");
		//if(kap->type == KEEP_ALIVE_RESPONSE)	printk("Receiving PIGGY Keep-Alive response...\n");

		//printk("TID: %d\n", kap->tid);
		//print_ka_info(kap->info);

		manage_piggy_keep_alive(te, kap);

		skb_pull(skb, PIGGYROOM);
	}
	else{
		//printk("\nNormal packet received\n");
		skb_pull(skb, HEADROOM);
	}

	skb_reset_network_header(skb);

	skb->dev = upmt_dev;

	mark_packet(skb);

	return 0;
}

//int encap(struct sk_buff *skb, struct tun_param *tp, unsigned int mark, struct ka_payload *kap){
int encap(struct sk_buff *skb, struct tun_param *tp, unsigned int mark, struct ka_payload *kap){
	struct sk_buff *new_skb;
	struct iphdr *old_iph;
	//struct iphdr *iph;
	struct net_device *dev; 
	int ret = 0;
	struct sock tmpsk;
	struct udphdr *udph;

	u32 saddr, daddr;
	u16 sport, dport;

	struct rtable *rt;
	struct flowi4 fl4;

	unsigned int pushroom, piggyroom;

	piggyroom = 0;
	if(kap->tid == 0) piggyroom = 0;
	else piggyroom = PIGGYROOM;
	pushroom = HEADROOM + piggyroom;

	old_iph = get_IP_header(skb);

	daddr = tp->tr.addr;

	sport = tp->tl.port;
	dport = tp->tr.port;

	// since 2.6.36 rtable no longer has rt_src attribute, now I get source address from the device (Sander)
	dev = dev_get_by_index(upmtns->net_ns, tp->tl.ifindex);

	if (!dev) {
		dmesge("no device found during encap");
		return -1;
	}
	saddr = get_dev_ip_address(dev, NULL, 0);

	// since 2.6.39 ip_route_output_key flowi parameter has been replaced with flowi4, rtable no longer has rt_src attribute
	// ip_route_output_key function now returns an rtable and should be replaced with ip_route_output_ports (Sander)
	
	tmpsk.sk_mark = mark;
	rt = ip_route_output_ports(dev_net(dev), &fl4, &tmpsk, daddr, 0, 0, 0, IPPROTO_IP, RT_TOS(old_iph->tos), tp->tl.ifindex);	

	if (!rt) {
		dmesge("encap - no rtable found during encap");
		ret = -1;
		goto end;
	}

	if (skb_headroom(skb) < pushroom || skb_shared(skb) || (skb_cloned(skb) && !skb_clone_writable(skb, 0))) {
		new_skb = skb_realloc_headroom(skb, pushroom);
		printk(" --- socket buffer REALLOCATION\n");
		if (!new_skb) {
			dmesge("encap - can not realloc skb room");
			ret = -1;
			goto end;
		}
		if (skb->sk) skb_set_owner_w(new_skb, skb->sk);
		dev_kfree_skb_any(skb);
		skb = new_skb;
		old_iph = ip_hdr(skb);
	}

	if(piggyroom > 0){
		skb_push(skb, PIGGYROOM);
		//print_ka_info(kap->info);
		memcpy(skb->data, kap, sizeof(struct ka_payload));
		skb_push(skb, HEADROOM);
	}
	else{
		//printk("Creating normal packet...\n");
		skb_push(skb, HEADROOM);
	}

	skb_reset_network_header(skb);

	memset(&(IPCB(skb)->opt), 0, sizeof(IPCB(skb)->opt));
	IPCB(skb)->flags &= ~(IPSKB_XFRM_TUNNEL_SIZE | IPSKB_XFRM_TRANSFORMED | IPSKB_REROUTED);
	skb_dst_drop(skb);
	// since 2.6.36 rtable has no 'u' attribute, you can use dst directly (Sander)
	skb_dst_set(skb, &rt->dst);

	set_ip_header(skb, old_iph, saddr, daddr, piggyroom);

	set_udp_header(skb, old_iph, sport, dport, piggyroom);

	nf_reset(skb);
	// since 2.6.36 rtable has no 'u' attribute, you can use dst directly (Sander)
	ip_select_ident(ip_hdr(skb), &rt->dst, NULL);

	skb->mark = mark; //useless...

	/*************/
	udph = get_UDP_header(skb);
	//dmesg("ENCAP ---> UDP src: %u dst: %u mark: %u", ntohs(udph->source), ntohs(udph->dest),skb->mark);

end:
	dev_put(dev);
	return ret;
}

MODULE_LICENSE("GPL");
