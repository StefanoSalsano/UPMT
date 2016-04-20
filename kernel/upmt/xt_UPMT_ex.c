//Module author: Alessio Bianchi

#include <net/sock.h>
#include <net/genetlink.h>
#include <net/ip.h>
#include <net/ipv6.h>
#include <linux/netfilter.h>
#include <linux/skbuff.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/ip.h>
#include <linux/udp.h>
#include <linux/version.h>
#include <linux/fs.h>
#include <linux/string.h>
#include <linux/unistd.h>
#include <linux/netfilter_ipv4.h>
#include <linux/inetdevice.h> 
#include <linux/netdevice.h>
#include <linux/utsname.h>

#include <linux/sched.h>
#include <linux/pid.h>

#include <linux/notifier.h>
#include <net/netfilter/nf_conntrack.h>
#include <net/netfilter/nf_conntrack_ecache.h>
#include <net/net_namespace.h>
#include <net/netfilter/ipv4/nf_conntrack_ipv4.h>
#include <linux/netfilter/nf_conntrack_common.h>

#include <linux/mm.h>
#include <linux/highmem.h>
#include <linux/pagemap.h>
#include <linux/list.h>
#include <net/netlink.h>
#include <net/route.h>

#include "include/upmt.h"
#include "include/upmt_conntracker_appmon.h"
#include "include/xt/compat_xtables.h"
#include "include/upmt_paft.h"
#include "include/upmt_stamp.h"
#include "include/upmt_util.h"

#include "include/xt_UPMT_ex.h"
#include "include/upmt_locks.h"

#ifdef ANDROID
//buffer for pid_to_exe_name
char cmdline_buffer[PAGE_SIZE];
#endif

#define PAFT_DELETION 1

static struct packet_type out_pt;

struct app_node {
	struct list_head list;
	char appname[MAX_APPNAME_LENGTH];
	int tid;
	unsigned int vipa; //Fabio Patriarca
};

struct no_upmt_node {
	struct list_head list;
	char appname[MAX_APPNAME_LENGTH];
};

struct app_flow_node {
	struct list_head list;
	//unsigned int proto:8;
	unsigned int daddr:32;
	//unsigned int dport:16;
	unsigned int tid;
};

struct app_node			apps;
struct no_upmt_node		no_upmt;
struct app_flow_node	app_flow;

// application monitor pid to send messages to
unsigned int appmon_pid = 0;

static int default_tid = 0;

// sequence number for connection notifications messages
unsigned int conn_notif_seqno = 0;

struct genl_family upmt_appmgr_gnl_family = {
	.id = GENL_ID_GENERATE,
	.hdrsize = 0,
//	Family name is now computed during module loading for namespace support (Sander)
//	.name = UPMT_GNL_FAMILY_NAME,
	.version = UPMT_GNL_FAMILY_VERSION,
	.maxattr = UPMT_APPMON_A_MAX,
	.netnsok = true //this allows the family to be visible across different network namespaces (Sander)
};

struct nla_policy upmt_appmgr_genl_policy[UPMT_APPMON_A_MAX + 1] = {
	[UPMT_APPMON_A_UNSPEC]			= { .type = NLA_BINARY },
	[UPMT_APPMON_A_ASSOCIATE]		= { .type = NLA_U32 },
	[UPMT_APPMON_A_APP]				= { .type = NLA_BINARY },
	[UPMT_APPMON_A_NO_UPMT]			= { .type = NLA_BINARY },
	[UPMT_APPMON_A_APP_FLOW]		= { .type = NLA_BINARY },
	[UPMT_APPMON_A_NEW_CONN]		= { .type = NLA_BINARY },
	[UPMT_APPMON_A_DEL_CONN]		= { .type = NLA_BINARY },
	[UPMT_APPMON_A_DUMP_LIST_ID]	= { .type = NLA_U32 },
};

int app_cmd_handler(struct sk_buff *skb, struct genl_info *info);
int no_upmt_cmd_handler(struct sk_buff *skb, struct genl_info *info);
int app_flow_cmd_handler(struct sk_buff *skb, struct genl_info *info);
int associate_cmd_handler(struct sk_buff *skb, struct genl_info *info);
int dump_list_cmd_handler(struct sk_buff *skb, struct genl_info *info);

static struct genl_ops upmt_gnl_ops_app_cmd = {
	.cmd = UPMT_APPMON_C_APP,
	.flags = 0,
	.policy = upmt_appmgr_genl_policy,
	.doit = app_cmd_handler,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_no_upmt_cmd = {
	.cmd = UPMT_APPMON_C_NO_UPMT,
	.flags = 0,
	.policy = upmt_appmgr_genl_policy,
	.doit = no_upmt_cmd_handler,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_app_flow_cmd = {
	.cmd = UPMT_APPMON_C_APP_FLOW,
	.flags = 0,
	.policy = upmt_appmgr_genl_policy,
	.doit = app_flow_cmd_handler,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_associate_cmd = {
	.cmd = UPMT_APPMON_C_ASSOCIATE,
	.flags = 0,
	.policy = upmt_appmgr_genl_policy,
	.doit = associate_cmd_handler,
	.dumpit = NULL,
};

static struct genl_ops upmt_gnl_ops_dump_list_cmd = {
	.cmd = UPMT_APPMON_C_DUMP_LIST,
	.flags = 0,
	.policy = upmt_appmgr_genl_policy,
	.doit = dump_list_cmd_handler,
	.dumpit = NULL,
};

// ########################  UTILITY FUNCTIONS  ########################
void *extract_nl_attr(const struct genl_info *info, const int atype) {
	struct nlattr *na;
	void *data = NULL;
	na = info->attrs[atype];
	if(na) data = nla_data(na);
	return data;
}

static void calculate_upmt_key(struct upmt_key *k, struct sk_buff *skb) {
	struct iphdr *ip_header;
	struct udphdr *udp_header;
	struct tcphdr *tcp_header;

	if (skb == NULL) return;

	ip_header = (struct iphdr *)skb_network_header(skb);
	k->proto = ip_header->protocol;
	k->saddr = ip_header->saddr;
	k->daddr = ip_header->daddr;

	if (ip_header->protocol == IPPROTO_TCP) {
		tcp_header = (struct tcphdr *) skb_transport_header(skb);
		k->sport = ntohs(tcp_header->source);
		k->dport = ntohs(tcp_header->dest);
	}
	else if (ip_header->protocol == IPPROTO_UDP) {
		udp_header = (struct udphdr *) skb_transport_header(skb);
		k->sport = htons(udp_header->source);
		k->dport = htons(udp_header->dest);
	}
}


#ifdef ANDROID
/*
 * From mm/nommu.c
 * NOT exported by the kernel
 * 
 * Access another process' address space.
 * - source/target buffer must be kernel space
 */
static int upmt_access_process_vm(struct mm_struct *mm, unsigned long addr, void *buf, int len, int write)
{               
        struct vm_area_struct *vma;
        
        if (addr + len < addr)
                return 0;
        
        if (!mm)
                return 0;

        //down_read(&mm->mmap_sem);

        /* the access must start within one of the target process's mappings */
        vma = find_vma(mm, addr);
        if (vma) {
                /* don't overrun this mapping */
                if (addr + len >= vma->vm_end)
                        len = vma->vm_end - addr;

                /* only read or write mappings where it is permitted */
                if (write && vma->vm_flags & VM_MAYWRITE)
                        len -= copy_to_user((void *) addr, buf, len);
                else if (!write && vma->vm_flags & VM_MAYREAD)
                        len -= copy_from_user(buf, (void *) addr, len);
                else
                        len = 0;
        } else {
                len = 0;
        }

        //up_read(&mm->mmap_sem);
        return len;
}

/*
 * From fs/proc/base.c
 */
static int proc_pid_cmdline(struct task_struct *task, char * buffer)
{
	int res = 0;
	unsigned int len;
	struct mm_struct *mm = task->mm; //get_task_mm(task);
	if (!mm)
		goto out;
	if (!mm->arg_end)
		goto out_mm;	/* Shh! No looking before we're done */

 	len = mm->arg_end - mm->arg_start;
 
	if (len > PAGE_SIZE)
		len = PAGE_SIZE;
 
	res = upmt_access_process_vm(mm, mm->arg_start, buffer, len, 0);

	// If the nul at the end of args has been overwritten, then
	// assume application is using setproctitle(3).
	if (res > 0 && buffer[res-1] != '\0' && len < PAGE_SIZE) {
		len = strnlen(buffer, res);
		if (len < res) {
		    res = len;
		} else {
			len = mm->env_end - mm->env_start;
			if (len > PAGE_SIZE - res)
				len = PAGE_SIZE - res;
			res += upmt_access_process_vm(mm, mm->env_start, buffer+res, len, 0);
			res = strnlen(buffer, res);
		}
	}
out_mm:
	//mmput(mm);
out:
	return res;
}
#endif

//XXX change the approach. use a cache and don't browse all 
//the pointers to get process name from pid.
const char* pid_to_exe_name(int pid) {
	struct pid* spid;
	struct task_struct* task;
	#ifdef ANDROID
	int len;
	#else
	struct mm_struct *mm;
	struct qstr fname;
	struct file * exe_file;
	struct dentry * dentry;
	#endif
	if (current->nsproxy == NULL) {
		dmesg("upmt_prefilter: BUG nsproxy == NULL");
		return NULL;
	}
	
	//dmesg("pid_to_exe_name - new request for pid %i", pid);

	#ifdef ANDROID
	spid = find_get_pid(pid);
	if (spid == NULL) return NULL;
	task = pid_task(spid, PIDTYPE_PID);
	if (task == NULL) return NULL;
	
	len = proc_pid_cmdline(task, cmdline_buffer);
	if (len > 0)
		return cmdline_buffer;
	else
		return NULL;
	#else
	//XXX OMG!!! XXX
	//if (!(spid = find_pid_ns(pid,upmtns->pid_ns))) return NULL; // now using pid namespace (Sander)
	if (!(spid = find_get_pid(pid))) return NULL; // now using pid namespace (Sander)
	if (!(task = pid_task(spid, PIDTYPE_PID))) return NULL;
	if (!(mm = get_task_mm(task))) return NULL; 
	if (!(exe_file = mm->exe_file)) return NULL;
	if (!(dentry = exe_file->f_path.dentry)) return NULL;
	fname = dentry->d_name;
	//XXX wr should call mm_put() but it sleeps....
	//we'll get rid of this problem with the new approach

	//dmesg("pid_to_exe_name - appname: %s", fname.name);
	return fname.name;
	#endif
}

/*void print_ip_address(unsigned int addr){
	dmesg("%d.", ((unsigned char *)&addr)[0]);
	dmesg("%d.", ((unsigned char *)&addr)[1]);
	dmesg("%d.", ((unsigned char *)&addr)[2]);
	dmesg("%d",  ((unsigned char *)&addr)[3]);
}*/

/*void print_upmt_key(const struct upmt_key *k){
	dmesg(" proto: %d", k->proto);
	dmesg(" saddr: "); print_ip_address(k->saddr);
	dmesg(" daddr: "); print_ip_address(k->daddr);
	dmesg(" sport: %d", k->sport);
	dmesg(" dport: %d", k->dport);
}*/
// #####################################################################

int app_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_app_msg* cmd;
	struct app_node *tmp;
	struct list_head *pos, *q;
	unsigned long flag;

	cmd = (struct upmt_app_msg*) extract_nl_attr(info, UPMT_APPMON_A_APP);

	write_lock_irqsave(&bul_mutex, flag);

	if (cmd->command == CMD_ADD) {
		dmesg("app_cmd_handler: gestione comando CMD_ADD [%s,%pI4 ---> %d]", cmd->appname, &cmd->vipa, cmd->tid);
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			if ((strncasecmp(cmd->appname, tmp->appname, MAX_APPNAME_LENGTH) == 0)&&(cmd->vipa == tmp->vipa)) {
				tmp->tid = cmd->tid;
				goto end;
			}
		}
		
		tmp = (struct app_node *) kmalloc(sizeof(struct app_node), GFP_ATOMIC);
		if(tmp == NULL){
			dmesge("app_cmd_handler: memory error - tmp = NULL");
			write_unlock_irqrestore(&bul_mutex, flag);
			return -1;
		}

		strncpy(tmp->appname, cmd->appname, MAX_APPNAME_LENGTH);
		tmp->tid = cmd->tid;
		tmp->vipa = cmd->vipa; //Fabio Patriarca
		list_add_tail(&(tmp->list), &(apps.list));

		//if(tmp->vipa != 0) dmesg("xt_UPMT_ex: added <%s,%d,%pI4> to app list ---> VIPA: ", tmp->appname, tmp->tid, tmp->vipa); //Fabio Patriarca
		//else dmesg("xt_UPMT_ex: added <%s,%d> to app list", tmp->appname, tmp->tid); //Fabio Patriarca
	}
	else if (cmd->command == CMD_RM) {
		dmesg("app_cmd_handler: gestione comando CMD_RM");
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			if (strncasecmp(cmd->appname, tmp->appname, MAX_APPNAME_LENGTH) == 0) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		dmesg("app_cmd_handler: gestione comando CMD_FLUSH_LIST");
		list_for_each_safe(pos, q, &(apps.list)){
			tmp = list_entry(pos, struct app_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	else if (cmd->command == CMD_SET_DEFAULT_TID) {
		dmesg("app_cmd_handler: gestione comando CMD_SET_DEFAULT_TID");
		default_tid = cmd->tid;
	}
end:
	write_unlock_irqrestore(&bul_mutex, flag);
	return 0;
}

int no_upmt_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_no_upmt_msg* cmd;
	struct no_upmt_node *tmp;
	struct list_head *pos, *q;
	int found;
	unsigned long flag;

	//lock_ex();
	write_lock_irqsave(&bul_mutex, flag);

	cmd = (struct upmt_no_upmt_msg*) extract_nl_attr(info, UPMT_APPMON_A_NO_UPMT);

	if (cmd->command == CMD_ADD) {
		dmesg("no_upmt_cmd_handler: gestione comando CMD_ADD");
		found = 0;
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				found = 1;
				break;
			}
		}
		if (found == 0) {
			tmp = (struct no_upmt_node *) kmalloc(sizeof(struct no_upmt_node), GFP_ATOMIC);
			strncpy(tmp->appname, cmd->appname, MAX_APPNAME_LENGTH);
			list_add_tail(&(tmp->list), &(no_upmt.list));
			//dmesg("added <%s> to no_upmt list", tmp->appname);
		}
		else{
			//dmesg("app %s already in the no_upmt list", cmd->appname);
		}
	}
	else if (cmd->command == CMD_RM) {
		dmesg("no_upmt_cmd_handler: gestione comando CMD_RM");
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		dmesg("no_upmt_cmd_handler: gestione comando CMD_FLUSH_LIST");
		list_for_each_safe(pos, q, &(no_upmt.list)){
			tmp = list_entry(pos, struct no_upmt_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	write_unlock_irqrestore(&bul_mutex, flag);
	return 0;
}

int app_flow_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_app_flow_msg *cmd;
	struct app_flow_node *tmp;
	struct list_head *pos, *q;
	int found;
	unsigned long flag;

	write_lock_irqsave(&bul_mutex, flag);

	cmd = (struct upmt_app_flow_msg*) extract_nl_attr(info, UPMT_APPMON_A_APP_FLOW);

	if (cmd->command == CMD_ADD) {
		dmesg("app_flow_cmd_handler: gestione comando CMD_ADD");
		found = 0;
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			if ((cmd->daddr == tmp->daddr) && (cmd->tid == tmp->tid)) {
				found = 1;
				break;
			}
		}
		if (found == 0) {
			tmp = (struct app_flow_node *) kmalloc(sizeof(struct app_flow_node), GFP_ATOMIC);
			tmp->daddr = cmd->daddr;
			tmp->tid = cmd->tid;
			list_add_tail(&(tmp->list), &(app_flow.list));
			dmesg("added <"); print_ip_address(tmp->daddr); dmesg(", %d> to app_flow list", tmp->tid);
		}
		else {
			dmesg("<"); print_ip_address(cmd->daddr); dmesg(", %d> already in the app_flow list", cmd->tid);
		}
	}
	else if (cmd->command == CMD_RM) {
		dmesg("app_flow_cmd_handler: gestione comando CMD_RM");
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			if ((cmd->daddr == tmp->daddr) && (cmd->tid == tmp->tid)) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		dmesg("app_flow_cmd_handler: gestione comando CMD_FLUSH_LIST");
		list_for_each_safe(pos, q, &(app_flow.list)){
			tmp = list_entry(pos, struct app_flow_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	write_unlock_irqrestore(&bul_mutex, flag);
	return 0;
}

int associate_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	int magic = *(int*) extract_nl_attr(info, UPMT_APPMON_A_ASSOCIATE);
	
	// since 3.7.0 genl_info snd_id has been renamed into snd_portid (Sander)
	appmon_pid = info->snd_portid; //save pid of application monitor
	dmesg("UPMT-AppMon with PID %d associated with magic number %d", appmon_pid, magic);
	return 0;
}

int dump_list_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	int list_id;
	struct list_head *pos, *q;
	unsigned long flag;

	//lock_ex();
	write_lock_irqsave(&bul_mutex, flag);

	list_id = *(int*) extract_nl_attr(info, UPMT_APPMON_A_DUMP_LIST_ID);

	if (list_id == MSG_TYPE_APP) {
		struct app_node *tmp;
		dmesg("--- DUMP apps LIST ---");
		dmesg("--- appname\t\ttid ---");
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			dmesg("%s\t%d", tmp->appname, tmp->tid);
		}
		dmesg("--- END DUMP apps LIST ---");
	}
	else if (list_id == MSG_TYPE_NO_UPMT) {
		struct no_upmt_node *tmp;
		dmesg("--- DUMP no_upmt LIST ---");
		dmesg("--- appname ---");
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			dmesg("%s", tmp->appname);
		}
		dmesg("--- END DUMP no_upmt LIST ---");
	}
	else if (list_id == MSG_TYPE_APP_FLOW) {
		struct app_flow_node *tmp;
		dmesg("--- DUMP app_flow LIST ---");
		dmesg("--- daddr ttid ---");
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			print_ip_address(tmp->daddr); dmesg("", tmp->tid);
		}
		dmesg("--- END DUMP app_flow LIST ---");
	}
	write_unlock_irqrestore(&bul_mutex, flag);
	//unlock_ex();
	return 0;
}

int send_conn_notif(struct upmt_new_conn_data *data) {
	struct sk_buff *skb;
	void *skb_head;

	skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
	if (skb == NULL){
		dmesge("send_conn_notif - unable to allocate skb");
		return -1;
	}

	skb_head = genlmsg_put(skb, appmon_pid, conn_notif_seqno++, &upmt_appmgr_gnl_family, 0, UPMT_APPMON_C_NEW_CONN);
	if (skb_head == NULL) {
		dmesge("send_conn_notif - unable to allocate skb_head");
		return -ENOMEM;
	}

	if(nla_put(skb, UPMT_APPMON_A_NEW_CONN, sizeof(struct upmt_new_conn_data), data) != 0){
		dmesge("send_conn_notif - unable to put UPMT_A_MSG_TYPE attribute");
		return -1;
	}

	genlmsg_end(skb, skb_head);

#if LINUX_VERSION_CODE < UPMT_LINUX_VERSION_CODE
	if(genlmsg_unicast(skb, appmon_pid ) != 0){
#else
	if(genlmsg_unicast(upmtns->net_ns, skb, appmon_pid ) != 0){ // now using the proper namespace (Sander)
#endif
		dmesge("send_conn_notif - unable to send response with appmon_pid = %i", appmon_pid);
		return -1;
	}
	return 0;
}

/*static void print_app_node(struct app_node *an, struct upmt_key *key, const char *appname){
	dmesg("Appname:%s", appname);
	dmesg("VIPA:");	print_ip_address(an->vipa);
	dmesg("appname:%s", an->appname);
	dmesg("---> TID:%u", an->tid);
	dmesg("DEST VIPA:");	print_ip_address(key->daddr);
}*/

// invio messaggio netlink contenente la struct upmt_del_conn_data (bonus)
int send_delete_conn_notif(struct upmt_del_conn_data *data) {
        struct sk_buff *skb;
        void *skb_head;

        skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
        if (skb == NULL){
                dmesge("send_delete_conn_notif - unable to allocate skb");
                return -1;
        }

        skb_head = genlmsg_put(skb, appmon_pid, conn_notif_seqno++, &upmt_appmgr_gnl_family, 0, UPMT_APPMON_C_DEL_CONN);
        if (skb_head == NULL) {
                dmesge("send_delete_conn_notif - unable to allocate skb_head");
                return -ENOMEM;
        }

        if(nla_put(skb, UPMT_APPMON_A_DEL_CONN, sizeof(struct upmt_del_conn_data), data) != 0){
                dmesge("send_delete_conn_notif - unable to put UPMT_A_MSG_TYPE attribute");
                return -1;
        }

        genlmsg_end(skb, skb_head);

        if(genlmsg_unicast(&init_net, skb, appmon_pid ) != 0){
                //dmesge("send_delete_conn_notif - unable to send response");
                return -1;
        }
        return 0;
}

unsigned int upmt_tg(struct sk_buff **pskb, const struct xt_action_param * tp) {
	struct sk_buff *skb = *pskb;
	struct upmt_key key;
	struct upmt_new_conn_data notif_data;
	const char *appname;
	unsigned long flag;	
	int res;
	struct pid* tmp;
	int stampa = 0;

	tmp = find_pid_ns(skb->tgid,&init_pid_ns);
	//dmesg("upmt_tg new request for tgid : %i", skb->tgid);
	skb->tgid = pid_nr_ns(tmp,upmtns->pid_ns);
	//dmesg("upmt_tg namespace modified tgid: %i", skb->tgid);

	if (skb->mark == NO_UPMT_MARK)
		return NF_ACCEPT;

	//Istruzione temporaneamente disabilitata per RME
	//if (default_tid == -1) return NF_ACCEPT; // do nothing until we receive a valid default tunnel

	if (skb->tgid <= 0) return NF_ACCEPT;
	
	//XXX for UDP packets, if skb->nfct == 2 and STATE == NEW, this is a packet for a connection already established but not yet replayed.
	//in this case we don't want the notification, even though for NETFILTER the state of this packet is NEW
	if (skb->nfct->use.counter == 2)	
		return NF_ACCEPT;
	
	if ((appname = pid_to_exe_name(skb->tgid)) == NULL) {
		dmesg("upmt_tg: error finding exe name for pid %d", skb->tgid);
		return NF_ACCEPT;
	}

	calculate_upmt_key(&key, skb);
	/*if((key.daddr != 4294967295)&&(key.sport != 5060)) stampa = 1;
	if(stampa == 1){
		printk("***************\n");
		dmesg("upmt_tg - NEW CONNECTION -TGID %i [%s]", skb->tgid, appname);
		print_upmt_key_line(&key);
	}*/
	printk("upmt_tg - NEW CONNECTION -TGID %i [%s]\n", skb->tgid, appname);
	print_upmt_key_line(&key);


	// Second, check whether the destination address of the socket is in the app_flow list. If yes, divert the app to the specified tunnel


	// This is in order to exclude keep-alive packets
	/*if(strncasecmp(appname, JAVA_APPNAME, MAX_APPNAME_LENGTH) == 0){
		if((key.sport >= KA_MIN_PORT)&&(key.sport <= KA_MAX_PORT)){
			dmesg("xt_UPMT_ex - keep-alive packet ---> NF_ACCEPT");
			return NF_ACCEPT;
		}
	}*/

	write_lock_irqsave(&bul_mutex, flag);
	{
		struct app_flow_node *tmp;
		list_for_each_entry(tmp, &app_flow.list, list) {
			if (tmp->daddr == key.daddr) {
				dmesg("app [%s] opened a socket to the IP ", appname); print_ip_address(key.daddr); dmesg(" in the app_flow list, will go on tunnel %d", tmp->tid);
				paft_insert_by_tid(&key, tmp->tid, 1);
				goto out;
			}
		}
	}
	
	// At last, check if the app is in the apps list. If yes, divert it to its tunnel, otherwise divert it to the default tunnel.
	// Moreover, notify the appmon of the new app.
	{
		struct app_node *tmp;
		notif_data.key = key;
		strncpy(notif_data.appname, appname, MAX_APPNAME_LENGTH);
		notif_data.tid = default_tid;
		list_for_each_entry(tmp, &apps.list, list) {
			//print_app_node(tmp, &key, app);
			if(tmp->vipa == 0){
				if (strncasecmp(appname, tmp->appname, MAX_APPNAME_LENGTH) == 0) {
					notif_data.tid = tmp->tid;
					if(stampa == 1) dmesg("app [%s] found in the apps list with tid %d", appname, tmp->tid);
					break;
				}
			}
			else{	//Fabio Patriarca RME destination VIPA
				if ((strncasecmp(appname, tmp->appname, MAX_APPNAME_LENGTH) == 0)&&(tmp->vipa == key.daddr)) {
					notif_data.tid = tmp->tid;
					if(stampa == 1) dmesg("app [%s] ---> tid: %d", appname, tmp->tid);
					break;
				}
				else notif_data.tid = -1;
			}

		}

		if /*(notif_data.tid != 0) &&*/ (notif_data.tid != -1) {
			res = paft_insert_by_tid(&notif_data.key, notif_data.tid, 1);
			if(stampa == 1) dmesg("paft_insert_by_tid(key, tid=%d, static=1) returned %d", notif_data.tid, res);
		} else {
			//res = paft_delete(&notif_data.key);
			//dmesg("paft_delete(key) returned %d", res);
		}
		if(stampa == 1) printk("***************\n");
		write_unlock_irqrestore(&bul_mutex, flag);
		send_conn_notif(&notif_data);
	}
	return NF_ACCEPT;

out:
	write_unlock_irqrestore(&bul_mutex, flag);
	return NF_ACCEPT;
}

/*struct app_node {
	struct list_head list;
	char appname[MAX_APPNAME_LENGTH];
	unsigned int tid;
	unsigned int vipa; //Fabio Patriarca
};*/

#if 0
static void __snat(struct sk_buff *skb, struct net_device *dev) {	
	struct iphdr * ip = ip_hdr(skb);
	__u8 * l4hdr = skb->data + (ip->ihl*4);
	struct in_device *indev;
	unsigned short l4len = ntohs(ip->tot_len) - (ip->ihl*4);
	__sum16 *l4csum;
	__wsum csum;

	//SNAT with the first primary address...
	rcu_read_lock();
	indev = __in_dev_get_rcu(dev);
	for_primary_ifa(indev){ 
		ip->saddr = ifa->ifa_address;
		break;
	}endfor_ifa(indev);
	rcu_read_unlock();

	ip->check = 0;
	ip->check = ip_fast_csum((unsigned char *)ip, ip->ihl);

	switch(ip->protocol){
		case IPPROTO_UDP:
			l4csum = &((struct udphdr *)l4hdr)->check; 
			break;
		case IPPROTO_TCP:
			l4csum = &((struct tcphdr *)l4hdr)->check;
			break;
		case IPPROTO_ICMP:
			//do nothing.. netfilter will masquerade the ICMP packet
		default:
			goto csum_done;
	}

	*l4csum = 0;
	csum = csum_partial(l4hdr, l4len, 0);
	*l4csum = csum_tcpudp_magic(ip->saddr, ip->daddr, l4len, ip->protocol, csum);
csum_done:
	return;
}
#endif

static void __reroute_and_snat(struct sk_buff *skb, const struct net_device *odev) {
	struct iphdr * ip = ip_hdr(skb);
	struct sock tmpsk;
	struct rtable *rt = NULL;
	struct flowi4 fl4;

	tmpsk.sk_mark=NO_UPMT_MARK;

	// since 2.6.39 ip_route_output_key flowi attribute has been replaced with flowi4, rtable no longer has rt_src
	// attribute, ip_route_output_key function now returns an rtable and should be replaced with ip_route_output_ports (Sander)

	rt = ip_route_output_ports(dev_net(odev), &fl4, &tmpsk, ip->daddr, 0, 0, 0, 0, 0, 0);

	if (!rt) {
		return;
	}

	// since 2.6.36 rtable 'u' attribute has been replaced with 'dst' (Sander)
	if (rt->dst.dev == odev) {
		ip_rt_put(rt);
		return;
	}
	//__snat(skb, rt->u.dst.dev);

	skb_dst_drop(skb);

	// since 2.6.36 rtable 'u' attribute has been replaced with 'dst' (Sander)
	skb_dst_set(skb, &rt->dst);
}

int __is_dns(struct sk_buff *skb){
	struct iphdr *ip = ip_hdr(skb);
	struct udphdr * udp = (struct udphdr *)(skb->data + (ip->ihl*4));

	return ((ip->protocol == IPPROTO_UDP) && (ntohs(udp->dest) == 53) ? 1 : 0);
}

int __is_icmp(struct sk_buff *skb){
	struct iphdr *ip = ip_hdr(skb);

	return ((ip->protocol == IPPROTO_ICMP) ? 1 : 0);
}


//static unsigned int upmt_prefilter(unsigned int hooknum,
//                  struct sk_buff *skb,
//                  const struct net_device *in,
//                  const struct net_device *out,
//                  int (*okfn)(struct sk_buff*)) {

// Replacing hook with handler (Sander)
static int upmt_prefilter(struct sk_buff *skb,
					struct net_device *in,
					struct packet_type *pt,
					struct net_device *out) {

	
	const char *appname;
	unsigned long flag;
	struct iphdr * ip = ip_hdr(skb);
	
	if (skb->pkt_type != PACKET_OUTGOING) {
		//dmesge("upmt_prefilter : An incoming packet received on upmt device handler");
		return NF_ACCEPT;
	}

	if (skb->tgid <= 0) return NF_ACCEPT;
//	if (out != upmt_dev) return NF_ACCEPT; (Sander)

	//exception for broadcast
	if (ipv4_is_lbcast(ip->daddr))
		goto reroute_and_return;

	//exception for multicast	
	if (ipv4_is_multicast(ip->daddr))
		goto reroute_and_return;

	if (__is_dns(skb) || __is_icmp(skb) ) {
		goto reroute_and_return;
	}
	#if 0
	//exception for "local" packets
	//XXX remove it. let's handle it with the routing table
	for_each_netdev(dev_net(out), d) {
		struct in_ifaddr *ifa;
		__u32 prefix;
		__u32 daddr_prefix;
		
		if (!strcmp(d->name, "upmt0")) continue;
		indev = in_dev_get(d);
		
		for (ifa = indev->ifa_list; ifa; ifa = ifa->ifa_next) {


			prefix = ifa->ifa_address & ifa->ifa_mask;
			daddr_prefix = ip->daddr & ifa->ifa_mask;
			
			if (prefix == daddr_prefix){
				//dmesg("prefix: %d.%d.%d.%d", NIPQUAD(prefix));
				goto reroute_and_return;
			}
		}
	}
	#endif
	if ((appname = pid_to_exe_name(skb->tgid)) == NULL) {
		dmesg("upmt_prefilter: error finding exe name for pid %d", skb->tgid);
		return NF_ACCEPT;
	}
	
	write_lock_irqsave(&bul_mutex, flag);
	{
		struct no_upmt_node *tmp;
		list_for_each_entry(tmp, &no_upmt.list, list) {
			if (strncasecmp(appname, tmp->appname, MAX_APPNAME_LENGTH) == 0) {
				dmesg("app [%s] found in no_upmt list", appname);
				goto unlock_reroute_and_return;
			}
		}
	}
	write_unlock_irqrestore(&bul_mutex, flag);

	return NF_ACCEPT;

unlock_reroute_and_return:
	write_unlock_irqrestore(&bul_mutex, flag);
reroute_and_return:
	//ignore errors.... upmt_tun_xmit() will take care of it...
	skb->mark = NO_UPMT_MARK; //with this no notification will be sent
	__reroute_and_snat(skb, out); 
	return NF_ACCEPT;
}

// (Sander)
//static struct nf_hook_ops nf_ops_prefilter = {
//	.hook      = upmt_prefilter,
//	.pf        = PF_INET,
//	.hooknum   = NF_INET_LOCAL_OUT,
//	.priority  = NF_IP_PRI_FIRST
//};

// (bonus)
int conntrack_event(unsigned int event, struct nf_ct_event *item){

	struct nf_conn *ct;
        struct nf_conntrack_tuple_hash hash;
        struct nf_conntrack_tuple *tuple;
	struct upmt_key packet, packet2;
	struct upmt_del_conn_data notif_data;

	//int res_paft1, res_paft2, BEFORE, AFTER;
	int res_paft1, BEFORE, AFTER;
	unsigned long flags;

	if(event & (1 << IPCT_DESTROY)){
                ct              = item->ct;
                hash            = ct->tuplehash[IP_CT_DIR_ORIGINAL];
                tuple           = &hash.tuple;

		if(ipv4_is_multicast(tuple->dst.u3.ip))
			return 0;


		//if(ipv4_is_lbcast(tuple->dst.u3.ip))
		//	return 0;

		if(tuple->dst.protonum == IPPROTO_ICMP)
			return 0;
		// (bonus)
		//if((tuple->dst.protonum == IPPROTO_UDP) && (ntohs(tuple->dst.u.all) == 53))
		//	return 0;
	
		packet.proto	= tuple->dst.protonum;
		packet.saddr	= tuple->src.u3.ip;
		packet.daddr	= tuple->dst.u3.ip;
		packet.sport	= ntohs(tuple->src.u.all);
		packet.dport	= ntohs(tuple->dst.u.all);

		packet2.proto	= tuple->dst.protonum;
		packet2.saddr	= tuple->dst.u3.ip;
		packet2.daddr	= tuple->src.u3.ip;
		packet2.sport	= ntohs(tuple->dst.u.all);
		packet2.dport	= ntohs(tuple->src.u.all);

		//if((packet.proto == IPPROTO_UDP) && ((packet.sport >= KA_MIN_PORT) && (packet.sport <= KA_MAX_PORT))) return 0;
		//if((packet2.proto == IPPROTO_UDP) && ((packet2.sport >= KA_MIN_PORT) && (packet2.sport <= KA_MAX_PORT))) return 0;

		if(((unsigned char *)&tuple->dst.u3.ip)[0] == 127 
		&& ((unsigned char *)&tuple->dst.u3.ip)[1] == 0 
		&& ((unsigned char *)&tuple->dst.u3.ip)[2] == 0 
		&& ((unsigned char *)&tuple->dst.u3.ip)[3] == 1)
			return 0;

                /*printk(KERN_INFO "\tDESTROY -> tuple %u %pI4:%hu -> %pI4:%hu\n", tuple->dst.protonum,
                                                                                  &tuple->src.u3.ip, ntohs(tuple->src.u.all),
                                                                                  &tuple->dst.u3.ip, ntohs(tuple->dst.u.all));*/
 	        notif_data.key		= packet;
 	        strncpy(notif_data.appname, "conn_delete", MAX_APPNAME_LENGTH);
 	        notif_data.tid		= -1;

		//check_context("conntrack_event");
		
		write_lock_irqsave(&bul_mutex, flags);
		BEFORE = paft_count();
		res_paft1	= paft_delete(&packet);
		//res_paft2	= paft_delete(&packet2);
		AFTER = paft_count();

		/*if((res_paft1 >= 0)||(res_paft2 >= 0)) printk("****************\nPAFT ROW DELETED\n");
		if(res_paft1 >= 0) print_upmt_key_line(&packet);
		if(res_paft2 >= 0) print_upmt_key_line(&packet2);
		if((res_paft1 >= 0)||(res_paft2 >= 0)) printk("PAFT ROW COUNT before/after: %d/%d\n", BEFORE, AFTER);
		if((res_paft1 >= 0)||(res_paft2 >= 0)) printk("****************\n");

		if((res_paft1 >= 0)||(res_paft2 >= 0)) send_delete_conn_notif(&notif_data);*/

		//printk("****************\nPAFT ROW DELETED\n");
		//printk("NORMAL - "); print_upmt_key_line(&packet);
		//printk("REVERS - "); print_upmt_key_line(&packet2);
		//printk("PAFT ROW COUNT before/after: %d/%d\n", BEFORE, AFTER);
		//printk("****************\n");

		send_delete_conn_notif(&notif_data);

		write_unlock_irqrestore(&bul_mutex, flags);
	}

	return 0;
}

// (bonus)
static struct nf_ct_event_notifier notifier = {
	.fcn	    = conntrack_event,
};

static struct xt_target upmt_tg_reg[] __read_mostly = {{
	.name       = "UPMT",
	.revision   = 0,
	.family     = NFPROTO_UNSPEC,
	.target     = upmt_tg,
	.me         = THIS_MODULE
}};

int xt_UPMT_ex_init(void) {
	// Generic Netlink family and commands registration

	// the netlink family name is now concatenated with the hostname of the namespace from where insmod is executed (Sander)
	sprintf(upmt_appmgr_gnl_family.name,"%s%s",UPMT_GNL_FAMILY_NAME,upmtns->uts_ns->name.nodename);

	// the family can't be too long (why 13? should be 16) (Sander)
	if (strlen(upmt_appmgr_gnl_family.name)>13){
		dmesg("upmt_genl_register - hostname too long - unable to register upmt_appmgr_gnl_family");
		return -1;
	}

	if (genl_register_family(&upmt_appmgr_gnl_family)) {
		dmesg(" upmt_genl_register - unable to register upmt_appmgr_gnl_family");
		goto fail_genl_register_family;
	}

	dmesg("upmt_appmgr_gnl_family registered : %s",upmt_appmgr_gnl_family.name);

	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd)) {
		dmesg(" upmt_genl_register - unable to register upmt_gnl_ops_app_cmd");
		goto fail_genl_register_ops_app_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_no_upmt_cmd)) {
		dmesg(" upmt_genl_register - unable to register upmt_gnl_ops_no_upmt_cmd");
		goto fail_genl_register_ops_no_upmt_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_flow_cmd)) {
		dmesg(" upmt_genl_register - unable to register upmt_gnl_ops_app_flow_cmd");
		goto fail_genl_register_ops_app_flow_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_associate_cmd)) {
		dmesg(" upmt_genl_register - unable to register upmt_gnl_ops_associate_cmd");
		goto fail_genl_register_ops_associate_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_dump_list_cmd)) {
		dmesg(" upmt_genl_register - unable to register upmt_gnl_ops_dump_list_cmd");
		goto fail_genl_register_ops_dump_list_cmd;
	}

	// Register the 'UPMT' xtables target
	if (xt_register_targets(upmt_tg_reg, ARRAY_SIZE(upmt_tg_reg))) {
		dmesg(" xt_register_targets - unable to register UPMT target");
		goto fail_xt_register_targets;
	}

	//(bonus)
	if(PAFT_DELETION == 1){
		need_conntrack();
		need_ipv4_conntrack();
		if(nf_conntrack_register_notifier(&init_net, &notifier)){
		        dmesg(" nf_conntrack_register_notifier - unable to register conntrack notifier");
			goto fail_register_conntrack_notifier;
		}
	}

	// Register pre-filter handler (Sander)
	out_pt.type = htons(ETH_P_IP);
	out_pt.func = upmt_prefilter;
	out_pt.dev = upmt_dev;
	dev_add_pack(&out_pt);

//	if (nf_register_hook(&nf_ops_prefilter)) {
//		dmesg(" prefilter_register_hook - unable to register prefilter hook");
//		goto fail_register_prefilter;
//	}

	// Initialize the lists
	INIT_LIST_HEAD(&(apps.list));
	INIT_LIST_HEAD(&(no_upmt.list));
	INIT_LIST_HEAD(&(app_flow.list));

	dmesg("upmt-conntracker xt_UPMT_ex - INIT COMPLETED");
	return 0;

// (Sander)
//fail_register_prefilter:
//	xt_unregister_targets(upmt_tg_reg, ARRAY_SIZE(upmt_tg_reg));

fail_register_conntrack_notifier:
	nf_conntrack_unregister_notifier(&init_net, &notifier);

fail_xt_register_targets:
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_dump_list_cmd);

fail_genl_register_ops_dump_list_cmd:
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_associate_cmd);

fail_genl_register_ops_associate_cmd:
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd);

fail_genl_register_ops_app_flow_cmd:
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_no_upmt_cmd);

fail_genl_register_ops_no_upmt_cmd:
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd);

fail_genl_register_ops_app_cmd:
	genl_unregister_family(&upmt_appmgr_gnl_family);

fail_genl_register_family:
	return -1;
}

void xt_UPMT_ex_exit(void) {
	struct list_head *pos, *q;
	
	// (Sander)
	//nf_unregister_hook(&nf_ops_prefilter);

	//(bonus)
	if(PAFT_DELETION == 1) nf_conntrack_unregister_notifier(&init_net, &notifier);

	dev_remove_pack(&out_pt);
	xt_unregister_targets(upmt_tg_reg, ARRAY_SIZE(upmt_tg_reg));
	
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_dump_list_cmd);
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_associate_cmd);
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd);
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_no_upmt_cmd);
	genl_unregister_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd);
	genl_unregister_family(&upmt_appmgr_gnl_family);
	
	{
		struct app_node *tmp;
		list_for_each_safe(pos, q, &(apps.list)){
			tmp = list_entry(pos, struct app_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	{
		struct no_upmt_node *tmp;
		list_for_each_safe(pos, q, &(no_upmt.list)){
			tmp = list_entry(pos, struct no_upmt_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	{
		struct app_flow_node *tmp;
		list_for_each_safe(pos, q, &(app_flow.list)){
			tmp = list_entry(pos, struct app_flow_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
		
	dmesg("upmt-conntracker xt_UPMT_ex - EXIT COMPLETED");
}

