
#include "include/upmt.h"
#include "include/upmt_ka.h"
#include "include/upmt_util.h"
#include "include/upmt_encap.h"

#include "include/upmt_stamp.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>
#include <linux/ktime.h>

#include <net/ip.h>
#include <net/route.h>
#include <net/genetlink.h>

void print_ka_info(struct keep_alive_info info){
	u64 val;

	//printk("\nKEEP-ALIVE INFO \n");

	printk("\nRTT INFO \n");

	val = to32bit(info.Sc);
	printk("Sc: %llu\n", val);

	val = to32bit(info.Rc);
	printk("Rc: %llu\n", val);

	val = to32bit(info.Ss);
	printk("Ss: %llu\n", val);

	val = to32bit(info.Rs);
	printk("Rs: %llu\n", val);

	/*printk("\nLOSS INFO \n");
	printk("Sc: %lu\n", info.pSc);
	printk("Rc: %lu\n", info.pRc);
	printk("Ss: %lu\n", info.pSs);
	printk("Rs: %lu\n", info.pRs);*/

	printk("\n");
}

void print_tun_loss_info(struct tunt_entry *te, int MODE){
	struct keep_alive kp = te->kp;

	if(MODE == KEEP_ALIVE_CLIENT_MODE){
		printk("TUN LOSS INFO - CLIENT\n");
		printk("Sc: %lu\n", kp.info.pSc);
		printk("Rc: %lu\n", kp.info.pRc);
		printk("Sc_last: %lu\n", kp.pSc_last);
		printk("Rs_last: %lu\n", kp.pRs_last);
		//printk("Ss_tmp: %lu\n", kp.pSs_tmp);
		//printk("Rc_tmp: %lu\n", kp.pRc_tmp);
	}

	if(MODE == KEEP_ALIVE_SERVER_MODE){
		printk("\n TUN LOSS INFO - SERVER\n");
		printk("Ss: %lu\n", kp.info.pSs);
		printk("Rs: %lu\n", kp.info.pRs);
		printk("Ss_last: %lu\n", kp.pSs_last);
		printk("Rc_last: %lu\n", kp.pRc_last);
		//printk("Sc_tmp: %lu\n", kp.pSc_tmp);
		//printk("Rs_tmp: %lu\n", kp.pRs_tmp);
	}

	printk("\n");
}

unsigned long getMSecJiffies(void){
	unsigned long m = jiffies * 1000 / HZ;
	return m;
}

void to24bit(unsigned char *dest, u64 val){
	val = val >> 4;

	dest[0] = val % 256;

	val = val >> 8;
	dest[1] = val % 256;

	val = val >> 8;
	dest[2] = val % 256;
}

u32 to32bit(unsigned char *dest){
	u32 val;

	val = dest[2];
	val = val << 8;

	val += dest[1];
	val = val << 8;

	val += dest[0];

	val = val << 4;
	return val;
}

u64 getMSecJiffies2(void){
	u64 usec, nsec;
	u64 million, billion;
	struct timeval tv;
	struct timespec ts;

	do_gettimeofday(&tv);
	million = 1000000;
	usec = tv.tv_sec;
	usec = usec * million + tv.tv_usec;
//	printk("Taking MICRO time - sec:\t %ld\n", tv.tv_sec);
//	printk("Taking MICRO time - usec:\t %ld\n", tv.tv_usec);
//	printk("Taking MICRO time - TOTAL usec:\t %llu\n", usec);


	getnstimeofday(&ts);
	billion = 1000000000;
	nsec = ts.tv_sec;
	nsec = nsec * billion + ts.tv_nsec;
//	printk("Taking NANO time - sec:\t %ld\n", ts.tv_sec);
//	printk("Taking NANO time - nsec:\t %ld\n", ts.tv_nsec);
//	printk("Taking NANO time - TOTAL nsec:\t %llu\n", nsec);

	return usec;
}

unsigned long getJiffiesMSec(unsigned long ms){
	unsigned long m = ms * HZ / 1000;
	return m;
}

/*int is_keep_alive_pkt(struct sk_buff *skb, int headroom){
	return -1;
}*/

int is_ka_request(struct upmt_key *key){
	if(key->sport != KEEP_ALIVE_SRC_PORT)	return -1;
	if(key->dport != KEEP_ALIVE_DST_PORT)	return -1;
	return 0;
}

int is_ka_response(struct upmt_key *key){
	if(key->dport != KEEP_ALIVE_SRC_PORT)	return -1;
	if(key->sport != KEEP_ALIVE_DST_PORT)	return -1;
	return 0;
}

int is_ka_pkt(struct sk_buff *skb, int offset){
	struct upmt_key key;
	set_upmt_key_from_skb(&key, skb, offset);
	if(is_ka_request(&key) == 0) return 0;
	if(is_ka_response(&key) == 0)  return 0;
	return -1;
}

int isTimeout(struct tunt_entry *tmp){
	unsigned long now, last;

	now = getMSecJiffies();
	last = tmp->kp.tstamp_recv;

	if((now - last) > T_TO) return 0;
	else return -1;
}

unsigned long compute_LOSS_client(struct tunt_entry *te){
	unsigned long loss;
	if(te->kp.sent >= te->kp.recv)	loss = te->kp.sent - te->kp.recv;
	else loss = 0;
	return loss;
}

unsigned long compute_LOSS_server(struct tunt_entry *te){
	unsigned long loss;
	if(te->kp.sent >= te->kp.recv)	loss = te->kp.sent - te->kp.recv + 1; //the first keep-alive from client has to be excluded
	else loss = 0;
	return loss;
}

unsigned long compute_LOSS(struct tunt_entry *te, struct ka_payload *payload, int MODE){
	unsigned long Sc, Rs;
	unsigned long Ss, Rc;
	unsigned long S = 0, R = 0;
	unsigned long owl = 0;
	if(MODE == KEEP_ALIVE_CLIENT_MODE){

		print_tun_loss_info(te, KEEP_ALIVE_CLIENT_MODE);

		Sc = payload->info.pSc;
		Rs = payload->info.pRs;

		//printk("Received Sc: %lu\n", Sc);
		//printk("Received Rs: %lu\n", Rs);

		S = Sc - te->kp.pSc_last;
		R = Rs - te->kp.pRs_last;

		te->kp.pSc_last = Sc;
		te->kp.pRs_last = Rs;
	}
	if(MODE == KEEP_ALIVE_SERVER_MODE){

		print_tun_loss_info(te, KEEP_ALIVE_SERVER_MODE);

		Ss = payload->info.pSs;
		Rc = payload->info.pRc;

		//printk("Received Ss: %lu\n", Ss);
		//printk("Received Rc: %lu\n", Rc);

		S = Ss - te->kp.pSs_last;
		R = Rc - te->kp.pRc_last;

		te->kp.pSs_last = Ss;
		te->kp.pRc_last = Rc;
	}

	//printk("Computed S: %lu\n", S);
	//printk("Computed R: %lu\n", R);
	//printk("Operation: 1000 - (%lu * 1000) / %lu \n", R, S);

	if(S == 0) owl = 0;
	else owl = 1000 - (R*1000) / S;

	//printk(" ---> OWL: %lu\n", owl);
	return owl;
}

unsigned long compute_RTT(struct tunt_entry *te, int MODE){
	unsigned long rtt, rtta, rttb, Sc, Rc, Ss, Rs;
	//unsigned long MAX = 16777216;
	unsigned long MAX = 268435456;

	Sc = to32bit(te->kp.info.Sc);
	Rc = to32bit(te->kp.info.Rc);
	Ss = to32bit(te->kp.info.Ss);
	Rs = to32bit(te->kp.info.Rs);

	rtta = 0;
	rttb = 0;
	if((MODE != KEEP_ALIVE_CLIENT_MODE)&&(MODE != KEEP_ALIVE_SERVER_MODE)) dmesge("compute_RTT - unknow mode");

	print_ka_info(te->kp.info);
	if(MODE == KEEP_ALIVE_CLIENT_MODE){
		//printk("KEEP_ALIVE_CLIENT_MODE\n");

		if(Rc < Sc) rtta = MAX + Rc - Sc;
		else rtta = Rc - Sc;

		if(Ss < Rs) rttb = MAX + Ss - Rs;
		else rttb = Ss - Rs;
	}

	if(MODE == KEEP_ALIVE_SERVER_MODE){
		//printk("KEEP_ALIVE_SERVER_MODE\n");
		rtta = Rs - Ss;
		rttb = Sc - Rc;

		if(Rs < Ss) rtta = MAX + Rs - Ss;
		else rtta = Rs - Ss;

		if(Sc < Rc) rttb = MAX + Sc - Rc;
		else rttb = Sc - Rc;
	}

	rtt = rtta - rttb;

	rtt = rtt / 1000; //from microseconds to milliseconds

	if(rtta < rttb){
		//printk(" ---> QUANTUM PHYSICS\n");
		//print_ka_info(te->kp.info);
	}
	return rtt;
}

void set_ka_ip_header(struct sk_buff *skb, u32 saddr, u32 daddr, u16 len, int checksum){
	struct iphdr *iph = NULL;
	skb_push(skb, IP_HEADROOM);
	skb_reset_network_header(skb);
	iph = ip_hdr(skb);
	iph->ihl		= 5;
	iph->version	= 4;
	iph->tos		= 0;
	iph->tot_len	= htons(len);
	iph->id			= 0;
	iph->frag_off 	= htons(IP_DF);
	iph->ttl		= 64;
	//iph->ttl = ip_select_ttl(inet, &rt->u.dst);
	iph->protocol	= IPPROTO_UDP;
	iph->saddr		= saddr;
	iph->daddr		= daddr;
	if(checksum == 1)	ip_send_check(iph);
	else iph->check		= 0;
}

void set_ka_udp_header(struct sk_buff *skb, u32 saddr, u32 daddr, u16 sport, u16 dport, u16 len){
	struct udphdr *udph = NULL;
	skb_push(skb, UDP_HEADROOM);
	skb_reset_transport_header(skb);
	udph = udp_hdr(skb);
	udph->source	= htons(sport);
	udph->dest		= htons(dport);
	udph->len		= htons(len);
	udph->check		= 0;
}

static struct sk_buff * alloc_keep_alive_skb(struct tunt_entry *te, u32 saddr, u32 daddr, u16 sport, u16 dport, struct ka_payload *payload){
	u32 SADDR, DADDR;
	struct sk_buff *skb;
	struct net_device *dev;
	int DATA_LEN;

	//dmesg("alloc_keep_alive_skb - BEGIN");

	DATA_LEN = sizeof(struct ka_payload);
	//printk("DATA_LEN: %d\n", DATA_LEN);

	skb = alloc_skb(2*HEADROOM + DATA_LEN, GFP_ATOMIC);
	if(skb == NULL){
		dmesge("alloc_keep_alive_skb - unable to alloc_skb");
		goto end;
	}

	skb_reserve(skb, 2*HEADROOM);
	skb_put(skb, DATA_LEN);
	memcpy(skb->data, payload, DATA_LEN);

	dev = dev_get_by_index(&init_net, te->tp.tl.ifindex);
	if(dev == NULL){
		dmesge("alloc_keep_alive_skb - dev = NULL");
		goto error;
	}
	SADDR = get_dev_ip_address(dev, NULL, 0);
	DADDR = te->tp.tr.addr;

	skb->dev = dev;
	skb->protocol = htons(ETH_P_IP);
	skb->pkt_type = PACKET_OUTGOING;
	skb->ip_summed = CHECKSUM_NONE;

	//dmesg("alloc_keep_alive_skb - MIDDLE");

	set_ka_udp_header(skb, saddr, daddr, sport, dport, UDP_HEADROOM + DATA_LEN);
	set_ka_ip_header(skb, saddr, daddr, HEADROOM + DATA_LEN, 1);

	if(te->tp.in.local != 0) inat_packet(skb, &te->tp);

	set_ka_udp_header(skb, SADDR, DADDR, te->tp.tl.port, te->tp.tr.port, HEADROOM + UDP_HEADROOM + DATA_LEN);
	set_ka_ip_header(skb, SADDR, DADDR, 2*HEADROOM + DATA_LEN, 0);

	skb->skb_iif = te->tp.tl.ifindex;

	goto end;

error:
	dev_kfree_skb_any(skb);
	skb = NULL;
end:
	//dmesg("alloc_keep_alive_skb - END");
	return skb;
}

void create_keep_alive_payload(struct tunt_entry *te, struct ka_payload *payload, int mode){

	u64 Sc, Ss;
	memset(&payload->info, 0, sizeof(struct keep_alive_info));

	if(mode == KEEP_ALIVE_CLIENT_MODE){
		KEEP_ALIVE_COUNTER++;

		payload->tid = te->tp.tid; //il tid viene inserito solo lato CLIENT
		payload->id = KEEP_ALIVE_COUNTER; //l'id viene inserito solo lato CLIENT

		payload->type = KEEP_ALIVE_REQUEST;

		memcpy(payload->info.Rc, te->kp.info.Rc, KP_BYTES);
		memcpy(payload->info.Ss, te->kp.info.Ss, KP_BYTES);
		Sc = getMSecJiffies2();
		to24bit(payload->info.Sc, Sc);

		payload->info.pSc = te->kp.info.pSc;

		payload->info.pSs = te->kp.pSs_tmp;
		payload->info.pRc = te->kp.pRc_tmp;

		//printk("Sending Keep-Alive request, Sc: %lld\n", payload->info.Sc);
	}
	if(mode == KEEP_ALIVE_SERVER_MODE){
		payload->tid = te->kp.info.client_tid;
		payload->id = te->kp.info.client_id;

		payload->type = KEEP_ALIVE_RESPONSE;

		memcpy(payload->info.Sc, te->kp.info.Sc, KP_BYTES);
		memcpy(payload->info.Rs, te->kp.info.Rs, KP_BYTES);
		Ss = getMSecJiffies2();
		to24bit(payload->info.Ss, Ss);

		payload->info.pSc = te->kp.pSc_tmp;
		payload->info.pRs = te->kp.pRs_tmp;

		payload->info.pSs = te->kp.info.pSs;

		//printk("Sending Keep-Alive response, Ss: %lld\n", payload->info.Ss);
	}
}

struct sk_buff * create_keep_alive_response(struct tunt_entry *te){
	u32 VIPA;
	struct sk_buff *skb;
	struct ka_payload payload;

	//printk("Creating keep-alive response on tunnel %d\n", te->tp.tid);
	create_keep_alive_payload(te, &payload, KEEP_ALIVE_SERVER_MODE);
	//print_ka_info(payload->info);

	skb = NULL;
	VIPA = get_dev_ip_address(NULL, UPMT_DEVICE_NAME, 0);
	skb = alloc_keep_alive_skb(te, VIPA, te->tp.tr.addr, KEEP_ALIVE_DST_PORT, KEEP_ALIVE_SRC_PORT, &payload);
	if(skb == NULL){
		dmesge("create_keep_alive_response - skb = NULL");
	}
	return skb;
}

struct sk_buff * create_keep_alive_request(struct tunt_entry *te){
	u32 VIPA;
	struct ka_payload payload;
	struct sk_buff *skb;

	//printk("Creating keep-alive request on tunnel %d\n", te->tp.tid);
	create_keep_alive_payload(te, &payload, KEEP_ALIVE_CLIENT_MODE);
	//print_ka_info(payload.info);

	skb = NULL;
	VIPA = get_dev_ip_address(NULL, UPMT_DEVICE_NAME, 0);
	skb = alloc_keep_alive_skb(te, VIPA, te->tp.tr.addr, KEEP_ALIVE_SRC_PORT, KEEP_ALIVE_DST_PORT, &payload);
	if(skb == NULL){
		dmesge("create_keep_alive_request - skb = NULL");
	}
	return skb;
}

int send_keep_alive_message(struct sk_buff *skb){
	int ret;
	struct iphdr *ip_hdr;
	struct rtable *rt;
	struct flowi4 fl4;
	struct sock tmpsk;

	ret = 0;

	ip_hdr = get_IP_header(skb);
	rt = ip_route_output_ports(dev_net(skb->dev), &fl4, &tmpsk, ip_hdr->daddr, 0, 0, 0, IPPROTO_IP, RT_TOS(0), skb->dev->ifindex);
	if(rt == NULL){
		dmesge("send_keep_alive_message - rtable = NULL");
		goto error;
	}
	skb_dst_set(skb, &rt->dst);

	if(ip_local_out(skb) < 0){
		dmesge("send_keep_alive_message - ip_local_out < 0");
		goto error;
	}
	goto end;

error:
	ret = -1;
	dev_kfree_skb_any(skb);
end:
	return ret;
}
