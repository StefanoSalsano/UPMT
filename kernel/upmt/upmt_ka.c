
#include "include/upmt.h"
#include "include/upmt_ka.h"
#include "include/upmt_util.h"
#include "include/upmt_locks.h"
#include "include/xt_UPMT_ex.h"
#include "include/upmt_util.h"
#include "include/upmt_stamp.h"
#include "include/upmt_conntracker_appmon.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/string.h>

#include <net/ip.h>
#include <net/route.h>
#include <net/genetlink.h>

struct timer_list ka_timer;

unsigned long KEEP_ALIVE_COUNTER	= 0;
unsigned long T_KA		= 5000;

unsigned long kc		= 0;
unsigned long K			= 5;
unsigned long T_TO		= 0;

unsigned long N			= 10;
unsigned long T_L		= 0;

/*
 *
 */

static int send_info_tun_notif(struct upmt_info_tun_data *data) {
	struct sk_buff *skb;
	void *skb_head;

	skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
	if (skb == NULL){
			dmesge("send_info_tun_notif - unable to allocate skb");
			return -1;
	}

	skb_head = genlmsg_put(skb, appmon_pid, conn_notif_seqno++, &upmt_appmgr_gnl_family, 0, UPMT_APPMON_C_INFO_TUN);
	if (skb_head == NULL) {
			dmesge("send_info_tun_notif - unable to allocate skb_head");
			return -ENOMEM;
	}

	if(nla_put(skb, UPMT_APPMON_A_INFO_TUN, sizeof(struct upmt_info_tun_data), data) != 0){
			dmesge("send_info_tun_notif - unable to put UPMT_A_MSG_TYPE attribute");
			return -1;
	}

	genlmsg_end(skb, skb_head);

	if(genlmsg_unicast(&init_net, skb, appmon_pid ) != 0){
			dmesge("send_info_tun_notif - unable to send response");
			return -1;
	}
	//printk(" ---> INFO TUN sent: %lu \n", data->rtt);
	return 0;
}

static int send_delete_tun_notif(struct upmt_del_tun_data *data) {
	struct sk_buff *skb;
	void *skb_head;

	dmesg("Sending del tun notify on tid %d...", data->tid);

	skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
	if (skb == NULL){
			dmesge("send_delete_tun_notif - unable to allocate skb");
			return -1;
	}

	skb_head = genlmsg_put(skb, appmon_pid, conn_notif_seqno++, &upmt_appmgr_gnl_family, 0, UPMT_APPMON_C_DEL_TUN);
	if (skb_head == NULL) {
			dmesge("send_delete_tun_notif - unable to allocate skb_head");
			return -ENOMEM;
	}

	if(nla_put(skb, UPMT_APPMON_A_DEL_TUN, sizeof(struct upmt_del_tun_data), data) != 0){
			dmesge("send_delete_tun_notif - unable to put UPMT_A_MSG_TYPE attribute");
			return -1;
	}

	genlmsg_end(skb, skb_head);

	if(genlmsg_unicast(&init_net, skb, appmon_pid ) != 0){
			dmesge("send_delete_tun_notif - unable to send response");
			return -1;
	}
	return 0;
}

/*
 *
 */

void set_ka_values(){

	T_TO	= T_KA * K;
	T_L		= T_KA * N;

	dmesg("set_ka_values");
	printk(" --- T_KA:\t%lu\n", T_KA);
	printk(" --- T_TO:\t%lu\n", T_TO);
	printk(" --- T_L:\t%lu\n", T_L);
}

/*
 *
 */

static void scan_tunt_for_keep_alive(struct sk_buff_head *list){
	int i;
	struct tunt_entry *tmp;
	struct sk_buff *skb;
	struct upmt_del_tun_data data;

	skb = NULL;

	//dmesg("Scanning TUNT table...");
	for(i=0; i<tunt->dim; i++){
		if(tunt->table[i] == NULL) continue;
		list_for_each_entry(tmp, &tunt->table[i]->list, list){
			if(isTimeout(tmp) == 0){
				data.tid = tmp->tp.tid;
				data.daddr = tmp->tp.tr.addr;
				strncpy(data.ifname, tmp->tp.md->iname, IFNAMSIZ);

				send_delete_tun_notif(&data);
				continue;
			}

			if((tmp->kp.state == KEEP_ALIVE_ON)&&(tmp->kp.toSend == 1)){
				dmesg("Sending NORMAL keep-alive request...");
				tmp->kp.info.pSc++;
				skb = create_keep_alive_request(tmp);
				if(skb != NULL) skb_queue_tail(list, skb);
			}
			if(tmp->kp.state == KEEP_ALIVE_ON) tmp->kp.toSend = 1;

			if((tmp->kp.state == KEEP_ALIVE_OFF)&&(tmp->kp.toSend == 1)){
				dmesg("Sending NORMAL keep-alive response...");
				tmp->kp.info.pSs++;
				skb = create_keep_alive_response(tmp);
				if(skb != NULL) skb_queue_tail(list, skb);
			}

			if(kc % N == 0) tmp->kp.computeLOSS = 1;
		}
	}
}

static void keep_alive_task(unsigned long data){
	int ret;
	unsigned long flag;
	struct sk_buff_head list, sent_list;
	struct sk_buff *skb;
	struct tun_param tp;
	struct tunt_entry *te;
	struct iphdr *iph;
	struct udphdr *udph;

	//dmesg("Keep-Alive task is working at time %lu", getMSecJiffies());

	skb_queue_head_init(&list);
	skb_queue_head_init(&sent_list);

	write_lock_irqsave(&bul_mutex, flag);
	scan_tunt_for_keep_alive(&list);
	write_unlock_irqrestore(&bul_mutex, flag);

	while(skb_queue_len(&list) > 0){
		skb = skb_dequeue(&list);
		ret = send_keep_alive_message(skb);
		if(ret >= 0) skb_queue_tail(&sent_list, skb);
		else dmesge("keep_alive_task - out_keep_alive_message");
	}

	write_lock_irqsave(&bul_mutex, flag);
	while(skb_queue_len(&sent_list) > 0){
		skb = skb_dequeue(&sent_list);
		iph = ip_hdr(skb);
		udph = udp_hdr(skb);
		tp.tl.ifindex = skb->skb_iif;
		tp.tl.port = ntohs(udph->source);
		tp.tr.addr = iph->daddr;
		tp.tr.port = ntohs(udph->dest);
		te = tunt_search(&tp);
		if(te != NULL){
			te->kp.sent++;
			te->kp.tstamp_sent = getMSecJiffies();

			if(te->kp.state == KEEP_ALIVE_ON){
				//te->kp.info.pSc++;
			}
			if(te->kp.state == KEEP_ALIVE_OFF){
				//te->kp.info.pSs++;
				te->kp.toSend = 0;
			}

		}
		else dmesge("keep_alive_task - te (from skb) = NULL");
	}
	ka_timer.expires = jiffies + getJiffiesMSec(T_KA);
	kc++;
	write_unlock_irqrestore(&bul_mutex, flag);

	add_timer(&ka_timer);
}

int ka_timer_register(void){
	dmesg("Registering Keep-Alive timer at time %lu", jiffies);
	init_timer(&ka_timer);
	ka_timer.function = keep_alive_task;
	ka_timer.data = 0;
	ka_timer.expires = jiffies + getJiffiesMSec(T_KA);
	add_timer(&ka_timer);
	return 0;
}

int ka_timer_unregister(void){
	dmesg("Unregistering Keep-Alive timer at time %lu", jiffies);
	del_timer_sync(&ka_timer);
	return 0;
}

/****************/

static void manage_keep_alive_response(struct tunt_entry *te, struct ka_payload *payload){
	//unsigned long rtt;
	u64 Rc;
	struct upmt_info_tun_data data;

	//printk("Receiving keep-alive response on tunnel %d\n", te->tp.tid);
	//print_ka_info(payload->info);

	te->kp.recv++;
	te->kp.tstamp_recv = getMSecJiffies();

	memcpy(te->kp.info.Sc, payload->info.Sc, KP_BYTES);
	memcpy(te->kp.info.Ss, payload->info.Ss, KP_BYTES);
	memcpy(te->kp.info.Rs, payload->info.Rs, KP_BYTES);

	Rc = getMSecJiffies2();
	to24bit(te->kp.info.Rc, Rc);

	te->kp.pSs_tmp = payload->info.pSs;
	//te->kp.pRc_tmp = te->kp.pRc;
	te->kp.pRc_tmp = te->kp.info.pRc;

	data.tid = te->tp.tid;
	strncpy(data.ifname, te->tp.md->iname, IFNAMSIZ);
	data.daddr = te->tp.tr.addr;

	data.rtt = compute_RTT(te, KEEP_ALIVE_CLIENT_MODE);

	if(te->kp.computeLOSS == 1){
		data.loss = compute_LOSS(te, payload, KEEP_ALIVE_CLIENT_MODE);
		te->kp.computeLOSS = 0;
		send_info_tun_notif(&data);
		print_ka_info(te->kp.info);
	}
	else data.loss = 0;

	data.ewmartt = 0;
	data.ewmaloss = 0;
	//send_info_tun_notif(&data);
}

//I'm under data lock from upmt_netfilter.c
static void manage_keep_alive_request(struct tunt_entry *te, struct ka_payload *payload){
	u64 Rs;
	struct upmt_info_tun_data data;

	//printk("Receiving keep-alive request on tunnel %d\n", te->tp.tid);
	//print_ka_info(payload->info);

	te->kp.recv++;
	te->kp.tstamp_recv = getMSecJiffies();

	memcpy(te->kp.info.Sc, payload->info.Sc, KP_BYTES);
	memcpy(te->kp.info.Rc, payload->info.Rc, KP_BYTES);
	memcpy(te->kp.info.Ss, payload->info.Ss, KP_BYTES);

	Rs = getMSecJiffies2();
	to24bit(te->kp.info.Rs, Rs);

	te->kp.pSc_tmp = payload->info.pSc;
	//te->kp.pRs_tmp = te->kp.pRs;
	te->kp.pRs_tmp = te->kp.info.pRs;

	/***********************/

	te->kp.info.client_id = payload->id;
	te->kp.info.client_tid = payload->tid;

	if(te->kp.recv > 1){
		data.tid = te->tp.tid;
		strncpy(data.ifname, te->tp.md->iname, IFNAMSIZ);
		data.daddr = te->tp.tr.addr;

		data.loss = compute_LOSS_server(te);

		data.ewmartt = 0;
		data.ewmaloss = 0;

		data.rtt = compute_RTT(te, KEEP_ALIVE_SERVER_MODE);

		if(te->kp.computeLOSS == 1){
			data.loss = compute_LOSS(te, payload, KEEP_ALIVE_SERVER_MODE);
			te->kp.computeLOSS = 0;
		}
		else data.loss = 0;

		send_info_tun_notif(&data);
	}

	te->kp.toSend = 1;
}

void manage_piggy_keep_alive(struct tunt_entry *te, struct ka_payload *payload){
	if(te->kp.state == KEEP_ALIVE_OFF){
		dmesg("Receiving PIGGY keep-alive request...");
		manage_keep_alive_request(te, payload);
	}

	if(te->kp.state == KEEP_ALIVE_ON){
		dmesg("Receiving PIGGY keep-alive response...");
		manage_keep_alive_response(te, payload);
	}
}

int manage_keep_alive(struct tunt_entry *te, struct sk_buff *skb){
	struct upmt_key key;
	struct ka_payload *payload;

	set_upmt_key_from_skb(&key, skb, 0);

	payload = (struct ka_payload *) get_UDP_packet_payload(skb, 0);

//	if(payload->type == KEEP_ALIVE_REQUEST)		printk("Receiving Keep-Alive request...\n");
//	if(payload->type == KEEP_ALIVE_RESPONSE)	printk("Receiving Keep-Alive response...\n");

	if(is_ka_request(&key) == 0){
		dmesg("Receiving NORMAL keep-alive request...");
		manage_keep_alive_request(te, payload);
		return 0;
	}
	if(is_ka_response(&key) == 0){
		dmesg("Receiving NORMAL keep-alive response...");
		manage_keep_alive_response(te, payload);
		return 0;
	}
	return -1;
}
