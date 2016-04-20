/*
 * upmt_genl.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */
 

#include "include/upmt_genl.h"
#include "include/upmt_paft.h"
#include "include/upmt_tunt.h"
#include "include/upmt_tsa.h"
#include "include/upmt_spl.h"
#include "include/upmt_mdl.h"
#include "include/upmt_dev.h"
#include "include/upmt_util.h"
#include "include/upmt_stamp.h"
#include "include/upmt_locks.h"
#include "include/upmt_genl_config.h"
#include "include/upmt_netfilter.h"
#include "include/upmt_pdft.h"
#include "include/upmt_ka.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/netdevice.h>
#include <linux/inetdevice.h>
#include <net/netlink.h>
#include <linux/sched.h>
#include <linux/utsname.h>
#include <linux/pid_namespace.h>
#include <net/net_namespace.h>
#include <linux/ipc_namespace.h>
#include <net/genetlink.h>
#include <linux/in.h>


int verbose = 0;

static struct genl_family upmt_gnl_family = {
	.id = GENL_ID_GENERATE,
	.hdrsize = 0,
//	Family name is now computed during module loading for namespace support (Sander)
//	.name = UPMT_GNL_FAMILY_NAME,
	.version = UPMT_GNL_FAMILY_VERSION,
	.maxattr = UPMT_A_MSG_MAX,
	.netnsok = true, //this allows the family to be visible across different network namespaces
};

static struct nla_policy upmt_genl_policy[UPMT_A_MSG_MAX + 1] = {
	[UPMT_A_UNSPEC]			= { .type = NLA_STRING },
	[UPMT_A_MSG_TYPE]		= { .type = NLA_STRING },
	[UPMT_A_MSG_MESSAGE]	= { .type = NLA_STRING },
	[UPMT_A_PAFT_RID]		= { .type = NLA_BINARY },
	[UPMT_A_PAFT_KEY]		= { .type = NLA_BINARY },
	[UPMT_A_PAFT_STATIC]	= { .type = NLA_BINARY },

	[UPMT_A_TUN_TID]		= { .type = NLA_BINARY },
	[UPMT_A_TUN_LOCAL]		= { .type = NLA_BINARY },
	[UPMT_A_TUN_REMOTE]		= { .type = NLA_BINARY },

	[UPMT_A_TUN_DEV]		= { .type = NLA_STRING },
	[UPMT_A_TUN_PARAM]		= { .type = NLA_BINARY },
	[UPMT_A_MARK_DEV]		= { .type = NLA_BINARY },
	[UPMT_A_AN_MARK]		= { .type = NLA_BINARY },
	[UPMT_A_VERBOSE]		= { .type = NLA_BINARY },
	[UPMT_A_IP_ADDR]		= { .type = NLA_BINARY },
	
	[UPMT_A_LAST_LST_MSG]	= { .type = NLA_STRING },
};

static void *extract_nl_attr(const struct genl_info *info, const int atype){
	struct nlattr *na;
	void *data = NULL;
	na = info->attrs[atype];
	if(na) data = nla_data(na);
	return data;
}

/*static void print_upmt_type_msg(const char *c){
	dmesg("msg_type: %s", c);
}*/

static void set_msg_data(struct RESP_MSG_DATA *msg_data, int type, void *data, int len){
	msg_data->atype = type;
	msg_data->data 	= data;
	msg_data->len 	= len + 1;
}

static int send_response_message(const int command, const unsigned int n_data, const struct RESP_MSG_DATA *msg_data, const struct genl_info *info){
	struct sk_buff *skb;
	void *skb_head;
	unsigned int i;
	unsigned int remaining = n_data;
	int nummsg = 0;
	int tosend, ret;
	
	while (remaining > 0)
	{
		skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
		if (skb == NULL){
			dmesge("send_response_message - unable to allocate skb");
			return -1;
		}

		skb_head = genlmsg_put(skb, 0, info->snd_seq+1, &upmt_gnl_family, 0, command);
		if (skb_head == NULL) {
			dmesge("send_response_message - unable to allocate skb_head");
			return -ENOMEM;
		}

		if(nla_put_string(skb, UPMT_A_MSG_TYPE, RESPONSE_MSG) != 0){
			dmesge("send_response_message - unable to put UPMT_A_MSG_TYPE attribute");
			return -1;
		}

		tosend = (remaining < MAX_RULES_PER_MSG) ? remaining : MAX_RULES_PER_MSG;

		for(i = MAX_RULES_PER_MSG * nummsg; i < (MAX_RULES_PER_MSG * nummsg + tosend); i++){
			if((ret = nla_put(skb, msg_data[i].atype, msg_data[i].len, msg_data[i].data)) < 0){
				dmesge("send_response_message - unable to put attribute %d for elem %d/%d: %d", msg_data[i].atype, i, n_data, ret);
				return -1;
			}
		}
		
		if (remaining <= MAX_RULES_PER_MSG) { // is last message
			if ( (command == UPMT_C_LST_TUNNEL) || (command == UPMT_C_LST_RULE) || (command == UPMT_C_LST_TSA) ) {
				if (nla_put_string(skb, UPMT_A_LAST_LST_MSG, "lastmsg") != 0) {
					dmesge("send_response_message - unable to put attribute UPMT_A_LAST_LST_MSG");
					return -1;
				}
			}
		}	

		genlmsg_end(skb, skb_head);

	// since 3.7.0 genl_info snd_id has been renamed into snd_portid (Sander)
	#if LINUX_VERSION_CODE < UPMT_LINUX_VERSION_CODE
		if(genlmsg_unicast(skb, info->snd_portid ) != 0){
	#else
		if(genlmsg_unicast(upmtns->net_ns, skb, info->snd_portid ) != 0){ // now using the proper namespace (Sander)
	#endif
			dmesge("send_response_message - unable to send response - info->snd_portid = %u", info->snd_portid);
			return -1;
		}
		
		remaining -= tosend;
		nummsg++;
	}
	
	return 0;
}

static int send_error_response_message(const int command, const struct genl_info *info, char *message){
	struct RESP_MSG_DATA msg_data[2];
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	return send_response_message(command, 1, msg_data, info);
}

static int upmt_echo(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	char *response = "Hello from UPMT module in kernel space.";

	//dmesg("upmt_set - ECHO MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_get - info = NULL");
		return -1;
	}

	/*
	 * Extracting ECHO request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_echo - msg_a_type, wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	/*
	 * Creating ECHO response
	 */

	return send_error_response_message(UPMT_C_ECHO, info, response);
}

/* message handling code goes here; return 0 on success, negative values on failure */
static int upmt_paft_get(struct sk_buff *skb, struct genl_info *info){
	unsigned int n_data = 0;
	struct upmt_key *key;
	struct paft_entry *pe;
	char *msg_a_type;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];
	int res;
	char staticrule;

	//dmesg("upmt_paft_get - GET MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_paft_get - info = NULL");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_paft_get - msg_a_type, wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	//rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	key = (struct upmt_key *) extract_nl_attr(info, UPMT_A_PAFT_KEY);
	if(key == NULL){
		dmesge("upmt_paft_get - upmt_key = NULL");
		return -1;
	}
	//else print_upmt_key(key);
	//return 0;
	/*
	 * Creating GET response
	 */

	//check_context("upmt_paft_get");

	pe = paft_search_by_remote_key(key);
	//pe = paft_search_by_rid(rid);
	//te = paft_get_tun(key);
	staticrule = pe->staticrule;

	if(pe == NULL){
		//dmesg("Rule not found.");
		res = send_error_response_message(UPMT_C_GET_RULE, info, "Rule not found.");
		goto end;
	}

	//dmesg("Rule FOUNDED.");
	set_msg_data(&msg_data[0], UPMT_A_PAFT_RID,	&pe->rid, sizeof(int));
	set_msg_data(&msg_data[1], UPMT_A_PAFT_KEY,	&pe->key, sizeof(struct upmt_key));
	set_msg_data(&msg_data[2], UPMT_A_TUN_TID,	&pe->te->tp.tid, sizeof(int));
	set_msg_data(&msg_data[3], UPMT_A_PAFT_STATIC,	&pe->staticrule, sizeof(char));
	n_data = 4;

	/*set_msg_data(&msg_data[0], UPMT_A_PAFT_KEY, &pe->key, sizeof(struct upmt_key));
	if(pe->te == NULL){
		response = "This rule has not a tunnel";
		set_msg_data(&msg_data[1], UPMT_A_MSG_MESSAGE, response, strlen(response));
		set_msg_data(&msg_data[2], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
		n_data = 3;
	}
	else{
		dev = dev_get_by_index(upmtns->net_ns, pe->te->tp.tl.ifindex);  //XXX use mdl_search_by_idx where idx is the mark
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;
		set_msg_data(&msg_data[1], UPMT_A_TUN_DEV, iname, strlen(iname));
		set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &pe->te->tp, sizeof(struct tun_param));
		set_msg_data(&msg_data[3], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
		n_data = 4;
		dev_put(dev); //XXX
	}*/

	res = send_response_message(UPMT_C_GET_RULE, n_data, msg_data, info);

end:
	return res;
}

static int upmt_paft_set(struct sk_buff *skb, struct genl_info *info){
	unsigned int n_data = 0;
	char *iname, *msg_a_type;
	struct net_device *dev;
	int tid, res;
	struct upmt_key *key;
	struct tunt_entry *te;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];
	char staticrule;

	dmesg("upmt_paft_set - SET MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_paft_set - info = NULL");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_paft_set - msg_a_type, wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);

	key = (struct upmt_key *) extract_nl_attr(info, UPMT_A_PAFT_KEY);
	//print_upmt_key(key);

	if((key->proto != IPPROTO_TCP)&&(key->proto != IPPROTO_UDP)){
		res = send_error_response_message(UPMT_C_SET_RULE, info, "Transport protocol is wrong or not specified.");
		goto end;
	}
	
	staticrule = *(char*) extract_nl_attr(info, UPMT_A_PAFT_STATIC);
	if (!( staticrule == 0 || staticrule == 1 || staticrule == 2)) {
		res = send_error_response_message(UPMT_C_SET_RULE, info, "Static rule attribute must be 0, 1 or 2.");
		goto end;
	}

	/*
	 * Creating SET response
	 */

	if (tid > 0) {
		te = tunt_search_by_tid(tid);
		if (te == NULL) {
			res = send_error_response_message(UPMT_C_SET_RULE, info, "Tunnel does not exist.");
			goto lock_end;
		}
	}
	else { // we are editing a rule which is already set (i.e. setting it static)
		te = paft_get_tun(key);
		if (te == NULL) {
			res = send_error_response_message(UPMT_C_SET_RULE, info, "Rule does not exist (tunnel not provided).");
			goto lock_end;
		}
	}
	
	if (staticrule == 2) {
		struct paft_entry* pe = paft_search(key);
		if (pe != NULL)
			staticrule = pe->staticrule;
		else
			staticrule = 0;
	}

	if(paft_insert(key, te, staticrule) == NULL){
		res = send_error_response_message(UPMT_C_SET_RULE, info, "Error while inserting paft rule (type 'dmesg' for details).");
		goto lock_end;
	}

	dev = dev_get_by_index(upmtns->net_ns, te->tp.tl.ifindex); //XXX use mdl_search_by_idx where idx is the mark
	if(dev == NULL) iname = "Warning! Device does not exist!";
	else iname = dev->name;
	set_msg_data(&msg_data[0], UPMT_A_TUN_DEV, iname, strlen(iname));
	set_msg_data(&msg_data[1], UPMT_A_PAFT_KEY, key, sizeof(struct upmt_key));
	set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));
	set_msg_data(&msg_data[3], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
	n_data = 4;
	dev_put(dev); //XXX

	print_upmt_key_line(key);
	printk("TID: %d\n", tid);

	res = send_response_message(UPMT_C_SET_RULE, n_data, msg_data, info);

lock_end:
end:
	return res;
}

static int upmt_paft_del(struct sk_buff *skb, struct genl_info *info){
	int rid, res;
	struct paft_entry *pe;
	char *msg_a_type;

	//dmesg("upmt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_paft_del - info = NULL");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_paft_del - msg_a_type, wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	//dmesg("RID: %d", rid);

	/*
	 * Creating DEL response
	 */

	pe = paft_search_by_rid(rid);
	if(pe == NULL){
		res = send_error_response_message(UPMT_C_DEL_RULE, info, "Rule not found.");
		goto end;
	}

	if(paft_delete(&pe->key) != 0){
		res = send_error_response_message(UPMT_C_DEL_RULE, info, "Error while deleting paft rule (type 'dmesg' for details).");
		goto end;
	}

	res = send_error_response_message(UPMT_C_DEL_RULE, info, "Rule deleted.");

end:
	return res;
}

static int upmt_paft_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct paft_entry **pea = NULL;
	struct RESP_MSG_DATA *msg_data = NULL;
	int N, i, res;
	long pea_extrasize, msg_data_extrasize;

	if (info == NULL){
		dmesge("upmt_paft_lst - info = NULL");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_paft_list - msg_a_type - wrong message format");
		return -1;
	}

	N = paft_count();		//XXX fuori dal lock, solo per inizializzare buffer messaggi
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_RULE, info, "PAFT is empty.");
		goto end;
	}

	pea_extrasize = (sizeof(struct paft_entry *) * N * 6)/5;		//XXX 20% extra per compensare paft_count() fuori dal lock
	//pea = (struct paft_entry **) vzalloc(pea_extrasize);
	pea = (struct paft_entry **) kmalloc(pea_extrasize, GFP_ATOMIC);

	if(pea == NULL){
		dmesge("upmt_paft_lst - Unable to allocate memory for tpa");
		res = send_error_response_message(UPMT_C_LST_RULE, info, "Error while creating rule list1 (type 'dmesg' for details).");
		goto end;
	}
	
	msg_data_extrasize = (sizeof(struct RESP_MSG_DATA) * (N*4) * 6)/5;		//XXX 20% extra per compensare paft_count() fuori dal lock
	//msg_data = (struct RESP_MSG_DATA *) vzalloc(msg_data_extrasize);
	msg_data = (struct RESP_MSG_DATA *) kmalloc(msg_data_extrasize, GFP_ATOMIC);
	if(msg_data == NULL){
		dmesge("upmt_paft_lst - Unable to allocate memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_RULE, info, "Error while creating rule list2 (type 'dmesg' for details).");
		goto fail_msg_data;
	}
	
	paft_fill_pointers(pea);

	N = paft_count();

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i*4], UPMT_A_PAFT_RID, &pea[i]->rid, sizeof(int));
		set_msg_data(&msg_data[i*4+1], UPMT_A_PAFT_KEY, &pea[i]->key, sizeof(struct upmt_key));
		set_msg_data(&msg_data[i*4+2], UPMT_A_TUN_TID, &pea[i]->te->tp.tid, sizeof(int));
		set_msg_data(&msg_data[i*4+3], UPMT_A_PAFT_STATIC, &pea[i]->staticrule, sizeof(char));
	}

	res = send_response_message(UPMT_C_LST_RULE, N*4, msg_data, info);

	vfree(msg_data);

fail_msg_data:
	vfree(pea);

end:
	return res;
}


/*
 * TUNT
 */

static int upmt_tunt_reserve_port(struct sk_buff *skb, struct genl_info *info){
	int port, res, command;
	struct tun_local *tl;
	struct tun_param *tp;

	port = -1;
	command = info->genlhdr->cmd;

	if(command == UPMT_C_SET_TSA){
		tl = (struct tun_local *) extract_nl_attr(info, UPMT_A_TUN_LOCAL);
		if(tl == NULL) return -1;
		port = tl->port;
	}
	if(command == UPMT_C_SET_TUNNEL){
		tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
		if(tp == NULL) return -1;
		port = tp->tl.port;
	}
	if(port < 0) return -1;
	
	dmesg("UPMT - Reserving port %d", port);
	res = reserve_port(port);
	if(res < 0){
		dmesg("UPMT - Reserving port %d FAILED", port);
		release_port(port);
	}
	return res;
}

static int upmt_tunt_set(struct sk_buff *skb, struct genl_info *info, int bound){
	char *iname, *message;
	struct tun_param *tp;
	struct tunt_entry *te;
	struct mdl_entry *mde;
	struct net_device *dev;
	struct in_device *pin_dev;
	int res = 0, DT = 0;
	//int bound;

	char *msg_a_type;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//dmesg("upmt_tunt_set - SET MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tunt_set");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tunt_set - msg_a_type - wrong message format");

		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	//dmesg("DEV: %s", iname);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	if(tp == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error: tunnel parameters are not valid.");
		goto end;
	}

	if(tp->tl.ifindex == 1111) DT = 1;   //XXX 1111 can't be used for MARK -- TODO
	//print_upmt_tun_param(tp);

	dev = dev_get_by_name(upmtns->net_ns, iname); //XXX use mdl_search_by_idx where idx is the MARK
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Device does not exist.");
		goto end;
	}

	pin_dev = (struct in_device *) dev->ip_ptr;  //XXX useless, if the dev doesn't have a IP addr the routing will fail adn the packet will simply be discarded 
	/*if(pin_dev->ifa_list == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Device has not IP address.");
		goto end;
	}*/

	tp->tl.ifindex = dev->ifindex;  //XXX use the MARK
	
	dev_put(dev);  //XXX

	//bound = 1;
	/*dmesg("UPMT - Reserving port %d", tp->tl.port);
	res = reserve_port(tp->tl.port);
	if(res < 0){
		dmesg("UPMT - Reserving port %d FAILED", tp->tl.port);
		bound = 0;
		release_port(tp->tl.port);
	}*/

	mde = mdl_search(iname);
	if(mde == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error: the device is not under upmt control.");
		goto clear_sock;
	}
	tp->md = &mde->md;

	if (bound < 0) {
		if (tsa_search(&tp->tl) == NULL){
			if(res == -1) res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Unable to set TSA: port already reserved.");
			if(res == -2) res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Unable to set TSA: internal error (type 'dmesg' for details).");
			goto clear_sock;
		}
	}
	
	if(tsa_insert(&tp->tl) == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error while inserting tsa (type 'dmesg' for details).");
		goto clear_sock;
	}
	
	if(DT == 1){
		te = tunt_set_default(tp);
		if(te == NULL){
			//tsa_delete(&tp->tl);
			//release_port(tp->tl.port);
			res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error while inserting default tunnel (type 'dmesg' for details).");
			goto clear_sock;
		}
	}
	else{
		te = tunt_insert(tp, tp->tid);
		if(te == NULL){
			//tsa_delete(&tp->tl);
			//release_port(tp->tl.port);
			res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error while inserting tunnel (type 'dmesg' for details).");
			goto clear_sock;
		}

		if(te == NULL){
			//tsa_delete(&tp->tl);
			//release_port(tp->tl.port);
			res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error while setting keep-alive ON (type 'dmesg' for details).");
			goto clear_sock;
		}
	}

	/*
	 * Creating SET response
	 */

	//print_upmt_tun_param_line(tp);
	//printk("DEV: %s\n", iname);
	//printk("Tunnel accepted --> TID %d\n", te->tp.tid);
	message = "Tunnel accepted.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_TUN_DEV, iname, strlen(iname));
	set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));

	res = send_response_message(UPMT_C_SET_TUNNEL, 3, msg_data, info);

clear_sock:
end:	
	return res;
}

static int upmt_tunt_get(struct sk_buff *skb, struct genl_info *info){
	char *iname;
	struct net_device *dev;
	struct tun_param *tp;
	struct tunt_entry *te;
	int res;

	char *msg_a_type, *message;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//dmesg("upmt_tunt_get - GET MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tunt_get");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tunt_get - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	//print_upmt_tun_param(tp);

	if(tp->tid <= 0){
		res = send_error_response_message(UPMT_C_GET_TUNNEL, info, "Tunnel id not valid.");
		goto end;
	}

	te = tunt_search_by_tid(tp->tid);
	if(te == NULL){
		res = send_error_response_message(UPMT_C_GET_TUNNEL, info, "Tunnel does not exist.");
		goto end;
	}

	dev = dev_get_by_index(upmtns->net_ns, te->tp.tl.ifindex);  //XXX same as before... useless and dangerous
	if(dev == NULL) iname = "Warning! Device does not exist!";
	else iname = dev->name;

	dev_put(dev); //XXX
	

	message = "Found.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));
	set_msg_data(&msg_data[2], UPMT_A_TUN_DEV, iname, strlen(iname));

	res = send_response_message(UPMT_C_GET_TUNNEL, 3, msg_data, info);
end:
	return res;
}

static int upmt_tunt_del(struct sk_buff *skb, struct genl_info *info){
	struct tun_param *tp;
	struct tunt_entry *te;
	int res;

	char *msg_a_type, *message;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//dmesg("upmt_tunt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tunt_del");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tunt_del - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	if(tp->tl.ifindex == 1111) tp->tid = 0; //default tunnel
	//print_upmt_tun_param(tp);
	//dmesg("TID da cancellare: %d", tp->tid);

	if(tp->tid < 0){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Tunnel id not valid.");
		goto end;
	}

	if(tp->tid == 0){
		if(tunt_del_default() < 0) res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Default tunnel does not exist.");
		else res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Default tunnel deleted.");
		goto lock_end;
	}

	te = tunt_search_by_tid(tp->tid);
	if(te == NULL){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Tunnel does not exist.");
		goto lock_end;
	}

	res = tunt_delete(&te->tp);
	if(res != 0){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Error while deleting tunnel.");
		goto lock_end;
	}

	message = "Tunnel deleted.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	res = send_response_message(UPMT_C_DEL_TUNNEL, 1, msg_data, info);

lock_end:
end:
	return res;
}

static int upmt_tunt_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	int N, i, res, n_data;
	struct tun_param **tpa = NULL;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct net_device *dev;
	char *iname;
	char message[100];

	//dmesg("upmt_tunt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tunt_lst");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tunt_lst - msg_a_type - wrong message format");
		return -1;
	}

	N = tunt_count(); //this function counts the default tunnel too

	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "TUNT is empty.");
		goto end;
	}

	tpa = (struct tun_param **) kzalloc(sizeof(struct tun_param *)*N, GFP_ATOMIC);
	if(tpa == NULL){
		dmesge("upmt_tunt_lst - Unable to allocating memory for tpa");
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	tunt_fill_pointers(tpa);

	n_data = N*2 + 1; //adding a field for the sentence "Default tunnel: ..."

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(n_data), GFP_ATOMIC);
	if(msg_data == NULL){
		dmesge("upmt_tunt_lst - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		dev = dev_get_by_index(upmtns->net_ns, tpa[i]->tl.ifindex); //XXX
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;

		dev_put(dev); //XXX

		set_msg_data(&msg_data[i*2], UPMT_A_TUN_PARAM, tpa[i], sizeof(struct tun_param));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_DEV, iname, strlen(iname));
	}

	if(default_tunnel != NULL){
		sprintf(message, " ---> Default Tunnel: %d", default_tunnel->tp.tid);
		set_msg_data(&msg_data[n_data-1], UPMT_A_MSG_MESSAGE, message, strlen(message));
	}
	else{
		sprintf(message, " ---> Default Tunnel: NOT DEFINED");
		set_msg_data(&msg_data[n_data-1], UPMT_A_MSG_MESSAGE, message, strlen(message));
	}

	res = send_response_message(UPMT_C_LST_TUNNEL, n_data, msg_data, info);

end:
	kfree(tpa);
	kfree(msg_data);
	return res;
}

static int upmt_handover(struct sk_buff *skb, struct genl_info *info){
	int tid, rid, res;
	struct tunt_entry *te;
	struct paft_entry *pe;
	//char *msg_a_type, *message;
	char *msg_a_type;
	//struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//dmesg("upmt_tunt_handover - handover MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tunt_handover");
		return -1;
	}

	/*
	 * Extracting HANDOVER request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tunt_handover - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);
	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	//dmesg("TID: %d", tid);
	rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	//dmesg("RID: %d", rid);

	te = tunt_search_by_tid(tid);
	pe = paft_search_by_rid(rid);

	if(te == NULL){
		res = send_error_response_message(UPMT_C_HANDOVER, info, "Tunnel does not exist.");
		goto end;
	}
	if(pe == NULL){
		res = send_error_response_message(UPMT_C_HANDOVER, info, "Rule does not exist.");
		goto end;
	}

	pe->te = te; //handover

	res = send_error_response_message(UPMT_C_HANDOVER, info, "PAFT updated.");

//	message = "PAFT updated.";
//	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
//	res = send_response_message(UPMT_C_HANDOVER, 1, msg_data, info);
end:
	return res;
}

static int upmt_tsa_set(struct sk_buff *skb, struct genl_info *info, int bound){
	struct net_device *dev;
	char *msg_a_type, *iname;
	struct tun_local *tl;
	struct tsa_entry *tsae;
	int res = 0;

	//dmesg("upmt_tsa_set - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tsa_set");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tsa_set - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	tl = (struct tun_local *) extract_nl_attr(info, UPMT_A_TUN_LOCAL);

	dev = dev_get_by_name(upmtns->net_ns, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "Device does not exist.");
		goto end;
	}
	tl->ifindex = dev->ifindex;

	dev_put(dev); //XXX	

	if(bound < 0){
		release_port(tl->port);
		if(res == -1) res = send_error_response_message(UPMT_C_SET_TSA, info, "Unable to set TSA: port already reserved.");
		if(res == -2) res = send_error_response_message(UPMT_C_SET_TSA, info, "Unable to set TSA: internal error (type 'dmesg' for details).");
		goto end;
	}

	if(mdl_search(iname) == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "Device is not under upmt control.");
		goto clear_sock;
	}

	tsae = tsa_search(tl);
	if(tsae != NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA already present.");
		goto clear_sock;
	}

	tsae = tsa_insert(tl);
	if(tsae == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "Error while inserting tsa (type 'dmesg' for details).");
		goto clear_sock;
	}

	res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA added.");

clear_sock:

end:	
	return res;
}

static int upmt_tsa_del(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname;
	struct tun_local *tl;
	struct tsa_entry *tsae;
	int res;

	//dmesg("upmt_tsa_del - del MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tsa_del");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tsa_del - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	tl = (struct tun_local *) extract_nl_attr(info, UPMT_A_TUN_LOCAL);

	/****/

	/*dmesg("iname: %s", iname);
	print_upmt_tun_local(tl);
	send_error_response_message(UPMT_C_DEL_TSA, info, "Message received.");
	return 0;*/

	/****/

	dev = dev_get_by_name(upmtns->net_ns, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_DEL_TSA, info, "Device does not exist.");
		goto end;
	}
	tl->ifindex = dev->ifindex;

	dev_put(dev);  //XXX

	tsae = tsa_search(tl);
	if(tsae == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA not present.");
		goto lock_end;
	}
	tsa_delete(tl);
	release_port(tl->port);
	tunt_delete_by_tsa(tl);

	res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA deleted.");

lock_end:
end:
	return res;
}


static int upmt_tsa_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type, *iname;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct tun_local **tla = NULL;
	struct net_device *dev;
	int N, i, res;

#ifdef PRINT_INTERRUPT_CONTEXT
	dmesg("receiving netlink message");
	check_context();
#endif

	//dmesg("upmt_tsa_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_tsa_lst");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_tsa_lst - msg_a_type - wrong message format");
		return -1;
	}

	N = tsa_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_TSA, info, "TSA is empty.");
		goto end;
	}

	tla = (struct tun_local **) kzalloc(sizeof(struct tun_local *)*N, GFP_ATOMIC);
	if(tla == NULL){
		dmesge("upmt_tsa_lst - Unable to allocating memory for tla");
		res = send_error_response_message(UPMT_C_LST_TSA, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	tsa_fill_pointers(tla);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N*2+1), GFP_ATOMIC);
	if(msg_data == NULL){
		dmesge("upmt_tsa_lst - Unable to allocateg memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_TSA, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		dev = dev_get_by_index(upmtns->net_ns, tla[i]->ifindex); //XXX
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;

		dev_put(dev);  //XXX

		set_msg_data(&msg_data[i*2], UPMT_A_TUN_LOCAL, tla[i], sizeof(struct tun_local));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_DEV, iname, strlen(iname));
	}

	res = send_response_message(UPMT_C_LST_TSA, N*2, msg_data, info);

end:
	kfree(tla);
	kfree(msg_data);
	return res;
}

static int upmt_an(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	char response[50];
	int mark;

	if (info == NULL){
		dmesge("upmt_an");
		return -1;
	}

	/*
	 * Extracting AN request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_an - msg_a_type - wrong message format");
		return -1;
	}

	mark = *(int *) extract_nl_attr(info, UPMT_A_AN_MARK);

	an_mark = (u32) mark;

	sprintf(response, "AN_MARK updated. Value: %d", mark);
	return send_error_response_message(UPMT_C_SET_RULE, info, response);
}

static int upmt_verbose(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	char response[50];
	int new_verbose;

	if (info == NULL){
		dmesge("upmt_verbose");
		return -1;
	}

	/*
	 * Extracting AN request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_verbose - msg_a_type - wrong message format");
		return -1;
	}

	new_verbose = *(int *) extract_nl_attr(info, UPMT_A_VERBOSE);

	if(new_verbose >=0 ) verbose = new_verbose;
	else verbose = 0;

	sprintf(response, "Verbose level: %d", verbose);
	return send_error_response_message(UPMT_C_VERBOSE, info, response);
}

static int upmt_flush(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type, *table;
	char response[50];

	if (info == NULL){
		dmesg("upmt_flush");
		return -1;
	}

	/*
	 * Extracting FLUSH request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_flush - msg_a_type - wrong message format");
		return -1;
	}

	table = (char *) extract_nl_attr(info, UPMT_A_MSG_MESSAGE);

	if((strcmp(table, "tsa") == 0)||(strcmp(table, "all") == 0)){
		tsa_erase();
		spl_erase();
		spl_create();
		tsa_create();
	}

	if((strcmp(table, "tun") == 0)||(strcmp(table, "all") == 0)){
		tunt_erase();
		tunt_create();
	}

	if((strcmp(table, "rule") == 0)||(strcmp(table, "all") == 0)){
		paft_flush();
	}

	if((strcmp(table, "vpn") == 0)||(strcmp(table, "all") == 0)){
		pdft_flush();
	}

	sprintf(response, "Flush on %s table(s).", table);

	return send_error_response_message(UPMT_C_FLUSH, info, response);
}

static int upmt_mdl_set(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname, *message;
	struct mdl_entry *me;
	int res;
	struct RESP_MSG_DATA msg_data[2];

	//dmesg("upmt_mdl_set - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_mdl_set");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_mdl_set - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);

	dev = dev_get_by_name(upmtns->net_ns, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_MDL, info, "Device does not exist.");
		return res;
	}

	me = mdl_search(iname);
	if(me == NULL){
		me = mdl_insert(iname);
		if(me == NULL){
			res = send_error_response_message(UPMT_C_SET_MDL, info, "Unable to set device and mark: internal error (type 'dmesg' for details).");
			goto end;
		}
		upmt_ph_register(dev); // Registering packet handler for the new device (Sander)
		message = "Device is now under upmt control.";
		printk("Device %s is now under upmt control\n", iname);
	}
	else{
		message = "Device is already under upmt control.";
		printk("Device %s is already under upmt control\n", iname);
	}

	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_MARK_DEV, &me->md, sizeof(struct mark_dev));

	res = send_response_message(UPMT_C_SET_MDL, 2, msg_data, info);

end:
	dev_put(dev); //XXX
	return res;
}

static int upmt_mdl_del(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname;
	int res;
	struct socket *sock = NULL;

	//dmesg("upmt_mdl_del - del MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_mdl_del");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_mdl_set - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	dev = dev_get_by_name(upmtns->net_ns, iname);  //XXX this is a error. of course it can be null...
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_DEL_MDL, info, "Device does not exist.");
		goto end_no_lock;  //before was "goto end;" ---> PANIK
	}

	dev_put(dev);  //XXX

	if(mdl_search(iname) == NULL){
		res = send_error_response_message(UPMT_C_DEL_MDL, info, "This device is not under upmt control.");
		goto end;
	}

	if(mdl_delete(iname) < 0){
		res = send_error_response_message(UPMT_C_DEL_MDL, info, "Error while freeing device.");
		goto end;
	}

	sock = tsa_delete_by_ifindex(dev->ifindex);  //we can't use ifindex as search key... TODO use the mark
	tunt_delete_by_ifindex(dev->ifindex);		//open question: is it a problem if the same iface name is added brfore it is removed?
	
	res = send_error_response_message(UPMT_C_DEL_MDL, info, "Device released.");

end:
	if (sock) sock_release(sock); //if you do this whene the device has been removed ----> panik
end_no_lock:
	upmt_ph_unregister(dev); // Unregistering packet handler for the specified device (Sander)
	return res;
}

static int upmt_mdl_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct mark_dev **mds = NULL;
	int N, i, res;

	//dmesg("upmt_mdl_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_mdl_lst");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_mdl_lst - msg_a_type - wrong message format");
		return -1;
	}

	N = mdl_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_MDL, info, "There are no devices under upmt control.");
		goto end;
	}

	mds = (struct mark_dev **) kzalloc(sizeof(struct mark_dev *)*N, GFP_ATOMIC);
	if(mds == NULL){
		dmesge("upmt_mld_lst - Unable to allocate memory for mds");
		res = send_error_response_message(UPMT_C_LST_MDL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	mdl_fill_pointers(mds);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N), GFP_ATOMIC);
	if(msg_data == NULL){
		dmesge("upmt_mdl_lst - Unable to allocate memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_MDL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i], UPMT_A_MARK_DEV, mds[i], sizeof(struct mark_dev));
	}

	res = send_response_message(UPMT_C_LST_TSA, N, msg_data, info);

end:
	kfree(mds);
	kfree(msg_data);
	return res;
}

/*
 * PDFT
 */

static int upmt_pdft_set(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct tunt_entry *te;
	int tid, res;
	unsigned int ip;

	//dmesg("upmt_pdft_set - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_pdft_set");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_pdft_set - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	ip = *(unsigned int *) extract_nl_attr(info, UPMT_A_IP_ADDR);
	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	//dmesg("IpAddress: %u", ip);
	//dmesg("TID: %d", tid);

	/*
	 * Creating SET response
	 */

	if (tid > 0) {
		te = tunt_search_by_tid(tid);
		if (te == NULL) {
			res = send_error_response_message(UPMT_C_SET_PDFT, info, "Tunnel does not exist.");
			goto end;
		}
	}
	else{
		res = send_error_response_message(UPMT_C_SET_PDFT, info, "Tunnel id not valid.");
		goto end;
	}

	//controlli sull'ip?

	if(pdft_insert(ip, te) == NULL){
		res = send_error_response_message(UPMT_C_SET_PDFT, info, "Error while inserting rule (type 'dmesg' for details).");
		goto end;
	}


	res = send_error_response_message(UPMT_C_SET_PDFT, info, "Destination rule inserted.");
end:
	return res;
}

static int upmt_pdft_del(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	int res;
	unsigned int ip;

	//dmesg("upmt_pdft_set - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_pdft_del");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_pdft_del - msg_a_type - wrong message format");
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	ip = *(unsigned int *) extract_nl_attr(info, UPMT_A_IP_ADDR);

	/*
	 * Creating DEL response
	 */

	if (pdft_search(ip) == NULL) {
		res = send_error_response_message(UPMT_C_DEL_PDFT, info, "Destination rule does not exist.");
		goto end;
	}

	if (pdft_delete(ip) < 0) {
		res = send_error_response_message(UPMT_C_DEL_PDFT, info, "Error while deleting destination rule.");
		goto end;
	}

	res = send_error_response_message(UPMT_C_DEL_PDFT, info, "Destination rule deleted.");

end:
	return res;
}

static int upmt_pdft_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct pdft_entry **pea = NULL;
	int N, i, res;

	//dmesg("upmt_mdl_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_pdft_lst");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_pdft_lst - msg_a_type - wrong message format");
		return -1;
	}

	N = pdft_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "PDFT is empty: there are no destination rules.");
		goto end;
	}

	pea = (struct pdft_entry **) kzalloc(sizeof(struct pdft_entry *)*N, GFP_ATOMIC);
	if(pea == NULL){
		dmesge("upmt_pdft_lst - Unable to allocate memory for pea");
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	pdft_fill_pointers(pea);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N*2), GFP_ATOMIC);
	if(msg_data == NULL){
		dmesge("upmt_pdft_lst - Unable to allocate memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i*2], UPMT_A_IP_ADDR, &pea[i]->ip, sizeof(unsigned int));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_TID, &pea[i]->te->tp.tid, sizeof(int));
	}

	res = send_response_message(UPMT_C_LST_PDFT, N*2, msg_data, info);

end:
	kfree(pea);
	kfree(msg_data);
	return res;
}

static int upmt_keepAlive_set(struct sk_buff *skb, struct genl_info *info){
	char *message;
	struct tunt_entry *te;
	int tid, state;
	unsigned long period, timeout;

	char *msg_a_type;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	dmesg("upmt_keepAlive_set - SET MESSAGE RECEIVED");
	if (info == NULL){
		dmesge("upmt_keepAlive_set");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		dmesge("upmt_keepAlive_set - msg_a_type - wrong message format");
		return -1;
	}

	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	state = *(int *) extract_nl_attr(info, UPMT_A_KEEP_STATE);
	period = *(unsigned long *) extract_nl_attr(info, UPMT_A_KEEP_PERIOD);
	timeout = *(unsigned long *) extract_nl_attr(info, UPMT_A_KEEP_TIMEOUT);

	/***********/
	/*printk("TID:\t %d\n", tid);
	printk("STATE:\t %d\n", state);
	printk("PERIOD:\t %lu\n", period);
	printk("TIMEOUT:\t %lu\n", timeout);*/
	/***********/

	if(timeout > 0) T_TO = timeout;
	if(period > 0){
		T_KA = period;
		set_ka_values();
	}

	if(state == 10)	state = KEEP_ALIVE_ON;
	if(state == 0)	state = KEEP_ALIVE_OFF;

	message = "Keep-Alive data updated.";
	if(tid > 0){
		te = tunt_set_ka_by_tid(tid, KEEP_ALIVE_ON);
		if(te == NULL){
			send_error_response_message(UPMT_C_SET_KEEP, info, "Tunnel does not exist.");
		}
		else{
			dmesg("Keel-Alive activated on tunnel %d", tid);
			message = "Keep-Alive state updated on the selected tunnel.";
		}
	}

	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	send_response_message(UPMT_C_SET_KEEP, 1, msg_data, info);


	return 0;
}


/*******************/

static int upmt_genl_dispatcher(struct sk_buff *skb, struct genl_info *info){
	unsigned long flags;
	int bound, command;
	int cmd_log;

	bound = 0;
	command = info->genlhdr->cmd;

	if (info == NULL){
		dmesge("upmt_genl_dispatcher - info = NULL");
		return -1;
	}
	if (info->genlhdr == NULL){
		dmesge("upmt_genl_dispatcher - info->genlhdr = NULL");
		return -1;
	}

	switch (command) {
		case UPMT_C_SET_TSA:
			bound = upmt_tunt_reserve_port(skb, info);
			break;
		case UPMT_C_SET_TUNNEL:
			bound = upmt_tunt_reserve_port(skb, info);
			break;
		default:
			break;
	}

	cmd_log = 1;
	if(cmd_log == 1) printk("upmt_genl_dispatcher - command received: %d\n", command);
	write_lock_irqsave(&bul_mutex, flags);
	switch (command) {
		case UPMT_C_ECHO:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_ECHO\n");
			upmt_echo(skb, info);
			break;

		case UPMT_C_GET_RULE:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_GET_RULE\n");
			upmt_paft_get(skb, info);
			break;
		case UPMT_C_DEL_RULE:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_DEL_RULE\n");
			upmt_paft_del(skb, info);
			break;
		case UPMT_C_SET_RULE:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_RULE\n");
			upmt_paft_set(skb, info);
			break;
		case UPMT_C_LST_RULE:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_LST_RULE\n");
			upmt_paft_lst(skb, info);
			break;

		case UPMT_C_GET_TUNNEL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_GET_TUNNEL\n");
			upmt_tunt_get(skb, info);
			break;
		case UPMT_C_DEL_TUNNEL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_DEL_TUNNEL\n");
			upmt_tunt_del(skb, info);
			break;
		case UPMT_C_SET_TUNNEL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_TUNNEL\n");
			upmt_tunt_set(skb, info, bound);
			break;
		case UPMT_C_LST_TUNNEL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_LST_TUNNEL\n");
			upmt_tunt_lst(skb, info);
			break;

		case UPMT_C_HANDOVER:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_HANDOVER\n");
			upmt_handover(skb, info);
			break;

		case UPMT_C_AN:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_AN\n");
			upmt_an(skb, info);
			break;

		case UPMT_C_SET_TSA:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_TSA\n");
			upmt_tsa_set(skb, info, bound);
			break;
		case UPMT_C_DEL_TSA:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_DEL_TSA\n");
			upmt_tsa_del(skb, info);
			break;
		case UPMT_C_LST_TSA:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_LST_TSA\n");
			upmt_tsa_lst(skb, info);
			break;

		case UPMT_C_VERBOSE:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_VERBOSE\n");
			upmt_verbose(skb, info);
			break;

		case UPMT_C_FLUSH:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_FLUSH\n");
			upmt_flush(skb, info);
			break;

		case UPMT_C_SET_MDL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_MDL\n");
			upmt_mdl_set(skb, info);
			break;
		case UPMT_C_DEL_MDL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_DEL_MDL\n");
			upmt_mdl_del(skb, info);
			break;
		case UPMT_C_LST_MDL:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_LST_MDL\n");
			upmt_mdl_lst(skb, info);
			break;

		case UPMT_C_SET_PDFT:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_PDFT\n");
			upmt_pdft_set(skb, info);
			break;
		case UPMT_C_DEL_PDFT:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_DEL_PDFT\n");
			upmt_pdft_del(skb, info);
			break;
		case UPMT_C_LST_PDFT:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_LST_PDFT\n");
			upmt_pdft_lst(skb, info);
			break;

		case UPMT_C_SET_KEEP:
			if(cmd_log == 1) printk("upmt_genl_dispatcher - UPMT_C_SET_KEEP\n");
			upmt_keepAlive_set(skb, info);
			break;
		
		default:
			dmesg("upmt_genl_dispatcher - unknow message");
		break;
	}
	write_unlock_irqrestore(&bul_mutex, flags);
	return 0;
}

/*******************/

static struct genl_ops upmt_gnl_ops_echo = {
	.cmd = UPMT_C_ECHO,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_get = {
	.cmd = UPMT_C_GET_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_del = {
	.cmd = UPMT_C_DEL_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_set = {
	.cmd = UPMT_C_SET_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_lst = {
	.cmd = UPMT_C_LST_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_get = {
	.cmd = UPMT_C_GET_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_del = {
	.cmd = UPMT_C_DEL_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_set = {
	.cmd = UPMT_C_SET_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_lst = {
	.cmd = UPMT_C_LST_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_handover = {
	.cmd = UPMT_C_HANDOVER,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_an = {
	.cmd = UPMT_C_AN,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_set = {
	.cmd = UPMT_C_SET_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_del = {
	.cmd = UPMT_C_DEL_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_lst = {
	.cmd = UPMT_C_LST_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_verbose = {
	.cmd = UPMT_C_VERBOSE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_flush = {
	.cmd = UPMT_C_FLUSH,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_set = {
	.cmd = UPMT_C_SET_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_del = {
	.cmd = UPMT_C_DEL_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_lst = {
	.cmd = UPMT_C_LST_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

/*
 * PDFT
 */

static struct genl_ops upmt_gnl_ops_pdft_set = {
	.cmd = UPMT_C_SET_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_pdft_del = {
	.cmd = UPMT_C_DEL_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_pdft_lst = {
	.cmd = UPMT_C_LST_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};

/*
 * KEEP-ALIVE
 */

static struct genl_ops upmt_gnl_ops_keep_set = {
	.cmd = UPMT_C_SET_KEEP,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_genl_dispatcher,
	.dumpit = NULL,
};
/*******************/

//int upmt_genl_register(int FAM){
int upmt_genl_register(){
	int rc;
//	char family[GENL_NAMSIZ];

	// the netlink family name is now concatenated with the hostname of the namespace from where insmod is executed (Sander)
	sprintf(upmt_gnl_family.name,"%s%s",UPMT_GNL_FAMILY_NAME,upmtns->uts_ns->name.nodename);

	// the family can't be too long (why 13? should be 16) (Sander)
	if (strlen(upmt_gnl_family.name) > 13){
		dmesge("upmt_genl_register - hostname too long - unable to register upmt_genl_family");
		return -1;
	}

	/*if(FAM > 0){
		sprintf(family, "%s%s%d",UPMT_GNL_FAMILY_NAME, upmtns->uts_ns->name.nodename, FAM);
		memcpy(upmt_gnl_family.name, family, GENL_NAMSIZ);
		//dmesg("upmt_genl_register: %s", upmt_gnl_family.name);
	}*/

	rc = genl_register_family(&upmt_gnl_family);
	if (rc != 0){
		dmesge("upmt_genl_register - unable to register upmt_genl_family");
		dmesge(upmt_gnl_family.name);
		return -1;
	}
	//dmesg("upmt_genl_register - id ---> %u", upmt_gnl_family.id);

	/*
	 * ECHO
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_echo);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_echo");
		return -1;
	}

	/*
	 * PAFT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_get);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesg("upmt_genl_register - unable to register upmt_gnl_ops_paft_get");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_paft_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_paft_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_paft_lst");
		return -1;
	}

	/*
	 * TUNT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tunt_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_get);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tunt_get");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tunt_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tunt_lst");
		return -1;
	}

	/*
	 * HANDOVER
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_handover);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_handover");
		return -1;
	}

	/*
	 * TSA
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tsa_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tsa_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_tsa_lst");
		return -1;
	}

	/*
	 * Anchor node
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_an);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesg("upmt_genl_register - unable to register upmt_gnl_ops_an");
		return -1;
	}

	/*
	 * Verbose mode
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_verbose);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_verbose");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_flush);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_flush");
		return -1;
	}

	/*
	 * MDL
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_mdl_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_mdl_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_mdl_lst");
		return -1;
	}

	/*
	 * PDFT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_pdft_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_pdft_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_pdft_lst");
		return -1;
	}

	/*
	 * KEEP-ALIVE
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_keep_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		dmesge("upmt_genl_register - unable to register upmt_gnl_ops_keep_set");
		return -1;
	}

	return 0;
}

int upmt_genl_unregister(void){
	int rc;

	rc = genl_unregister_family(&upmt_gnl_family);
	if (rc != 0){
		dmesge("upmt_genl_unregister - unable to unregister upmt_genl_family");
		return -1;
	}

	return 0;
}


MODULE_LICENSE("GPL");
