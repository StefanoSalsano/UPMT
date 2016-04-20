/*
 * upmt_dev.c
 *
 *  Created on: 14/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_dev.h"
#include "include/upmt.h"
#include "include/upmt_util.h"
#include "include/upmt_paft.h"
#include "include/upmt_tunt.h"
#include "include/upmt_encap.h"
#include "include/upmt_locks.h"
#include "include/upmt_pdft.h"
#include "include/upmt_ka.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/if_arp.h>
#include <linux/netdevice.h>
#include <linux/etherdevice.h>
#include <net/ip.h>
#include <linux/udp.h>
#include <linux/string.h>
#include <linux/in.h>
#include <linux/inetdevice.h>

int out_counter;
struct net_device *upmt_dev;
struct net_device *upmt_dev1;
//UPMT modules are now merged, no need to export (Sander)
//EXPORT_SYMBOL(upmt_dev);
//EXPORT_SYMBOL(upmt_dev1);

static unsigned int process_packet_OUT(struct sk_buff *skb){
	struct upmt_key upmt_key;
	struct tunt_entry *te;
	struct pdft_entry *pde;
	struct iphdr *ip;
	struct tun_param tp;
	unsigned int mark;
	int err;
	unsigned long flag;
	struct ka_payload kap;

	ip = get_IP_header(skb);
	if(ip == NULL){
		dmesge("process_packet_OUT - iph - NULL");
		return NF_ACCEPT; 
	}

	if (!( (ip->protocol == IPPROTO_UDP)
			|| (ip->protocol == IPPROTO_TCP) 
#ifdef UPMT_S
			|| (ip->protocol == IPPROTO_ESP) 
#endif
			))
		goto out_free_skb;


#ifdef PRINT_INTERRUPT_CONTEXT
	dmesg("sending packet");
	check_context();
#endif

#ifdef VERBOSE_MODE
	if(verbose >= 1){
		dmesg_lbl("OUT", "Begin");
		print_packet_information(skb, 0);
	}
#endif

	write_lock_irqsave(&bul_mutex, flag);

	set_upmt_key_from_skb(&upmt_key, skb, 0);

	te = paft_get_tun(&upmt_key);

	if(te == NULL){

		//begin vpn part
		pde = pdft_search(ip->daddr);
		if(pde != NULL){
			te = pde->te;
#ifdef VERBOSE_MODE
		if(verbose >= 3){
			dmesg_lbl("OUT", "Before VPN Encap");
			print_packet_information(skb, 0);
		}
#endif
			goto out_vpn_skb;
		}
		//end vpn part


		if(default_tunnel == NULL) 
			goto out_free_skb_lock;
		te = default_tunnel;
	}

	if(te->tp.in.local != 0){
#ifdef VERBOSE_MODE
		if(verbose >= 4){
			dmesg_lbl("OUT", "Before iNAT");
			print_packet_information(skb, 0);
		}
#endif
		inat_packet(skb, &te->tp);
#ifdef VERBOSE_MODE
		if(verbose >= 4){
			dmesg_lbl("OUT", "After iNAT");
			print_packet_information(skb, 0);
		}
#endif
	}

out_vpn_skb:
	mark = te->tp.md->mark;
	tun_param_copy(&tp, &te->tp);

	kap.tid = 0;
	memset(&kap, 0, sizeof(struct ka_payload));

	/*if(te->kp.state == KEEP_ALIVE_ON)*/	te->kp.info.pSc++;
	/*if(te->kp.state == KEEP_ALIVE_OFF)*/	te->kp.info.pSs++;

	if((te->kp.state == KEEP_ALIVE_ON)&&(te->kp.toSend == 1)){
		dmesg("Sending PIGGY Keep-Alive request...");
		te->kp.sent++;
		te->kp.tstamp_sent = getMSecJiffies();
		te->kp.toSend = 0;

		create_keep_alive_payload(te, &kap, KEEP_ALIVE_CLIENT_MODE);
	}

	if((te->kp.state == KEEP_ALIVE_OFF)&&(te->kp.toSend == 1)){
		dmesg("Sending PIGGY Keep-Alive response...");
		te->kp.sent++;
		te->kp.tstamp_sent = getMSecJiffies();
		te->kp.toSend = 0;

		create_keep_alive_payload(te, &kap, KEEP_ALIVE_SERVER_MODE);
	}

	write_unlock_irqrestore(&bul_mutex, flag);

	if (encap(skb, &tp, mark, &kap) < 0)
		goto out_free_skb;

#ifdef VERBOSE_MODE
	if(verbose >= 2){
		dmesg_lbl("OUT", "After Encap: External");
		print_packet_information(skb, 0);

		dmesg_lbl("OUT", "After Encap: Internal");
		print_packet_information(skb, HEADROOM);
	}
#endif

	if ((err = ip_local_out(skb)) < 0){
		dmesge("process_packet_OUT - ip_local_out returned < 0 ");
		print_packet_information(skb, 0);
		print_packet_information(skb, HEADROOM);
	}

	return err;

out_free_skb_lock:
	write_unlock_irqrestore(&bul_mutex, flag);

out_free_skb:
	dev_kfree_skb_any(skb);

	return 0;
}

static void upmt_dev_uninit(struct net_device *dev) {
	/*TODO*/
	//dmesg("TODO upmt_tunnel_uninit");
}

static int upmt_dev_change_mtu(struct net_device *dev, int new_mtu){
	if (new_mtu < 68 || new_mtu > ETH_DATA_LEN - sizeof(struct iphdr) - sizeof(struct udphdr))
		return -EINVAL;
	dev->mtu = new_mtu;
	return 0;
}

static int upmt_tunnel_xmit(struct sk_buff *skb, struct net_device *dev) {
	out_counter++;
	process_packet_OUT(skb);
	return NETDEV_TX_OK;
}

static const struct net_device_ops upmt_netdev_ops = {
	.ndo_uninit     = upmt_dev_uninit,
	.ndo_start_xmit = upmt_tunnel_xmit,
	.ndo_change_mtu = upmt_dev_change_mtu,
};

//#define CONFIG_NET_NS 1

static void upmt_dev_setup(struct net_device *dev){
	dev->netdev_ops         = &upmt_netdev_ops;
	dev->destructor         = free_netdev;
	dev->type               = ARPHRD_TUNNEL;
	dev->hard_header_len    = LL_MAX_HEADER + sizeof(struct iphdr) + sizeof(struct udphdr);

	/*default starting MTU - it might be changed everytime we add a real interface under upmt control*/
	dev->mtu                = ETH_DATA_LEN - sizeof(struct iphdr) - sizeof(struct udphdr) - 8; /* 8 is for pppoe */
	dev->flags              = IFF_NOARP;
	dev->iflink             = 0;
	dev->addr_len           = 4;
	dev->nd_net				= upmtns->net_ns;
	//dev->features           |= 0/*TODO*/;
	printk("MTU: %u\n", dev->mtu);
}

int upmt_dev_register(){
	upmt_dev = alloc_netdev(0, UPMT_DEVICE_NAME, upmt_dev_setup);
	if (!upmt_dev) {
		dmesge("upmt_dev_register - failed to allocate memory for device");
		return -ENOMEM;
	}
	// The interface is moved to the initializer namespace (Sander)
	//dev_net_set(upmt_dev,upmtns->net_ns);
	if(dev_alloc_name(upmt_dev, upmt_dev->name) < 0){
		dmesge("upmt_dev_register - failed to allocate memory for device name");
		return -1;
	}
	if(register_netdev(upmt_dev)){
		dmesge("upmt_dev_register - failed to register device");
		free_netdev(upmt_dev);
		return -1;
	}
	//to create upmt1 if
	upmt_dev1 = alloc_netdev(0, UPMT_DEVICE_NAME1, upmt_dev_setup);
	if (!upmt_dev1) {
		dmesge("upmt_dev_register - failed to allocate memory for second device");
		return -ENOMEM;
	}
	// The interface is moved to the initializer namespace (Sander)
	//dev_net_set(upmt_dev1,upmtns->net_ns);
	if(dev_alloc_name(upmt_dev1, upmt_dev1->name) < 0){
		dmesge("upmt_dev_register - failed to allocate memory for second device name");
		return -1;
	}
	if(register_netdev(upmt_dev1)){
		dmesge("upmt_dev_register - failed to register second device");
		free_netdev(upmt_dev1);
		return -1;
	}
	//end create
	//dmesg("DEV ILINK: %d", upmt_dev->iflink);
	
	out_counter = 0;
	return 0;
}

void upmt_dev_unregister(){
	unregister_netdev(upmt_dev);
	unregister_netdev(upmt_dev1);
}

MODULE_LICENSE("GPL");
