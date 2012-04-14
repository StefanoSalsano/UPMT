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

#include "include/upmt_pdft.h"

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/netdevice.h>
#include <linux/inetdevice.h>
#include <net/netlink.h>
#include <net/genetlink.h>


int verbose = 0;

static struct genl_family upmt_gnl_family = {
	.id = GENL_ID_GENERATE,
	.hdrsize = 0,
	.name = UPMT_GNL_FAMILY_NAME,
	.version = UPMT_GNL_FAMILY_VERSION,
	.maxattr = UPMT_A_MSG_MAX,
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
	printk("\n\t msg_type:\t %s", c);
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
			printk("\n\t send_response_message - unable to allocate skb");
			return -1;
		}

		skb_head = genlmsg_put(skb, 0, info->snd_seq+1, &upmt_gnl_family, 0, command);
		if (skb_head == NULL) {
			printk("\n\t send_response_message - unable to allocate skb_head");
			return -ENOMEM;
		}

		if(nla_put_string(skb, UPMT_A_MSG_TYPE, RESPONSE_MSG) != 0){
			printk("\n\t send_response_message - unable to put UPMT_A_MSG_TYPE attribute");
			return -1;
		}

		tosend = (remaining < MAX_RULES_PER_MSG) ? remaining : MAX_RULES_PER_MSG;

		for(i = MAX_RULES_PER_MSG * nummsg; i < (MAX_RULES_PER_MSG * nummsg + tosend); i++){
			if((ret = nla_put(skb, msg_data[i].atype, msg_data[i].len, msg_data[i].data)) < 0){
				printk("\n\t send_response_message - unable to put attribute %d for elem %d/%d: %d", msg_data[i].atype, i, n_data, ret);
				return -1;
			}
		}
		
		if (remaining <= MAX_RULES_PER_MSG) { // is last message
			if ( (command == UPMT_C_LST_TUNNEL) || (command == UPMT_C_LST_RULE) || (command == UPMT_C_LST_TSA) ) {
				if (nla_put_string(skb, UPMT_A_LAST_LST_MSG, "lastmsg") != 0) {
					printk("\n\t send_response_message - unable to put attribute UPMT_A_LAST_LST_MSG\n");
					return -1;
				}
			}
		}	

		genlmsg_end(skb, skb_head);

	#if LINUX_VERSION_CODE < UPMT_LINUX_VERSION_CODE
		if(genlmsg_unicast(skb, info->snd_pid ) != 0){
	#else
		if(genlmsg_unicast(&init_net, skb, info->snd_pid ) != 0){
	#endif
			printk("\n\t send_response_message - unable to send response");
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
	char *response = "Hello from upmt module in kernel space.";

	//printk("\n\t upmt_set - ECHO MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_get - error");
		return -1;
	}

	/*
	 * Extracting ECHO request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_echo - msg_a_type - wrong message format %s", msg_a_type);
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
	struct net_device *dev;
	//struct upmt_key *key;
	//struct tunt_entry *te;
	struct paft_entry *pe;
	char *msg_a_type, *response, *iname;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];
	int res, rid;
	char staticrule;

	//printk("\n\t upmt_paft_get - GET MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_paft_get - error");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_paft_get - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	//print_upmt_key(key);

	/*
	 * Creating GET response
	 */

	bul_read_lock_bh();

	pe = paft_search_by_rid(rid);
	//te = paft_get_tun(key);
	staticrule = pe->staticrule;

	if(pe == NULL){
		res = send_error_response_message(UPMT_C_GET_RULE, info, "Rule not found.");
		goto end;
	}

	set_msg_data(&msg_data[0], UPMT_A_PAFT_KEY, &pe->key, sizeof(struct upmt_key));
	if(pe->te == NULL){
		response = "This rule has not a tunnel";
		set_msg_data(&msg_data[1], UPMT_A_MSG_MESSAGE, response, strlen(response));
		set_msg_data(&msg_data[2], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
		n_data = 3;
	}
	else{
		dev = dev_get_by_index(&init_net, pe->te->tp.tl.ifindex);  //XXX use mdl_search_by_idx where idx is the mark
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;
		set_msg_data(&msg_data[1], UPMT_A_TUN_DEV, iname, strlen(iname));
		set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &pe->te->tp, sizeof(struct tun_param));
		set_msg_data(&msg_data[3], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
		n_data = 4;
		dev_put(dev); //XXX
	}

	res = send_response_message(UPMT_C_GET_RULE, n_data, msg_data, info);

end:
	bul_read_unlock_bh();

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

	//printk("\n\t upmt_paft_set - SET MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_paft_set - error");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_paft_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	//printk("\n\t - TID: %d", tid);

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

	bul_write_lock_bh();
	
	if (tid > 0) {
		te = tunt_search_by_tid(tid);
		if (te == NULL) {
			res = send_error_response_message(UPMT_C_SET_RULE, info, "Tunnel does not exist.");
			goto end;
		}
	}
	else { // we are editing a rule which is already set (i.e. setting it static)
		te = paft_get_tun(key);
		if (te == NULL) {
			res = send_error_response_message(UPMT_C_SET_RULE, info, "Rule does not exist (tunnel not provided).");
			goto end;
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
		goto end;
	}

	dev = dev_get_by_index(&init_net, te->tp.tl.ifindex); //XXX use mdl_search_by_idx where idx is the mark
	if(dev == NULL) iname = "Warning! Device does not exist!";
	else iname = dev->name;
	set_msg_data(&msg_data[0], UPMT_A_TUN_DEV, iname, strlen(iname));
	set_msg_data(&msg_data[1], UPMT_A_PAFT_KEY, key, sizeof(struct upmt_key));
	set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));
	set_msg_data(&msg_data[3], UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
	n_data = 4;
	dev_put(dev); //XXX


	res = send_response_message(UPMT_C_SET_RULE, n_data, msg_data, info);

end:
	bul_write_unlock_bh();
	return res;
}

static int upmt_paft_del(struct sk_buff *skb, struct genl_info *info){
	int rid, res;
	struct paft_entry *pe;
	char *msg_a_type;

	//printk("\n\t upmt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_del - error");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_del - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	//printk("\n\t - RID: %d", rid);

	/*
	 * Creating DEL response
	 */

	bul_write_lock_bh();
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
	bul_write_unlock_bh();
	return res;
}

static int upmt_paft_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct paft_entry **pea = NULL;
	struct RESP_MSG_DATA *msg_data = NULL;
	int N, i, res;
	long pea_extrasize, msg_data_extrasize;

	//printk("\n\t upmt_paft_lst - LST MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_paft_lst - error");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_paft_lst - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	N = paft_count();		//XXX fuori dal lock, solo per inizializzare buffer messaggi
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_RULE, info, "PAFT is empty.");
		goto end;
	}

	pea_extrasize = (sizeof(struct paft_entry *) * N * 6)/5;		//XXX 20% extra per compensare paft_count() fuori dal lock
	pea = (struct paft_entry **) vzalloc(pea_extrasize);
	if(pea == NULL){
		printk("upmt_paft_lst - Error - Unable to allocate memory for tpa");
		res = send_error_response_message(UPMT_C_LST_RULE, info, "Error while creating rule list1 (type 'dmesg' for details).");
		goto end;
	}
	
	msg_data_extrasize = (sizeof(struct RESP_MSG_DATA) * (N*4) * 6)/5;		//XXX 20% extra per compensare paft_count() fuori dal lock
	msg_data = (struct RESP_MSG_DATA *) vzalloc(msg_data_extrasize);
	if(msg_data == NULL){
		printk("upmt_paft_lst - Error - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_RULE, info, "Error while creating rule list2 (type 'dmesg' for details).");
		goto fail_msg_data;
	}
	
	bul_read_lock_bh();

	paft_fill_pointers(pea);

	N = paft_count();

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i*4], UPMT_A_PAFT_RID, &pea[i]->rid, sizeof(int));
		set_msg_data(&msg_data[i*4+1], UPMT_A_PAFT_KEY, &pea[i]->key, sizeof(struct upmt_key));
		set_msg_data(&msg_data[i*4+2], UPMT_A_TUN_TID, &pea[i]->te->tp.tid, sizeof(int));
		set_msg_data(&msg_data[i*4+3], UPMT_A_PAFT_STATIC, &pea[i]->staticrule, sizeof(char));
	}

	res = send_response_message(UPMT_C_LST_RULE, N*4, msg_data, info);

	bul_read_unlock_bh();

	vfree(msg_data);

fail_msg_data:
	vfree(pea);

end:
	return res;
}


/*
 * TUNT
 */

static int upmt_tunt_set(struct sk_buff *skb, struct genl_info *info){
	char *iname, *message;
	struct tun_param *tp;
	struct tunt_entry *te;
	struct mdl_entry *mde;
	struct net_device *dev;
	struct in_device *pin_dev;
	int res = 0, DT = 0;
	int bound;

	char *msg_a_type;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//printk("\n\t upmt_tunt_set - SET MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tunt_set - error");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tunt_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	//printk("\n\t - DEV: %s", iname);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	if(tp->tl.ifindex == 1111) DT = 1;   //XXX 1111 can't be used for MARK -- TODO
	//print_upmt_tun_param(tp);

	dev = dev_get_by_name(&init_net, iname); //XXX use mdl_search_by_idx where idx is the MARK
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Device does not exist.");
		goto end;
	}

	pin_dev = (struct in_device *) dev->ip_ptr;  //XXX useless, if the dev doesn't have a IP addr the routing will fail adn the packet will simply be discarded 
	if(pin_dev->ifa_list == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Device has not IP address.");
		goto end;
	}

	tp->tl.ifindex = dev->ifindex;  //XXX use the MARK
	
	dev_put(dev);  //XXX

	bound = 1;
	printk("reserving port %d\n", tp->tl.port);
	res = reserve_port(tp->tl.port);
	if(res < 0){
		printk("reserving port %d FAILED\n", tp->tl.port);
		bound = 0;
		release_port(tp->tl.port);
	}

	bul_write_lock_bh();

	mde = mdl_search(iname);
	if(mde == NULL){
		res = send_error_response_message(UPMT_C_SET_TUNNEL, info, "Error: the device is not under upmt control.");
		goto clear_sock;
	}
	tp->md = &mde->md;

	if (bound == 0) {
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
	}

	/*
	 * Creating SET response
	 */

	message = "Tunnel accepted.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_TUN_DEV, iname, strlen(iname));
	set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));

	res = send_response_message(UPMT_C_SET_TUNNEL, 3, msg_data, info);

clear_sock:
	bul_write_unlock_bh();

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

	//printk("\n\t upmt_tunt_get - GET MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tunt_get - error");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tunt_get - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	//print_upmt_tun_param(tp);

	bul_read_lock_bh();

	if(tp->tid <= 0){
		res = send_error_response_message(UPMT_C_GET_TUNNEL, info, "Tunnel id not valid.");
		goto end;
	}

	te = tunt_search_by_tid(tp->tid);
	if(te == NULL){
		res = send_error_response_message(UPMT_C_GET_TUNNEL, info, "Tunnel does not exist.");
		goto end;
	}

	dev = dev_get_by_index(&init_net, te->tp.tl.ifindex);  //XXX same as before... useless and dangerous
	if(dev == NULL) iname = "Warning! Device does not exist!";
	else iname = dev->name;

	dev_put(dev); //XXX
	

	message = "Found.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_TUN_DEV, iname, strlen(iname));
	set_msg_data(&msg_data[2], UPMT_A_TUN_PARAM, &te->tp, sizeof(struct tun_param));

	res = send_response_message(UPMT_C_GET_TUNNEL, 3, msg_data, info);
end:
	bul_read_unlock_bh();
	return res;
}

static int upmt_tunt_del(struct sk_buff *skb, struct genl_info *info){
	struct tun_param *tp;
	struct tunt_entry *te;
	int res;

	char *msg_a_type, *message;
	struct RESP_MSG_DATA msg_data[UPMT_A_MSG_MAX];

	//printk("\n\t upmt_tunt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tunt_del - error");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tunt_del - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	tp = (struct tun_param *) extract_nl_attr(info, UPMT_A_TUN_PARAM);
	if(tp->tl.ifindex == 1111) tp->tid = 0; //default tunnel
	//print_upmt_tun_param(tp);

	if(tp->tid < 0){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Tunnel id not valid.");
		goto end;
	}

	bul_write_lock_bh();

	if(tp->tid == 0){
		if(tunt_del_default() < 0) res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Default tunnel does not exist.");
		else res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Default tunnel deleted.");
		goto end;
	}

	te = tunt_search_by_tid(tp->tid);
	if(te == NULL){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Tunnel does not exist.");
		goto end;
	}

	res = tunt_delete(&te->tp);
	if(res != 0){
		res = send_error_response_message(UPMT_C_DEL_TUNNEL, info, "Error while deleting tunnel.");
		goto end;
	}

	message = "Tunnel deleted.";
	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	res = send_response_message(UPMT_C_DEL_TUNNEL, 1, msg_data, info);

end:
	bul_write_unlock_bh();
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

	//printk("\n\t upmt_tunt_del - DEL MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tunt_lst - error");
		return -1;
	}

	/*
	 * Extracting GET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tunt_lst - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	bul_read_lock_bh();

	N = tunt_count(); //this function counts the default tunnel too

	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "TUNT is empty.");
		goto end;
	}

	tpa = (struct tun_param **) kzalloc(sizeof(struct tun_param *)*N, GFP_ATOMIC);
	if(tpa == NULL){
		printk("upmt_tunt_lst - Error - Unable to allocating memory for tpa");
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	tunt_fill_pointers(tpa);

	n_data = N*2 + 1; //adding a field for the sentence "Default tunnel: ..."

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(n_data), GFP_ATOMIC);
	if(msg_data == NULL){
		printk("upmt_tunt_lst - Error - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_TUNNEL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		dev = dev_get_by_index(&init_net, tpa[i]->tl.ifindex); //XXX
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;

		dev_put(dev); //XXX

		set_msg_data(&msg_data[i*2], UPMT_A_TUN_PARAM, tpa[i], sizeof(struct tun_param));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_DEV, iname, strlen(iname));
	}

	if(default_tunnel != NULL){
		sprintf(message, "\n ---> Default Tunnel: %d", default_tunnel->tp.tid);
		set_msg_data(&msg_data[n_data-1], UPMT_A_MSG_MESSAGE, message, strlen(message));
	}
	else{
		sprintf(message, "\n ---> Default Tunnel: NOT DEFINED");
		set_msg_data(&msg_data[n_data-1], UPMT_A_MSG_MESSAGE, message, strlen(message));
	}

	res = send_response_message(UPMT_C_LST_TUNNEL, n_data, msg_data, info);

end:
	bul_read_unlock_bh();
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

	//printk("\n\t upmt_tunt_handover - handover MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tunt_handover - error");
		return -1;
	}

	/*
	 * Extracting HANDOVER request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tunt_handover - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);
	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	//printk("\n\t TID: %d", tid);
	rid = *(int *) extract_nl_attr(info, UPMT_A_PAFT_RID);
	//printk("\n\t RID: %d", rid);

	bul_write_lock_bh();

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
	bul_write_unlock_bh();
	return res;
}

static int upmt_tsa_set(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname;
	struct tun_local *tl;
	struct tsa_entry *tsae;
	int res = 0;
	int bound;

	//printk("\n\t upmt_tsa_set - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tsa_set - error");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tsa_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	tl = (struct tun_local *) extract_nl_attr(info, UPMT_A_TUN_LOCAL);

	dev = dev_get_by_name(&init_net, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "Device does not exist.");
		goto end;
	}
	tl->ifindex = dev->ifindex;

	dev_put(dev); //XXX	

	bound = 1;
	res = reserve_port(tl->port);
	if(res < 0){
		release_port(tl->port);
		if(res == -1) res = send_error_response_message(UPMT_C_SET_TSA, info, "Unable to set TSA: port already reserved.");
		if(res == -2) res = send_error_response_message(UPMT_C_SET_TSA, info, "Unable to set TSA: internal error (type 'dmesg' for details).");
		goto end;
	}

	bul_write_lock_bh();

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
	bul_write_unlock_bh();

end:	
	return res;
}

static int upmt_tsa_del(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname;
	struct tun_local *tl;
	struct tsa_entry *tsae;
	int res;

	//printk("\n\t upmt_tsa_del - del MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tsa_del - error");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tsa_del - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	tl = (struct tun_local *) extract_nl_attr(info, UPMT_A_TUN_LOCAL);

	/****/

	/*printk("\t\n iname: %s", iname);
	print_upmt_tun_local(tl);
	send_error_response_message(UPMT_C_DEL_TSA, info, "Message received.");
	return 0;*/

	/****/

	dev = dev_get_by_name(&init_net, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_DEL_TSA, info, "Device does not exist.");
		goto end;
	}
	tl->ifindex = dev->ifindex;

	dev_put(dev);  //XXX

	bul_write_lock_bh();

	tsae = tsa_search(tl);
	if(tsae == NULL){
		res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA not present.");
		goto end;
	}
	tsa_delete(tl);
	release_port(tl->port);
	tunt_delete_by_tsa(tl);

	res = send_error_response_message(UPMT_C_SET_TSA, info, "TSA deleted.");

end:
	bul_write_unlock_bh();
	return res;
}


static int upmt_tsa_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type, *iname;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct tun_local **tla = NULL;
	struct net_device *dev;
	int N, i, res;

#ifdef PRINT_INTERRUPT_CONTEXT
	printk("\n\n\treceiving netlink message");
	check_context();
#endif

	//printk("\n\t upmt_tsa_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_tsa_lst - error");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_tsa_lst - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	bul_read_lock_bh();

	N = tsa_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_TSA, info, "TSA is empty.");
		goto end;
	}

	tla = (struct tun_local **) kzalloc(sizeof(struct tun_local *)*N, GFP_ATOMIC);
	if(tla == NULL){
		printk("upmt_tsa_lst - Error - Unable to allocating memory for tla");
		res = send_error_response_message(UPMT_C_LST_TSA, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	tsa_fill_pointers(tla);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N*2+1), GFP_ATOMIC);
	if(msg_data == NULL){
		printk("upmt_tsa_lst - Error - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_TSA, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		dev = dev_get_by_index(&init_net, tla[i]->ifindex); //XXX
		if(dev == NULL) iname = "Warning! Device does not exist!";
		else iname = dev->name;

		dev_put(dev);  //XXX

		set_msg_data(&msg_data[i*2], UPMT_A_TUN_LOCAL, tla[i], sizeof(struct tun_local));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_DEV, iname, strlen(iname));
	}

	res = send_response_message(UPMT_C_LST_TSA, N*2, msg_data, info);

end:
	bul_read_unlock_bh();
	kfree(tla);
	kfree(msg_data);
	return res;
}

static int upmt_an(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	char response[50];
	int mark;

	if (info == NULL){
		printk("\n\t upmt_an - error");
		return -1;
	}

	/*
	 * Extracting AN request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_an - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	mark = *(int *) extract_nl_attr(info, UPMT_A_AN_MARK);

	bul_write_lock_bh();
	an_mark = (u32) mark;
	bul_write_unlock_bh();

	sprintf(response, "AN_MARK updated. Value: %d", mark);
	return send_error_response_message(UPMT_C_SET_RULE, info, response);
}

static int upmt_verbose(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	char response[50];
	int new_verbose;

	if (info == NULL){
		printk("\n\t upmt_verbose - error");
		return -1;
	}

	/*
	 * Extracting AN request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_verbose - msg_a_type - wrong message format %s", msg_a_type);
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
		printk("\n\t upmt_flush - error");
		return -1;
	}

	/*
	 * Extracting FLUSH request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_flush - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	table = (char *) extract_nl_attr(info, UPMT_A_MSG_MESSAGE);

	bul_write_lock_bh();
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
	bul_write_unlock_bh();

	return send_error_response_message(UPMT_C_FLUSH, info, response);
}

static int upmt_mdl_set(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname, *message;
	struct mdl_entry *me;
	int res;
	struct RESP_MSG_DATA msg_data[2];

	//printk("\n\t upmt_mdl_set - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_mdl_set - error");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_mdl_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);

	dev = dev_get_by_name(&init_net, iname); //XXX
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_SET_MDL, info, "Device does not exist.");
		return res;
	}

	bul_write_lock_bh();

	me = mdl_search(iname);
	if(me == NULL){
		me = mdl_insert(iname);
		if(me == NULL){
			res = send_error_response_message(UPMT_C_SET_MDL, info, "Unable to set device and mark: internal error (type 'dmesg' for details).");
			goto end;
		}
		message = "Device is now under upmt control.";
	}
	else message = "Device is already under upmt control.";

	set_msg_data(&msg_data[0], UPMT_A_MSG_MESSAGE, message, strlen(message));
	set_msg_data(&msg_data[1], UPMT_A_MARK_DEV, &me->md, sizeof(struct mark_dev));

	res = send_response_message(UPMT_C_SET_MDL, 2, msg_data, info);

end:
	dev_put(dev); //XXX
	bul_write_unlock_bh();
	return res;
}

static int upmt_mdl_del(struct sk_buff *skb, struct genl_info *info){
	struct net_device *dev;
	char *msg_a_type, *iname;
	int res;
	struct socket *sock = NULL;

	//printk("\n\t upmt_mdl_del - del MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_mdl_del - error");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_mdl_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	iname = (char *) extract_nl_attr(info, UPMT_A_TUN_DEV);
	dev = dev_get_by_name(&init_net, iname);  //XXX this is a error. of course it can be null...
	if(dev == NULL){
		res = send_error_response_message(UPMT_C_DEL_MDL, info, "Device does not exist.");
		goto end_no_lock;  //before was "goto end;" ---> PANIK
	}

	dev_put(dev);  //XXX

	bul_write_lock_bh();

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
	bul_write_unlock_bh();
	if (sock) sock_release(sock); //if you do this whene the device has been removed ----> panik
end_no_lock:
	return res;
}

static int upmt_mdl_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct mark_dev **mds = NULL;
	int N, i, res;

	//printk("\n\t upmt_mdl_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_mdl_lst - error");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_mdl_lst - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	bul_read_lock_bh();

	N = mdl_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_MDL, info, "There are no devices under upmt control.");
		goto end;
	}

	mds = (struct mark_dev **) kzalloc(sizeof(struct mark_dev *)*N, GFP_ATOMIC);
	if(mds == NULL){
		printk("upmt_mld_lst - Error - Unable to allocating memory for mds");
		res = send_error_response_message(UPMT_C_LST_MDL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	mdl_fill_pointers(mds);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N), GFP_ATOMIC);
	if(msg_data == NULL){
		printk("upmt_mdl_lst - Error - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_MDL, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i], UPMT_A_MARK_DEV, mds[i], sizeof(struct mark_dev));
	}

	res = send_response_message(UPMT_C_LST_TSA, N, msg_data, info);

end:
	bul_read_unlock_bh();
	kfree(mds);
	kfree(msg_data);
	return res;



	return 0;
}

/*
 * PDFT
 */

static int upmt_pdft_set(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct tunt_entry *te;
	int tid, res;
	unsigned int ip;

	//printk("\n\t upmt_pdft_set - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_pdft_set - error");
		return -1;
	}

	/*
	 * Extracting SET request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_pdft_set - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	ip = *(unsigned int *) extract_nl_attr(info, UPMT_A_IP_ADDR);
	tid = *(int *) extract_nl_attr(info, UPMT_A_TUN_TID);
	//printk("IpAddress: %u\n", ip);
	//printk("TID: %d\n", tid);

	/*
	 * Creating SET response
	 */

	bul_write_lock_bh();

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
	bul_write_unlock_bh();
	return res;
}

static int upmt_pdft_del(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	int res;
	unsigned int ip;

	//printk("\n\t upmt_pdft_set - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_pdft_del - error");
		return -1;
	}

	/*
	 * Extracting DEL request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_pdft_del - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}
	//print_upmt_type_msg(msg_a_type);

	ip = *(unsigned int *) extract_nl_attr(info, UPMT_A_IP_ADDR);

	/*
	 * Creating DEL response
	 */

	bul_write_lock_bh();

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
	bul_write_unlock_bh();
	return res;
}

static int upmt_pdft_lst(struct sk_buff *skb, struct genl_info *info){
	char *msg_a_type;
	struct RESP_MSG_DATA *msg_data = NULL;
	struct pdft_entry **pea = NULL;
	int N, i, res;

	//printk("\n\t upmt_mdl_lst - set MESSAGE RECEIVED");
	if (info == NULL){
		printk("\n\t upmt_pdft_lst - error");
		return -1;
	}

	/*
	 * Extracting LST request message
	 */

	msg_a_type = (char *) extract_nl_attr(info, UPMT_A_MSG_TYPE);
	if(strcmp(msg_a_type, REQUEST_MSG) != 0){
		printk("\n\t upmt_pdft_lst - msg_a_type - wrong message format %s", msg_a_type);
		return -1;
	}

	bul_read_lock_bh();

	N = pdft_count();
	if(N == 0){
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "PDFT is empty: there are no destination rules.");
		goto end;
	}

	pea = (struct pdft_entry **) kzalloc(sizeof(struct pdft_entry *)*N, GFP_ATOMIC);
	if(pea == NULL){
		printk("upmt_pdft_lst - Error - Unable to allocating memory for pea");
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}
	pdft_fill_pointers(pea);

	msg_data = (struct RESP_MSG_DATA *) kzalloc(sizeof(struct RESP_MSG_DATA)*(N*2), GFP_ATOMIC);
	if(msg_data == NULL){
		printk("upmt_pdft_lst - Error - Unable to allocating memory for msg_data");
		res = send_error_response_message(UPMT_C_LST_PDFT, info, "Memory error (type 'dmesg' for details).");
		goto end;
	}

	for(i=0; i<N; i++){
		set_msg_data(&msg_data[i*2], UPMT_A_IP_ADDR, &pea[i]->ip, sizeof(unsigned int));
		set_msg_data(&msg_data[i*2+1], UPMT_A_TUN_TID, &pea[i]->te->tp.tid, sizeof(int));
	}

	res = send_response_message(UPMT_C_LST_PDFT, N*2, msg_data, info);

end:
	bul_read_unlock_bh();
	kfree(pea);
	kfree(msg_data);
	return res;
}

/*******************/
static struct genl_ops upmt_gnl_ops_echo = {
	.cmd = UPMT_C_ECHO,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_echo,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_get = {
	.cmd = UPMT_C_GET_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_paft_get,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_del = {
	.cmd = UPMT_C_DEL_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_paft_del,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_set = {
	.cmd = UPMT_C_SET_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_paft_set,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_paft_lst = {
	.cmd = UPMT_C_LST_RULE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_paft_lst,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_get = {
	.cmd = UPMT_C_GET_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tunt_get,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_del = {
	.cmd = UPMT_C_DEL_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tunt_del,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_set = {
	.cmd = UPMT_C_SET_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tunt_set,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tunt_lst = {
	.cmd = UPMT_C_LST_TUNNEL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tunt_lst,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_handover = {
	.cmd = UPMT_C_HANDOVER,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_handover,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_an = {
	.cmd = UPMT_C_AN,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_an,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_set = {
	.cmd = UPMT_C_SET_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tsa_set,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_del = {
	.cmd = UPMT_C_DEL_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tsa_del,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_tsa_lst = {
	.cmd = UPMT_C_LST_TSA,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_tsa_lst,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_verbose = {
	.cmd = UPMT_C_VERBOSE,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_verbose,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_flush = {
	.cmd = UPMT_C_FLUSH,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_flush,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_set = {
	.cmd = UPMT_C_SET_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_mdl_set,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_del = {
	.cmd = UPMT_C_DEL_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_mdl_del,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_mdl_lst = {
	.cmd = UPMT_C_LST_MDL,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_mdl_lst,
	.dumpit = NULL,
};

/*
 * PDFT
 */

static struct genl_ops upmt_gnl_ops_pdft_set = {
	.cmd = UPMT_C_SET_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_pdft_set,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_pdft_del = {
	.cmd = UPMT_C_DEL_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_pdft_del,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_pdft_lst = {
	.cmd = UPMT_C_LST_PDFT,
	.flags = 0,
	.policy = upmt_genl_policy,
	.doit = upmt_pdft_lst,
	.dumpit = NULL,
};
/*******************/

int upmt_genl_register(void){
	int rc;

	rc = genl_register_family(&upmt_gnl_family);
	if (rc != 0){
		printk("\n\t upmt_genl_register - unable to register upmt_genl_family");
		return -1;
	}
	//printk("\n\t upmt_genl_register - id ---> %u", upmt_gnl_family.id);

	/*
	 * ECHO
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_echo);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_echo");
		return -1;
	}

	/*
	 * PAFT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_get);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_paft_get");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_paft_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_paft_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_paft_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_paft_lst");
		return -1;
	}

	/*
	 * TUNT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tunt_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_get);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tunt_get");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tunt_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tunt_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tunt_lst");
		return -1;
	}

	/*
	 * HANDOVER
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_handover);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_handover");
		return -1;
	}

	/*
	 * TSA
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tsa_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tsa_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_tsa_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_tsa_lst");
		return -1;
	}

	/*
	 * Anchor node
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_an);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_an");
		return -1;
	}

	/*
	 * Verbose mode
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_verbose);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_verbose");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_flush);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_flush");
		return -1;
	}

	/*
	 * MDL
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_mdl_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_mdl_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_mdl_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_mdl_lst");
		return -1;
	}

	/*
	 * PDFT
	 */

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_set);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_pdft_set");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_del);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_pdft_del");
		return -1;
	}

	rc = genl_register_ops(&upmt_gnl_family, &upmt_gnl_ops_pdft_lst);
	if (rc != 0){
		genl_unregister_family(&upmt_gnl_family);
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_pdft_lst");
		return -1;
	}

	return 0;
}

int upmt_genl_unregister(void){
	int rc;

	rc = genl_unregister_family(&upmt_gnl_family);
	if (rc != 0){
		printk("\n\t upmt_genl_unregister - unable to unregister upmt_genl_family");
		return -1;
	}

	return 0;
}


MODULE_LICENSE("GPL");
