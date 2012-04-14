/*
 * upmt_util.h
 *
 *  Created on: 01/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_UTIL_H_
#define UPMT_UTIL_H_

#include "upmt.h"

#include <linux/ip.h>
#include <linux/tcp.h>
#include <linux/udp.h>
#include <linux/skbuff.h>
#include <linux/hardirq.h>

struct iphdr* 	get_IP_header(const struct sk_buff *);
struct tcphdr* 	get_TCP_header(const struct sk_buff *);
struct udphdr* 	get_UDP_header(const struct sk_buff *);

void 	set_upmt_key(struct upmt_key *, u16, u32, u32, u16, u16);
int 	set_upmt_key_from_skb(struct upmt_key *, struct sk_buff *, int);
int 	set_upmt_key_from_remote_skb(struct upmt_key *, struct sk_buff *, int);
void 	upmt_key_copy(struct upmt_key *, const struct upmt_key *);
int 	umpt_key_equals(const struct upmt_key *, const struct upmt_key *);

void 	set_tun_local(struct tun_local *, const int, const unsigned int);
void 	set_tun_local_from_skb(struct tun_local *, const struct sk_buff *);
void 	tun_local_copy(struct tun_local *, const struct tun_local *);
int 	umpt_tun_local_equals(const struct tun_local *, const struct tun_local *);

void 	set_tun_remote(struct tun_remote *, const unsigned int, const unsigned int);
void 	set_tun_remote_from_skb(struct tun_remote *, const struct sk_buff *);
int 	umpt_tun_remote_equals(const struct tun_remote *, const struct tun_remote *);

void 	tun_param_copy(struct tun_param *, const struct tun_param *);
int 	umpt_tun_param_equals(const struct tun_param *, const struct tun_param *);

void 	print_packet_information(struct sk_buff *, unsigned int);
void 	print_TCP_packet_payload(struct sk_buff *, unsigned int);

__sum16 compute_UDP_checksum(struct sk_buff *);
__sum16 compute_TCP_checksum(struct sk_buff *);
__sum16 compute_TRANSPORT_checksum(struct sk_buff *skb);

int 	check_IP_checksum(struct sk_buff *, unsigned int);
int 	check_UDP_checksum(struct sk_buff *, unsigned int);
int 	check_TCP_checksum(struct sk_buff *, unsigned int);
int 	check_TRANSPORT_checksum(struct sk_buff *, unsigned int);

u32 	get_dev_ip_address(struct net_device *, char *, int); //warning, this function returns only the first of the IP ifaddr chain

struct	net_device* search_dev_by_ip(u32 ip_f);//This function returns the device from its IP

void check_context(void);

#endif /* UPMT_UTIL_H_ */
