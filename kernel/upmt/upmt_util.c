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
#include <linux/utsname.h>
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
		dmesg("DEBUG");
		upmt_key->proto = skb->upmt_flow.ip_proto;
		
		dmesg("\tsaddr: %d.%d.%d.%d,   saddr_vero: %d.%d.%d.%d", 
				NIPQUAD(skb->upmt_flow.ip_src), NIPQUAD(skb->upmt_flow.ip_src));

		upmt_key->daddr = skb->upmt_flow.ip_dst;
		dmesg(" lport %d, rport %d",ntohs(skb->upmt_flow.src_port),ntohs(skb->upmt_flow.dst_port));
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

	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct net_device* dev;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(2,6,33)
	
	tl->ifindex = skb->skb_iif;
#else
	tl->ifindex = skb->iif;
#endif

	udp_header = get_UDP_header(skb);
	tl->port = ntohs(udp_header->dest);

	//Fabio Patriarca 16/09/2013
	ip_header = get_IP_header(skb);
	dev = search_dev_by_ip(ip_header->daddr);

	if(dev == NULL){
		printk("/**********/\n");
		dmesg("saddr: %pI4 daddr: %pI4", &ip_header->saddr,&ip_header->daddr);
		dmesge("set_tun_local_from_skb - dev = NULL");
		printk("/**********/\n");
	}

	if(dev != NULL) tl->ifindex = dev->ifindex;
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

void keepAlive_data_copy(struct tun_param *a, const struct tun_param *b){
	memcpy(a, b, sizeof(struct tun_param));
}

int umpt_tun_param_equals(const struct tun_param *a, const struct tun_param *b){
	int res;
	res = memcmp(a, b, (sizeof(struct tun_param)));
	dmesg("\t -------- CONFRONTO ----------");
	print_upmt_tun_param(a);
	print_upmt_tun_param(b);
	dmesg("\t -------- RISPOSTA ----------> %d", res);

	return memcmp(a, b, (sizeof(struct tun_param)));
}

/*void print_upmt_tun_param(const struct tun_param *tp){
	print_upmt_tun_id(tp->tid);
	print_upmt_tun_local(&tp->tun_local);
	print_upmt_tun_remote(&tp->tun_remote);
}*/

void dmesg_lbl(char *l1, char *l2){
	dmesg("%s\t------------------------------------------- %s", l1, l2);
}

void print_packet_information(struct sk_buff *skb, unsigned int offset){
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct tcphdr *tcp_header;
	struct icmphdr *icmp_header;
	unsigned char *result = NULL;

	if(skb == NULL){
		dmesge("print_packet_information - NULL POINTER");
		return;
	}
	result = skb_pull(skb, offset);
	if(result == NULL){
		dmesge("print_packet_information - skb_pull = NULL");
		//skb_push(skb, offset);
		return;
	}

	ip_header =	get_IP_header(skb);
	
	//dmesg("packet of interface with ifindex : %i", skb->dev->ifindex);

	dmesg("\t IPv: %u", ip_header->version);
	//dmesg(" - TotLen: %u", ntohs(ip_header->tot_len));

	// since 2.6.36 NIPQUAD has been removed, you must use %pI4 to print ipv4 addresses (Sander)
	dmesg("saddr: %pI4 daddr: %pI4", &ip_header->saddr,&ip_header->daddr);

	//dmesg("\tProtocol %u", ip_header->protocol);

	if (ip_header->protocol == IPPROTO_TCP){
		tcp_header = get_TCP_header(skb);
		dmesg("TCP src: %u dst: %u mark: %u", ntohs(tcp_header->source), ntohs(tcp_header->dest),skb->mark);
		//dmesg("\tlen: %u", (tcp_header->doff)*4);
	}

	if (ip_header->protocol == IPPROTO_UDP){
		udp_header = get_UDP_header(skb);
		dmesg("UDP src: %u dst: %u mark: %u", ntohs(udp_header->source), ntohs(udp_header->dest),skb->mark);

		//dmesg("\tlen: %u", ntohs(udp_header->len));
		//dmesg("\tCHECKSUM: %u", udp_header->check);
	}

	if (ip_header->protocol == IPPROTO_ICMP){
		dmesg("ICMP");
		icmp_header = (struct icmphdr *) skb_transport_header(skb);
	}
	skb_push(skb, offset);
}

void print_packet_information2(struct sk_buff *skb, unsigned int offset){
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct tcphdr *tcp_header;
	struct icmphdr *icmp_header;
	unsigned char *result = NULL;

	if(skb == NULL){
		dmesge("print_packet_information - NULL POINTER");
		return;
	}
	result = skb_pull(skb, offset);
	if(result == NULL){
		dmesge("print_packet_information - skb_pull = NULL");
		//skb_push(skb, offset);
		return;
	}

	ip_header =	get_IP_header(skb);
	//ip_header =	ip_hdr(skb);

	//dmesg("packet of interface with ifindex : %i", skb->dev->ifindex);

	//dmesg("\t IPv: %u", ip_header->version);
	//dmesg(" - TotLen: %u", ntohs(ip_header->tot_len));

	// since 2.6.36 NIPQUAD has been removed, you must use %pI4 to print ipv4 addresses (Sander)
	dmesg("saddr: %pI4 daddr: %pI4", &ip_header->saddr,&ip_header->daddr);

	//dmesg("\tProtocol %u", ip_header->protocol);

	if (ip_header->protocol == IPPROTO_TCP){
		tcp_header = tcp_hdr(skb);
		tcp_header = get_TCP_header(skb);
		dmesg("TCP src: %u dst: %u mark: %u", ntohs(tcp_header->source), ntohs(tcp_header->dest),skb->mark);
		//dmesg("\tlen: %u", (tcp_header->doff)*4);
	}

	if (ip_header->protocol == IPPROTO_UDP){
		udp_header = udp_hdr(skb);
		udp_header = get_UDP_header(skb);
		dmesg("UDP src: %u dst: %u mark: %u", ntohs(udp_header->source), ntohs(udp_header->dest),skb->mark);

		//dmesg("\tlen: %u", ntohs(udp_header->len));
		//dmesg("\tCHECKSUM: %u", udp_header->check);
	}

	if (ip_header->protocol == IPPROTO_ICMP){
		dmesg("ICMP");
		icmp_header = (struct icmphdr *) skb_transport_header(skb);
	}
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
	//dmesg("PAYLOAD LEN: %d ", L);
	dmesg("PAYLOAD:");
	for(i=0; i<L; i++){
		dmesg("%c", *(payload + i));
	}
	skb_push(skb, data_offset);
}

void print_UDP_packet_payload(struct sk_buff *skb, unsigned int data_offset){
	int i, L, tot_len;
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	char *payload;

	skb_pull(skb, data_offset);
	ip_header = get_IP_header(skb);
	udp_header = get_UDP_header(skb);

	payload = (char *) (skb->data + (ip_header->ihl * 4) + UDP_HEADROOM);


	tot_len = ntohs(ip_header->tot_len);
	L = tot_len - (ip_header->ihl * 4) - UDP_HEADROOM;

	//dmesg(" PAYLOAD LEN: %d ", L);
	dmesg(" --- PAYLOAD:");
	for(i=0; i<L; i++){
		printk("%c", *(payload + i));
	}
	skb_push(skb, data_offset);
}

char * get_UDP_packet_payload(struct sk_buff *skb, unsigned int data_offset){
	int L, tot_len;
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	char *payload;
	//char *result;

	skb_pull(skb, data_offset);
	ip_header = get_IP_header(skb);
	udp_header = get_UDP_header(skb);

	payload = (char *) (skb->data + (ip_header->ihl * 4) + UDP_HEADROOM);

	tot_len = ntohs(ip_header->tot_len);
	L = tot_len - (ip_header->ihl * 4) - UDP_HEADROOM;

	/*result = (char *) kzalloc(sizeof(char)*L, GFP_ATOMIC);
	if(result == NULL){
		dmesge("get_UDP_packet_payload - Unable to allocating result");
		return NULL;
	}
	memcpy(result, payload, L);*/

	/*dmesg(" --- PAYLOAD:");
	for(i=0; i<L; i++){
		dmesg("%c", *(payload + i));
	}*/

	skb_push(skb, data_offset);
	//return result;
	return payload;
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

	addr = 0;
	device = dev;
	if(device == NULL){
		if(iname != NULL) device = dev_get_by_name(upmtns->net_ns, iname);
		else device = dev_get_by_index(upmtns->net_ns, index);
		
		if(device == NULL) {
			dmesge("get_dev_ip_address - device ---> NULL");
			goto end;
		}
		else put = 1;
	}
	pin_dev = (struct in_device *) device->ip_ptr;

	if(pin_dev == NULL){
		dmesge("get_dev_ip_address - pin_dev ---> NULL");
		addr = 0;
		goto end;
	}
	if(pin_dev->ifa_list == NULL){
		dmesge("get_dev_ip_address - pin_dev->ifa_list ---> NULL");
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
		dmesge("search_dev_by_ip - mdl ---> NULL");
		return NULL;
	}
	list_for_each_entry(tmp, &mdl->list, list) {
		 
		dev_s = dev_get_by_name(upmtns->net_ns, tmp->md.iname);
		if(dev_s == NULL){
			dmesge("search_dev_by_ip - dev_s ---> NULL");
			return NULL;
	        }

		pin_dev = (struct in_device *) dev_s->ip_ptr;
		if(pin_dev == NULL){
			dmesge("search_dev_by_ip - pin_dev ---> NULL");
			return NULL;
	        }
		if(pin_dev->ifa_list == NULL){
			dmesge("search_dev_by_ip - pin_dev->ifa_list struct sockaddr *addr; ---> NULL");
			return NULL;
	        }

		adlist = pin_dev->ifa_list;

		while (adlist != NULL){
			if(ip_f == adlist->ifa_address) return dev_s;
			adlist = adlist->ifa_next;
		}
	}
	return NULL;
}

void check_context(char *message){
	printk("/********************/\n");
	printk("%s\n", message);
	dmesg("in_irq():\t\t%lu",	in_irq());
	dmesg("in_softirq():\t\t%lu",	in_softirq());
	dmesg("in_interrupt():\t\t%lu",	in_interrupt());
	dmesg("preemptible():\t\t%d",	preemptible());
	printk("/********************/\n");
}

void dmesg( const char * format, ...) // Wrapping printk to add nodename and to allow debugging with multiple module istances (Sander) 
{
    va_list ap;
    va_start(ap, format);
    printk("[upmt_%s module] ", upmtns->uts_ns->name.nodename);
    vprintk(format, ap);
    printk("\n");
    va_end(ap);
}

void dmesge( const char * format, ...) // Wrapping printk to add nodename and to allow debugging with multiple module istances (Sander)
{
    va_list ap;
    va_start(ap, format);
    printk("[upmt_%s module error] ", upmtns->uts_ns->name.nodename);
    vprintk(format, ap);
    printk("\n");
    va_end(ap);
}

MODULE_LICENSE("GPL");
