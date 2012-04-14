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
EXPORT_SYMBOL(upmt_dev);
EXPORT_SYMBOL(upmt_dev1);

static unsigned int process_packet_OUT(struct sk_buff *skb){
	struct upmt_key upmt_key;
	struct tunt_entry *te;
	struct pdft_entry *pde;
	int err;
	struct iphdr * ip = get_IP_header(skb);

	bul_read_lock();

	if (!( (ip->protocol == IPPROTO_UDP)
			|| (ip->protocol == IPPROTO_TCP) 
#ifdef UPMT_S
			|| (ip->protocol == IPPROTO_ESP) 
#endif
			))
		goto out_free_skb;


#ifdef PRINT_INTERRUPT_CONTEXT
	printk("\n\n\tsending packet");
	check_context();
#endif

#ifdef VERBOSE_MODE
	if(verbose > 0) printk("\n");
	if(verbose >= 1){
		printk("\n\tOUT------------------Before Encap-------------------------");
		print_packet_information(skb, 0);
	}
#endif

	set_upmt_key_from_skb(&upmt_key, skb, 0);

	te = paft_get_tun(&upmt_key);

	if(te == NULL){

		//begin vpn part
		pde = pdft_search(ip->daddr);
		if(pde != NULL){
			te = pde->te;
			//vpn_encap(skb, &te->tp);
			printk("\n\tOUT-------------------Before VPU encap-------------------------");
			print_packet_information(skb, 0);
			goto out_vpn_skb;
		}
		//end vpn part


		if(default_tunnel == NULL) 
			goto out_free_skb;
		te = default_tunnel;
	}

	if(te->tp.in.local != 0){
		inat_packet(skb, &te->tp);
#ifdef VERBOSE_MODE
		if(verbose >= 4){
			printk("\n\tOUT-------------------After INAT--------------------------");
			print_packet_information(skb, 0);
		}
#endif
	}

out_vpn_skb:
	if (encap(skb, &te->tp) < 0)
		goto out_free_skb;

	bul_read_unlock();

#ifdef VERBOSE_MODE
	if(verbose >= 2){
		printk("\n\tOUT------------------After Encap-------------------------");
		print_packet_information(skb, 0);
	}
	if(verbose >= 3){
		printk("\n\tOUT-------------------Internal---------------------------");
		print_packet_information(skb, 28);
	}
#endif

	if ((err = ip_local_out(skb)) < 0) 
		printk("upmt: ip local out() error");

	return err;

out_free_skb:
	dev_kfree_skb(skb);

	bul_read_unlock();

	return 0;
}

static void upmt_dev_uninit(struct net_device *dev) {
	/*TODO*/
	printk(KERN_INFO "TODO upmt_tunnel_uninit\n");
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
	//dev->features           |= 0/*TODO*/;
}

int upmt_dev_register(){
	upmt_dev = alloc_netdev(0, UPMT_DEVICE_NAME, upmt_dev_setup);
	if (!upmt_dev) {
		printk("\n upmt_dev_init - failed to allocate memory for device");
		return -ENOMEM;
	}
	if(dev_alloc_name(upmt_dev, upmt_dev->name) < 0){
		printk("\n upmt_dev_init - failed to allocate memory for device name");
		return -1;
	}
	if(register_netdev(upmt_dev)){
		printk("\n upmt_dev_init - failed to register device");
		free_netdev(upmt_dev);
		return -1;
	}
	//to create upmt1 if
	upmt_dev1 = alloc_netdev(0, UPMT_DEVICE_NAME1, upmt_dev_setup);
	if (!upmt_dev1) {
		printk("\n upmt_dev_init - failed to allocate memory for device");
		return -ENOMEM;
	}
	if(dev_alloc_name(upmt_dev1, upmt_dev1->name) < 0){
		printk("\n upmt_dev_init - failed to allocate memory for device name");
		return -1;
	}
	if(register_netdev(upmt_dev1)){
		printk("\n upmt_dev_init - failed to register device");
		free_netdev(upmt_dev1);
		return -1;
	}
	//end create
	//printk("\n\t DEV ILINK: %d", upmt_dev->iflink);
	
	out_counter = 0;
	return 0;
}

void upmt_dev_unregister(){
	unregister_netdev(upmt_dev);
	unregister_netdev(upmt_dev1);
}

MODULE_LICENSE("GPL");
