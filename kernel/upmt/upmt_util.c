/*
 * upmt_util.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_util.h"
#include "include/upmt_stamp.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>
#include <linux/skbuff.h>
#include <linux/inetdevice.h>
#include "include/upmt_mdl.h"


struct iphdr* get_IP_header(const struct sk_buff *skb){
	/*struct iphdr *iph;
	iph = ip_hdr(skb);
	return ((struct tcphdr *) (skb->data + (iph->ihl*4)));*/
	return (struct iphdr *) (skb->data);
}

struct tcphdr* get_TCP_header(const struct sk_buff *skb){
	struct iphdr *iph;
	//iph = ip_hdr(skb);
	iph = get_IP_header(skb);
	return ((struct tcphdr *) (skb->data + (iph->ihl*4)));
	//return ((struct tcphdr *) (skb->data + sizeof(struct iphdr)));
}

struct udphdr* get_UDP_header(const struct sk_buff *skb){
	struct iphdr *iph;
	//iph = ip_hdr(skb);
	iph = get_IP_header(skb);
	return ((struct udphdr *) (skb->data + (iph->ihl*4)));
	//return ((struct udphdr *) (skb->data + sizeof(struct iphdr)));
}

void set_upmt_key(struct upmt_key *k, u16 proto, u32 saddr, u32 daddr, u16 sport, u16 dport){
	k->proto = proto;
	k->saddr = saddr;
	k->daddr = daddr;
	k->sport = sport;
	k->dport = dport;
}

void upmt_key_copy(struct upmt_key *a, const struct upmt_key *b){
	memcpy(a, b, sizeof(struct upmt_key));
}

int umpt_key_equals(const struct upmt_key *a, const struct upmt_key *b){
	if(a->proto != b->proto) return 1;
	if(a->saddr != b->saddr) return 1;
	if(a->daddr != b->daddr) return 1;
	if(a->sport != b->sport) return 1;
	if(a->dport != b->dport) return 1;
	return 0;
	//return memcmp(a, b, (sizeof(struct UPMT_KEY)));
}

int set_upmt_key_from_skb(struct upmt_key *upmt_key, struct sk_buff *skb, int offset){
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct tcphdr *tcp_header;

	skb_pull(skb, offset);
	ip_header = get_IP_header(skb);
	upmt_key->proto = ip_header->protocol;
	upmt_key->saddr = ip_header->saddr;
	upmt_key->daddr = ip_header->daddr;

#if 0
#ifdef UPMT_S
		printk("DEBUG");
		upmt_key->proto = skb->upmt_flow.ip_proto;
		
		printk("\tsaddr: %d.%d.%d.%d,   saddr_vero: %d.%d.%d.%d", 
				NIPQUAD(skb->upmt_flow.ip_src), NIPQUAD(skb->upmt_flow.ip_src));

		upmt_key->daddr = skb->upmt_flow.ip_dst;
		printk(" lport %d, rport %d\n",ntohs(skb->upmt_flow.src_port),ntohs(skb->upmt_flow.dst_port));
#endif
#endif

	if (ip_header->protocol == IPPROTO_TCP){
		tcp_header = get_TCP_header(skb);
		upmt_key->sport = ntohs(tcp_header->source);
		upmt_key->dport = ntohs(tcp_header->dest);
		skb_push(skb, offset);
		return 0;
	}

	if (ip_header->protocol == IPPROTO_UDP){
		udp_header = get_UDP_header(skb);
		upmt_key->sport = ntohs(udp_header->source);
		upmt_key->dport = ntohs(udp_header->dest);
		skb_push(skb, offset);
		return 0;
	}

#ifdef UPMT_S
	//for UPMT-S - MARCO
	if (ip_header->protocol == IPPROTO_ESP){
		//udp_header = get_UDP_header(skb);
		upmt_key->proto = skb->upmt_flow.ip_proto;
		upmt_key->saddr = skb->upmt_flow.ip_src;
		upmt_key->daddr = skb->upmt_flow.ip_dst;
		upmt_key->sport = ntohs(skb->upmt_flow.src_port);
		upmt_key->dport = ntohs(skb->upmt_flow.dst_port);
		skb_push(skb, offset);
		return 0;
	}
#endif

	skb_push(skb, offset);
	return -1;
}

int set_upmt_key_from_remote_skb(struct upmt_key *upmt_key, struct sk_buff *skb, int offset){
	int res;
	u16 port;
	u32 addr;
	res = set_upmt_key_from_skb(upmt_key, skb, offset);
	if (res < 0) return res;

	addr = upmt_key->saddr;
	upmt_key->saddr = upmt_key->daddr;
	upmt_key->daddr = addr;

	port = upmt_key->sport;
	upmt_key->sport = upmt_key->dport;
	upmt_key->dport = port;

	return 0;
}

void set_tun_local(struct tun_local *tl, const int ilink, const unsigned int port){
	tl->ifindex = ilink;
	tl->port = port;
}


void set_tun_local_from_skb(struct tun_local *tl, const struct sk_buff *skb){

	struct udphdr *udp_header;
#ifdef RME
	struct net_device* dev_f;
	struct iphdr *ip_header;

	ip_header = get_IP_header(skb);
	
	if(((dev_f = search_dev_by_ip(ip_header->daddr))!=NULL) && (skb->skb_iif!=dev_f->ifindex)){
		tl->ifindex = dev_f->ifindex;
	}
	else {
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(2,6,33)
	
		tl->ifindex = skb->skb_iif;
#else
		tl->ifindex = skb->iif;
#endif
#ifdef RME
	}
#endif
	udp_header = get_UDP_header(skb);
	tl->port = ntohs(udp_header->dest);
}

void tun_local_copy(struct tun_local *a, const struct tun_local *b){
	memcpy(a, b, sizeof(struct tun_local));
}


int umpt_tun_local_equals(const struct tun_local *a, const struct tun_local *b){
	if(a->ifindex != b->ifindex) 	return 1;
	if(a->port != b->port) 			return 1;
	return 0;
	//return memcmp(a, b, (sizeof(struct UPMT_KEY)));
}

void set_tun_remote(struct tun_remote *tr, const unsigned int addr, const unsigned int port){
	tr->addr = addr;
	tr->port = port;
}

void set_tun_remote_from_skb(struct tun_remote *tr, const struct sk_buff *skb){
	struct iphdr *iph;
	struct udphdr *udph;

	iph = get_IP_header(skb);
	udph = get_UDP_header(skb);

	tr->addr = iph->saddr;
	tr->port = ntohs(udph->source);
}

int umpt_tun_remote_equals(const struct tun_remote *a, const struct tun_remote *b){
	if(a->addr != b->addr) 	return 1;
	if(a->port != b->port)	return 1;
	return 0;
	//return memcmp(a, b, (sizeof(struct tun_remote)));
}

void tun_param_copy(struct tun_param *a, const struct tun_param *b){
	memcpy(a, b, sizeof(struct tun_param));
}

int umpt_tun_param_equals(const struct tun_param *a, const struct tun_param *b){
	int res;
	res = memcmp(a, b, (sizeof(struct tun_param)));
	printk("\n\t -------- CONFRONTO ----------");
	print_upmt_tun_param(a);
	print_upmt_tun_param(b);
	printk("\n\t -------- RISPOSTA ----------> %d", res);

	return memcmp(a, b, (sizeof(struct tun_param)));
}

/*void print_upmt_tun_param(const struct tun_param *tp){
	print_upmt_tun_id(tp->tid);
	print_upmt_tun_local(&tp->tun_local);
	print_upmt_tun_remote(&tp->tun_remote);
}*/

void print_packet_information(struct sk_buff *skb, unsigned int offset){
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct tcphdr *tcp_header;
	struct icmphdr *icmp_header;

	if(skb == NULL){
		printk("PUNTATORE NULL\n");
		return;
	}
	skb_pull(skb, offset);

	ip_header =	get_IP_header(skb);

	printk("\n");
	//printk("\t IPv: %u", ip_header->version);
	//printk(" - TotLen: %u", ntohs(ip_header->tot_len));

	printk(" saddr: %d.%d.%d.%d", NIPQUAD(ip_header->saddr));
	printk(" daddr: %d.%d.%d.%d\t", NIPQUAD(ip_header->daddr));

	//printk("\tProtocol %u", ip_header->protocol);

	if (ip_header->protocol == IPPROTO_TCP){
		tcp_header = get_TCP_header(skb);
		printk("| TCP");
		printk("\tsrc: %u", ntohs(tcp_header->source));
		printk("\tdst: %u", ntohs(tcp_header->dest));
		//printk("\tlen: %u", (tcp_header->doff)*4);
	}

	if (ip_header->protocol == IPPROTO_UDP){
		udp_header = get_UDP_header(skb);
		printk("| UDP");
		printk("\tsrc: %u", ntohs(udp_header->source));
		printk("\tdst: %u", ntohs(udp_header->dest));
		//printk("\tlen: %u", ntohs(udp_header->len));
		//printk("\tCHECKSUM: %u", udp_header->check);
	}

	if (ip_header->protocol == IPPROTO_ICMP){
		printk("| ICMP");
		icmp_header = (struct icmphdr *) skb_transport_header(skb);
	}
	printk("\tmark: %u", skb->mark);
	skb_push(skb, offset);
}

void print_TCP_packet_payload(struct sk_buff *skb, unsigned int data_offset){
	int i, L, tot_len;
	struct iphdr *ip_header;
	struct tcphdr *tcp_header;
	char *payload;

	skb_pull(skb, data_offset);
	ip_header = get_IP_header(skb);
	tcp_header = get_TCP_header(skb);
	payload = (char *) (skb->data + (ip_header->ihl * 4) + (tcp_header->doff * 4));
	tot_len = ntohs(ip_header->tot_len);
	L = tot_len - (ip_header->ihl * 4) - (tcp_header->doff * 4);
	//printk("\n\n PAYLOAD LEN: %d ", L);
	printk("\n PAYLOAD:\n");
	for(i=0; i<L; i++){
		printk("%c", *(payload + i));
	}
	skb_push(skb, data_offset);
}

__sum16 compute_UDP_checksum(struct sk_buff *skb){
	__sum16 ck;
	__wsum	csum;
	unsigned short udp_len;
	struct iphdr *iph;
	struct udphdr *udph;

	iph = get_IP_header(skb);
	udph = get_UDP_header(skb);
	udp_len = ntohs(udph->len);
	udph->check = 0;
	csum = csum_partial(udph, udp_len, 0);
	ck = csum_tcpudp_magic(iph->saddr, iph->daddr, udp_len, IPPROTO_UDP, csum);
	udph->check = ck;

	return ck;
}

__sum16 compute_TCP_checksum(struct sk_buff *skb){
	__sum16 ck;
	__wsum	csum;
	unsigned short ip_len, tcp_len;
	struct iphdr *iph;
	struct tcphdr *tcph;

	iph = get_IP_header(skb);
	tcph = get_TCP_header(skb);
	ip_len = ntohs(iph->tot_len);
	tcp_len = ip_len - (iph->ihl*4);
	tcph->check = 0;
	csum = csum_partial(tcph, tcp_len, 0);
	ck = csum_tcpudp_magic(iph->saddr, iph->daddr, tcp_len, IPPROTO_TCP, csum);
	tcph->check = ck;

	return ck;
}

__sum16 compute_TRANSPORT_checksum(struct sk_buff *skb){
	__sum16 ck = 0;
	struct iphdr *iph;
	iph = get_IP_header(skb);
	if (iph->protocol == IPPROTO_TCP) ck = compute_TCP_checksum(skb);
	if (iph->protocol == IPPROTO_UDP) ck = compute_UDP_checksum(skb);
	return ck;
}

int check_IP_checksum(struct sk_buff *skb, unsigned int offset){
	__sum16 ck;
	struct iphdr *iph;
	int ok;
	skb_pull(skb, offset);
	iph = get_IP_header(skb);
	ck = iph->check;
	iph->check = 0;
	iph->check = ip_fast_csum((unsigned char *)iph, iph->ihl);
	if(iph->check != ck){
		ok = -1;
		iph->check = ck;
	}
	else ok = 0;
	skb_push(skb, offset);
	return ok;
}

int check_UDP_checksum(struct sk_buff *skb, unsigned int offset){
	__sum16 ck;
	struct udphdr *udph;
	int ok = 0;

	skb_pull(skb, offset);
	udph = get_UDP_header(skb);
	ck = udph->check;
	if(ck == 0) goto out;

	//udph->check = compute_UDP_checksum(skb);
	compute_UDP_checksum(skb);
	if(udph->check != ck){
		udph->check = ck;
		ok = -1;
	}

out:
	skb_push(skb, offset);
	return ok;
}

int check_TCP_checksum(struct sk_buff *skb, unsigned int offset){
	__sum16 ck;
	struct tcphdr *tcph;
	int ok;

	skb_pull(skb, offset);
	tcph = get_TCP_header(skb);
	ck = tcph->check;

	compute_TCP_checksum(skb);
	if(tcph->check != ck){
		tcph->check = ck;
		ok = -1;
	}
	else ok = 0;

	skb_push(skb, offset);
	return ok;
}

int check_TRANSPORT_checksum(struct sk_buff *skb, unsigned int offset){
	int ok = -1;
	struct iphdr *iph;

	skb_pull(skb, offset);
	iph = get_IP_header(skb);
	if (iph->protocol == IPPROTO_TCP) ok = check_TCP_checksum(skb, 0);
	if (iph->protocol == IPPROTO_UDP) ok = check_UDP_checksum(skb, 0);
	skb_push(skb, offset);

	return ok;
}

u32 get_dev_ip_address(struct net_device *dev, char *iname, int index){
	u32 addr;
	struct net_device *device = NULL;
	struct in_device *pin_dev;
	int put = 0;

	device = dev;
	if(device == NULL){
		if(iname != NULL) device = dev_get_by_name(&init_net, iname);
		else device = dev_get_by_index(&init_net, index);
		
		if(device == NULL) {
			addr = 0;
			goto end;
		}
		else
			put = 1;
	}
	pin_dev = (struct in_device *) device->ip_ptr;

	if(pin_dev == NULL){
		printk("\n\t get_dev_ip_address - error - pin_dev ---> NULL");
		addr = 0;
		goto end;
	}
	if(pin_dev->ifa_list == NULL){
		printk("\n\t get_dev_ip_address - error - pin_dev->ifa_list ---> NULL");
		addr = 0;
		goto end;
	}

	addr = pin_dev->ifa_list->ifa_address;

end:
	if (put) dev_put(device);
	return addr;
}

struct net_device* search_dev_by_ip(u32 ip_f){

	struct net_device *dev_s = NULL;
	struct in_device *pin_dev;
	struct mdl_entry *tmp;
	struct in_ifaddr *adlist;

	if(mdl == NULL){
			printk("\n\t search_dev_by_ip - error - mdl ---> NULL");
			return NULL;
	}
	list_for_each_entry(tmp, &mdl->list, list) {
		 
		dev_s = dev_get_by_name(&init_net, tmp->md.iname);
		if(dev_s == NULL){
			printk("\n\t search_dev_by_ip - error - dev_s ---> NULL");
			return NULL;
	        }

		pin_dev = (struct in_device *) dev_s->ip_ptr;
		if(pin_dev == NULL){
			printk("\n\t search_dev_by_ip - error - pin_dev ---> NULL");
			return NULL;
	        }
		if(pin_dev->ifa_list == NULL){
			printk("\n\t search_dev_by_ip - error - pin_dev->ifa_list struct sockaddr *addr; ---> NULL");
			return NULL;
	        }

		adlist = pin_dev->ifa_list;

		while (adlist != NULL){
			if(ip_f == adlist->ifa_address ) return dev_s;
			adlist=adlist->ifa_next;
		}
	}
	return NULL;
}

void check_context(void){
	printk("\n\t ---> in_irq():      \t%lu", in_irq());
	printk("\n\t ---> in_softirq():  \t%lu", in_softirq());
	printk("\n\t ---> in_interrupt():\t%lu", in_interrupt());
	printk("\n\t ---> preemptible(): \t%d", preemptible());
}

MODULE_LICENSE("GPL");
