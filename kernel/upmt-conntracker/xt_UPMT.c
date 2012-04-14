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

#include "upmt.h"
#include "upmt_conntracker_appmon.h"
#include "compat_xtables.h"
#include "upmt_paft.h"

#include <linux/pid.h>
#include <linux/sched.h>
#include <linux/mm.h>
#include <linux/highmem.h>
#include <linux/pagemap.h>
#include <linux/list.h>
#include <net/netlink.h>
#include <net/route.h>

#ifdef ANDROID
//buffer for pid_to_exe_name
char cmdline_buffer[PAGE_SIZE];
#endif


struct app_node {
	struct list_head list;
	char appname[MAX_APPNAME_LENGTH];
	unsigned int tid;
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
static unsigned int appmon_pid = -1;

static int default_tid = -1;

// sequence number for connection notifications messages
static unsigned int conn_notif_seqno = 0;

struct genl_family upmt_appmgr_gnl_family = {
	.id = GENL_ID_GENERATE,
	.hdrsize = 0,
	.name = UPMT_GNL_FAMILY_NAME,
	.version = UPMT_GNL_FAMILY_VERSION,
	.maxattr = UPMT_APPMON_A_MAX,
};

struct nla_policy upmt_appmgr_genl_policy[UPMT_APPMON_A_MAX + 1] = {
	[UPMT_APPMON_A_UNSPEC]			= { .type = NLA_BINARY },
	[UPMT_APPMON_A_ASSOCIATE]		= { .type = NLA_U32 },
	[UPMT_APPMON_A_APP]				= { .type = NLA_BINARY },
	[UPMT_APPMON_A_NO_UPMT]			= { .type = NLA_BINARY },
	[UPMT_APPMON_A_APP_FLOW]		= { .type = NLA_BINARY },
	[UPMT_APPMON_A_NEW_CONN]		= { .type = NLA_BINARY },
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

extern int paft_insert_by_tid(const struct upmt_key *key, int tid, char staticrule);
extern int paft_delete(const struct upmt_key *k);

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
		printk("upmt_prefilter: BUG nsproxy == NULL\n");
		return NULL;
	}

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
	if (!(spid = find_get_pid(pid))) return NULL;
	if (!(task = pid_task(spid, PIDTYPE_PID))) return NULL;
	if (!(mm = get_task_mm(task))) return NULL; 
	if (!(exe_file = mm->exe_file)) return NULL;
	if (!(dentry = exe_file->f_path.dentry)) return NULL;
	fname = dentry->d_name;
	//XXX wr should call mm_put() but it sleeps....
	//we'll get rid of this problem with the new approach
	return fname.name;
	#endif
}

void print_ip_address(unsigned int addr){
	printk("%d.", ((unsigned char *)&addr)[0]);
	printk("%d.", ((unsigned char *)&addr)[1]);
	printk("%d.", ((unsigned char *)&addr)[2]);
	printk("%d",  ((unsigned char *)&addr)[3]);
}

void print_upmt_key(const struct upmt_key *k){
	printk("\n\t proto:\t %d", k->proto);
	printk("\n\t saddr:\t "); print_ip_address(k->saddr);
	printk("\n\t daddr:\t "); print_ip_address(k->daddr);
	printk("\n\t sport:\t %d", k->sport);
	printk("\n\t dport:\t %d", k->dport);
}
// #####################################################################

int app_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_app_msg* cmd;
	struct app_node *tmp;
	struct list_head *pos, *q;

	cmd = (struct upmt_app_msg*) extract_nl_attr(info, UPMT_APPMON_A_APP);

	if (cmd->command == CMD_ADD) {
		//printk("app_cmd_handler: gestione comando CMD_ADD\n");
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				list_del(pos);
				kfree(tmp);
				break;
			}
		}
		
		tmp = (struct app_node *) kmalloc(sizeof(struct app_node), GFP_KERNEL);
		strncpy(tmp->appname, cmd->appname, MAX_APPNAME_LENGTH);
		tmp->tid = cmd->tid;
		list_add_tail(&(tmp->list), &(apps.list));
		//printk("added <%s,%d> to app list\n", tmp->appname, tmp->tid);
	}
	else if (cmd->command == CMD_RM) {
		//printk("app_cmd_handler: gestione comando CMD_RM\n");
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		//printk("app_cmd_handler: gestione comando CMD_FLUSH_LIST\n");
		list_for_each_safe(pos, q, &(apps.list)){
			tmp = list_entry(pos, struct app_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	else if (cmd->command == CMD_SET_DEFAULT_TID) {
		//printk("app_cmd_handler: gestione comando CMD_SET_DEFAULT_TID\n");
		default_tid = cmd->tid;
	}
	return 0;
}

int no_upmt_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_no_upmt_msg* cmd;
	struct no_upmt_node *tmp;
	struct list_head *pos, *q;
	int found;

	cmd = (struct upmt_no_upmt_msg*) extract_nl_attr(info, UPMT_APPMON_A_NO_UPMT);

	if (cmd->command == CMD_ADD) {
		//printk("no_upmt_cmd_handler: gestione comando CMD_ADD\n");
		found = 0;
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				found = 1;
				break;
			}
		}
		if (found == 0) {
			tmp = (struct no_upmt_node *) kmalloc(sizeof(struct no_upmt_node), GFP_KERNEL);
			strncpy(tmp->appname, cmd->appname, MAX_APPNAME_LENGTH);
			list_add_tail(&(tmp->list), &(no_upmt.list));
			//printk("added <%s> to no_upmt list\n", tmp->appname);
		}
		else;
			//printk("app %s already in the no_upmt list\n", cmd->appname);
	}
	else if (cmd->command == CMD_RM) {
		//printk("no_upmt_cmd_handler: gestione comando CMD_RM\n");
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			if (strcmp(cmd->appname, tmp->appname) == 0) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		//printk("no_upmt_cmd_handler: gestione comando CMD_FLUSH_LIST\n");
		list_for_each_safe(pos, q, &(no_upmt.list)){
			tmp = list_entry(pos, struct no_upmt_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	return 0;
}

int app_flow_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct upmt_app_flow_msg *cmd;
	struct app_flow_node *tmp;
	struct list_head *pos, *q;
	int found;

	cmd = (struct upmt_app_flow_msg*) extract_nl_attr(info, UPMT_APPMON_A_APP_FLOW);

	if (cmd->command == CMD_ADD) {
		//printk("app_flow_cmd_handler: gestione comando CMD_ADD\n");
		found = 0;
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			if ((cmd->daddr == tmp->daddr) && (cmd->tid == tmp->tid)) {
				found = 1;
				break;
			}
		}
		if (found == 0) {
			tmp = (struct app_flow_node *) kmalloc(sizeof(struct app_flow_node), GFP_KERNEL);
			tmp->daddr = cmd->daddr;
			tmp->tid = cmd->tid;
			list_add_tail(&(tmp->list), &(app_flow.list));
			//printk("added <"); print_ip_address(tmp->daddr); printk(", %d> to app_flow list\n", tmp->tid);
		}
		else {
			//printk("<"); print_ip_address(cmd->daddr); printk(", %d> already in the app_flow list\n", cmd->tid);
		}
	}
	else if (cmd->command == CMD_RM) {
		//printk("app_flow_cmd_handler: gestione comando CMD_RM\n");
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			if ((cmd->daddr == tmp->daddr) && (cmd->tid == tmp->tid)) {
				list_del(pos);
				kfree(tmp);
			}
		}
	}
	else if (cmd->command == CMD_FLUSH_LIST) {
		//printk("app_flow_cmd_handler: gestione comando CMD_FLUSH_LIST\n");
		list_for_each_safe(pos, q, &(app_flow.list)){
			tmp = list_entry(pos, struct app_flow_node, list);
			list_del(pos);
			kfree(tmp);
		}
	}
	return 0;
}

int associate_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	int magic = *(int*) extract_nl_attr(info, UPMT_APPMON_A_ASSOCIATE);
	
	appmon_pid = info->snd_pid; //save pid of application monitor
	printk("UPMT-AppMon with PID %d associated with magic number %d\n", appmon_pid, magic);
	return 0;
}

int dump_list_cmd_handler(struct sk_buff *skb, struct genl_info *info) {
	struct list_head *pos, *q;

	int list_id = *(int*) extract_nl_attr(info, UPMT_APPMON_A_DUMP_LIST_ID);

	if (list_id == MSG_TYPE_APP) {
		struct app_node *tmp;
		printk("--- DUMP apps LIST ---\n");
		printk("--- appname\t\ttid ---\n");
		list_for_each_safe(pos, q, &(apps.list)) {
			tmp = list_entry(pos, struct app_node, list);
			printk("%s\t%d\n", tmp->appname, tmp->tid);
		}
		printk("--- END DUMP apps LIST ---\n");
	}
	else if (list_id == MSG_TYPE_NO_UPMT) {
		struct no_upmt_node *tmp;
		printk("--- DUMP no_upmt LIST ---\n");
		printk("--- appname ---\n");
		list_for_each_safe(pos, q, &(no_upmt.list)) {
			tmp = list_entry(pos, struct no_upmt_node, list);
			printk("%s\n", tmp->appname);
		}
		printk("--- END DUMP no_upmt LIST ---\n");
	}
	else if (list_id == MSG_TYPE_APP_FLOW) {
		struct app_flow_node *tmp;
		printk("--- DUMP app_flow LIST ---\n");
		printk("--- daddr\t\ttid ---\n");
		list_for_each_safe(pos, q, &(app_flow.list)) {
			tmp = list_entry(pos, struct app_flow_node, list);
			print_ip_address(tmp->daddr); printk("\t%d\n", tmp->tid);
		}
		printk("--- END DUMP app_flow LIST ---\n");
	}
	return 0;
}

int send_conn_notif(struct upmt_new_conn_data *data) {
	struct sk_buff *skb;
	void *skb_head;

	skb = genlmsg_new(NLMSG_GOODSIZE, GFP_ATOMIC);
	if (skb == NULL){
		printk("\n\t send_response_message - unable to allocate skb");
		return -1;
	}

	skb_head = genlmsg_put(skb, appmon_pid, conn_notif_seqno++, &upmt_appmgr_gnl_family, 0, UPMT_APPMON_C_NEW_CONN);
	if (skb_head == NULL) {
		printk("\n\t send_response_message - unable to allocate skb_head");
		return -ENOMEM;
	}

	if(nla_put(skb, UPMT_APPMON_A_NEW_CONN, sizeof(struct upmt_new_conn_data), data) != 0){
		printk("\n\t send_response_message - unable to put UPMT_A_MSG_TYPE attribute");
		return -1;
	}

	genlmsg_end(skb, skb_head);

#if LINUX_VERSION_CODE < UPMT_LINUX_VERSION_CODE
	if(genlmsg_unicast(skb, appmon_pid ) != 0){
#else
	if(genlmsg_unicast(&init_net, skb, appmon_pid ) != 0){
#endif
		printk("\n\t send_response_message - unable to send response");
		return -1;
	}
	return 0;
}

unsigned int upmt_tg(struct sk_buff **pskb, const struct xt_action_param * tp) {
	struct sk_buff *skb = *pskb;
	struct upmt_key key;
	struct upmt_new_conn_data notif_data;
	const char *appname;
	
	int res;
	
	if (skb->mark == NO_UPMT_MARK)
		return NF_ACCEPT;

	if (default_tid == -1) return NF_ACCEPT; // do nothing until we receive a valid default tunnel
	if (skb->tgid <= 0) return NF_ACCEPT;
			
	//XXX for UDP packets, if skb->nfct == 2 and STATE == NEW, this is a packet for a connection already established but not yet replayed.
	//in this case we don't want the notification, even though for NETFILTER the state of this packet is NEW
	if (skb->nfct->use.counter == 2)	
		return NF_ACCEPT;

	if ((appname = pid_to_exe_name(skb->tgid)) == NULL) {
		printk("upmt_tg: error finding exe name for pid %d\n", skb->tgid);
		return NF_ACCEPT;
	}
	
	//printk("UPMT NETFILTER MODULE - upmt_tg - NEW CONNECTION -TGID %i [%s]\n", skb->tgid, appname);

	// Second, check whether the destination address of the socket is in the app_flow list. If yes, divert the app to the specified tunnel
	calculate_upmt_key(&key, skb);
	{
		struct app_flow_node *tmp;
		list_for_each_entry(tmp, &app_flow.list, list) {
			if (tmp->daddr == key.daddr) {
				//printk("app [%s] opened a socket to the IP ", appname); print_ip_address(key.daddr); printk(" in the app_flow list, will go on tunnel %d\n", tmp->tid);
				paft_insert_by_tid(&key, tmp->tid, 1);
				return NF_ACCEPT;
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
			if (strncasecmp(appname, tmp->appname, MAX_APPNAME_LENGTH) == 0) {
				notif_data.tid = tmp->tid;
				//printk("app [%s] found in the apps list with tid %d\n", appname, tmp->tid);
				break;
			}
		}
		send_conn_notif(&notif_data);
		if /*(notif_data.tid != 0) &&*/ (notif_data.tid != -1) {
			res = paft_insert_by_tid(&notif_data.key, notif_data.tid, 1);
			//printk("paft_insert_by_tid(key, tid=%d, static=1) returned %d\n", notif_data.tid, res);
		} else {
			res = paft_delete(&notif_data.key);
			//printk("paft_delete(key) returned %d\n", res);
		}
	}

	return NF_ACCEPT;
}

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
	struct rtable *rt;
	struct flowi fl = {
		.mark = NO_UPMT_MARK,
		.nl_u = {
			.ip4_u = {
				.daddr 	= ip->daddr,
			}
		},
	};

	if (ip_route_output_key(dev_net(odev), &rt, &fl))
		return;
	
	if (rt->u.dst.dev == odev) {
		ip_rt_put(rt);
		return;
	}
	//__snat(skb, rt->u.dst.dev);
	
	skb_dst_drop(skb);
	skb_dst_set(skb, &rt->u.dst);
}

static unsigned int upmt_prefilter(unsigned int hooknum,
                  struct sk_buff *skb,
                  const struct net_device *in,
                  const struct net_device *out,
                  int (*okfn)(struct sk_buff*)) {
	
	const char *appname;
	struct iphdr * ip = ip_hdr(skb);
	
	if (skb->tgid <= 0) return NF_ACCEPT;
	if (out != upmt_dev) return NF_ACCEPT;

	//exception for broadcast
	if (ipv4_is_lbcast(ip->daddr))
		goto reroute_and_return;

	//exception for multicast	
	if (ipv4_is_multicast(ip->daddr))
		goto reroute_and_return;

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
				//printk("prefix: %d.%d.%d.%d", NIPQUAD(prefix));
				goto reroute_and_return;
			}
		}
	}
	#endif
	if ((appname = pid_to_exe_name(skb->tgid)) == NULL) {
		//printk("upmt_prefilter: error finding exe name for pid %d\n", skb->tgid);
		return NF_ACCEPT;
	}
	
	{
		struct no_upmt_node *tmp;
		list_for_each_entry(tmp, &no_upmt.list, list) {
			if (strncasecmp(appname, tmp->appname, MAX_APPNAME_LENGTH) == 0) {
				//printk("app [%s] found in no_upmt list\n", appname);
				goto reroute_and_return;
			}
		}
	}

	return NF_ACCEPT;

reroute_and_return:
	//ignore errors.... upmt_tun_xmit() will take care of it...
	__reroute_and_snat(skb, out); 
	return NF_ACCEPT;
}

static struct nf_hook_ops nf_ops_prefilter = {
	.hook      = upmt_prefilter,
	.pf        = PF_INET,
	.hooknum   = NF_INET_LOCAL_OUT,
	.priority  = NF_IP_PRI_FIRST
};

static struct xt_target upmt_tg_reg[] __read_mostly = {{
	.name       = "UPMT",
	.revision   = 0,
	.family     = NFPROTO_UNSPEC,
	.target     = upmt_tg,
	.me         = THIS_MODULE
}};

static int __init upmt_init(void) {
	// Generic Netlink family and commands registration
	if (genl_register_family(&upmt_appmgr_gnl_family)) {
		printk("\n\t upmt_genl_register - unable to register upmt_appmgr_gnl_family");
		goto fail_genl_register_family;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_cmd)) {
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_app_cmd");
		goto fail_genl_register_ops_app_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_no_upmt_cmd)) {
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_no_upmt_cmd");
		goto fail_genl_register_ops_no_upmt_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_app_flow_cmd)) {
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_app_flow_cmd");
		goto fail_genl_register_ops_app_flow_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_associate_cmd)) {
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_associate_cmd");
		goto fail_genl_register_ops_associate_cmd;
	}
	if (genl_register_ops(&upmt_appmgr_gnl_family, &upmt_gnl_ops_dump_list_cmd)) {
		printk("\n\t upmt_genl_register - unable to register upmt_gnl_ops_dump_list_cmd");
		goto fail_genl_register_ops_dump_list_cmd;
	}

	// Register the 'UPMT' xtables target
	if (xt_register_targets(upmt_tg_reg, ARRAY_SIZE(upmt_tg_reg))) {
		printk("\n\t xt_register_targets - unable to register UPMT target");
		goto fail_xt_register_targets;
	}

	// Register pre-filter hook	
	if (nf_register_hook(&nf_ops_prefilter)) {
		printk("\n\t prefilter_register_hook - unable to register prefilter hook");
		goto fail_register_prefilter;
	}

	// Initialize the lists
	INIT_LIST_HEAD(&(apps.list));
	INIT_LIST_HEAD(&(no_upmt.list));
	INIT_LIST_HEAD(&(app_flow.list));

	printk("upmt-conntracker xt_UPMT - INIT COMPLETED\n");
	return 0;

fail_register_prefilter:
	xt_unregister_targets(upmt_tg_reg, ARRAY_SIZE(upmt_tg_reg));

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

static void __exit upmt_exit(void) {
	struct list_head *pos, *q;
	
	nf_unregister_hook(&nf_ops_prefilter);
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
		
	printk("upmt-conntracker xt_UPMT - EXIT COMPLETED\n");
}

module_init(upmt_init);
module_exit(upmt_exit);
MODULE_AUTHOR("Alessio Bianchi");
MODULE_LICENSE("GPL");
