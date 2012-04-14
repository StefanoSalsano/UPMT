#ifndef __KERNEL__
#define __KERNEL__
#endif

#ifndef MODULE
#define MODULE
#endif

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/ip.h>
#include <linux/udp.h>
#include <linux/utsname.h>

#include "include/upmt.h"
#include "include/upmt_paft.h"
#include "include/upmt_tunt.h"
#include "include/upmt_tsa.h"
#include "include/upmt_spl.h"
#include "include/upmt_mdl.h"
#include "include/upmt_netfilter.h"
#include "include/upmt_dev.h"
#include "include/upmt_genl.h"
#include "include/upmt_s.h"

#include "include/upmt_pdft.h"

u32 an_mark;
unsigned int HEADROOM;
unsigned int IP_HEADROOM;
unsigned int UDP_HEADROOM;

static const char load_banner[] __initconst = KERN_INFO "UPMT tunneling (IP over IP/UDP) module\n";
static const char unload_banner[] = KERN_INFO "UMPT module unloaded\n";

static void init_headrooms(void){
	IP_HEADROOM = sizeof(struct iphdr);
	UDP_HEADROOM = sizeof(struct udphdr);
	HEADROOM = IP_HEADROOM + UDP_HEADROOM;
}

/*static void check_ver(void){
	printk("\n\t Sysname: %s", utsname()->sysname);
	printk("\n\t Release: %s", utsname()->release);
	printk("\n\t Version: %s", utsname()->version);
	printk("\n\t LINUX_VERSION_CODE: %d", LINUX_VERSION_CODE);
	printk("\n\t KERNEL_VERSION:	 %d", KERNEL_VERSION(2, 6, 32));
	printk("\n\t KERNEL_VERSION:	 %d", KERNEL_VERSION(13, 26, 39));
}*/

static int __init upmt_init(void) {

	an_mark = 0;
	init_headrooms();
	if (mdl_create())			goto fail_mdl_create;
	if (spl_create())			goto fail_spl_create;
	if (tsa_create())			goto fail_tsa_create;
	if (tunt_create())			goto fail_tunt_create;
	if (paft_create())			goto fail_paft_create;
	if (upmt_nf_register())		goto fail_upmt_nf_register;
	if (upmt_dev_register())	goto fail_upmt_dev_register;
	if (upmt_genl_register())	goto fail_upmt_genl_register;

	if (pdft_create())			goto fail_pdft_create;

#ifdef UPMT_S
	//TODO check for errors..
	if (upmt_s_init()) {
		printk("UPMT-S init error");
		goto fail_upmt_s_init;
	}
#endif

	printk(load_banner);
	return 0;

#ifdef UPMT_S
fail_upmt_s_init:
	upmt_genl_unregister();
#endif

fail_pdft_create:
	//tunt_erase();
	
fail_upmt_genl_register:
	upmt_dev_unregister();

fail_upmt_dev_register:
	upmt_nf_unregister();
	
fail_upmt_nf_register:
	paft_erase();

fail_paft_create:
	tunt_erase();
	
fail_tunt_create:
	tsa_erase();

fail_tsa_create:
	spl_erase();

fail_spl_create:
	mdl_erase();

fail_mdl_create:
	return -1;
}

static void __exit upmt_fini(void) {
	pdft_erase();
	upmt_genl_unregister();
	upmt_dev_unregister();
	upmt_nf_unregister();
	paft_erase();
	tunt_erase();
	tsa_erase();
	spl_erase();
	mdl_erase();

#ifdef UPMT_S
	upmt_s_fini();
#endif

	printk(unload_banner);
}

module_init(upmt_init);
module_exit(upmt_fini);

MODULE_LICENSE("GPL");
