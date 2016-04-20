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
#include "include/upmt_util.h"

#include "include/upmt_pdft.h"
#include "include/xt_UPMT_ex.h"
#include "include/upmt_ka.h"


struct nsproxy *upmtns;
u32 an_mark;
unsigned int HEADROOM;
unsigned int ADDROOM;
unsigned int PIGGYROOM;
unsigned int IP_HEADROOM;
unsigned int UDP_HEADROOM;

static const char load_banner[] __initconst = KERN_INFO "UPMT tunneling (IP over IP/UDP) module loaded";
static const char unload_banner[] = KERN_INFO "UMPT module unloaded";

static void init_headrooms(void){
	IP_HEADROOM = sizeof(struct iphdr);
	UDP_HEADROOM = sizeof(struct udphdr);
	HEADROOM = IP_HEADROOM + UDP_HEADROOM;
	PIGGYROOM = sizeof(struct ka_payload);
	ADDROOM = HEADROOM + PIGGYROOM;

	printk("HEADROOM:\t%u\n", HEADROOM);
	printk("PIGGYROOM:\t%u\n", PIGGYROOM);
	printk("ADDROOM:\t%u\n", ADDROOM);
}

/*static void check_ver(void){
	dmesg("Sysname: %s", utsname()->sysname);
	dmesg("Release: %s", utsname()->release);
	dmesg("Version: %s", utsname()->version);
	dmesg("LINUX_VERSION_CODE: %d", LINUX_VERSION_CODE);
	dmesg("KERNEL_VERSION:	 %d", KERNEL_VERSION(2, 6, 32));
	dmesg("KERNEL_VERSION:	 %d", KERNEL_VERSION(13, 26, 39));
}*/

int AN = 0;
module_param(AN, int, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP);
MODULE_PARM_DESC(AN, "This is the running mode: 0--->ClientMode, 1--->AnchorNodeMode");

int KA_TIMER = 1;

static int __init upmt_init(void) {
	upmtns=current->nsproxy; // Namespace 'imprinting' for the current module (Sander)
	an_mark = 0;
	INIT_LIST_HEAD(&(registered_pt.list)); // Inizialization for Packet Handler/Type list (Sander)
	init_headrooms();
	set_ka_values();
	if (mdl_create())			goto fail_mdl_create;
	if (spl_create())			goto fail_spl_create;
	if (tsa_create())			goto fail_tsa_create;
	if (tunt_create())			goto fail_tunt_create;
	if (paft_create())			goto fail_paft_create;
	if (pdft_create())			goto fail_pdft_create;
//	if (upmt_nf_register())			goto fail_upmt_nf_register; // pack handler is inserted when bringing a dev under upmt control (Sander)
	if (upmt_dev_register())		goto fail_upmt_dev_register;
	//if (upmt_genl_register(FAM))		goto fail_upmt_genl_register;
	if (upmt_genl_register())		goto fail_upmt_genl_register;


#ifdef UPMT_S
	//TODO check for errors..
	if (upmt_s_init()) {
		dmesge("UPMT-S init error");
		goto fail_upmt_s_init;
	}
#endif

	xt_UPMT_ex_init();
	if(KA_TIMER == 1) ka_timer_register();

	dmesg(load_banner);
	dmesg(KERN_ALERT);

	printk("UPMT_MODULE - AN variable value: %d\n", AN);

	return 0;

#ifdef UPMT_S
fail_upmt_s_init:
	upmt_genl_unregister();
#endif

fail_pdft_create:
	//tunt_erase();
	
fail_upmt_genl_register:
	upmt_dev_unregister();

//fail_upmt_dev_register:
//	upmt_nf_unregister();
	
fail_upmt_dev_register:
	paft_erase();

//fail_upmt_nf_register:
//	paft_erase();

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

	if(KA_TIMER == 1) ka_timer_unregister();
	upmt_genl_unregister();
	xt_UPMT_ex_exit();
	upmt_ph_unregister_all();
	upmt_dev_unregister();

	//write_lock_irqsave(&bul_mutex, flag);
	pdft_erase();

	 // Now this unregisters every packet handler instead of the netfilter hook (Sander)
	paft_erase();
	tunt_erase();
	tsa_erase();
	spl_erase();
	mdl_erase();
	//write_unlock_irqrestore(&bul_mutex, flag);

#ifdef UPMT_S
	upmt_s_fini();
#endif

	dmesg(unload_banner);
}

module_init(upmt_init);
module_exit(upmt_fini);

MODULE_LICENSE("GPL");
