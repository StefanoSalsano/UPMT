/*
 * upmt_user.c
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

// upmtconf -m an -M numero (-M mandatory)

/* foreach decapsulated packet do:
 * if (mode_an) setta il mark del skbuff a numero (skb->mark = markinfo->mark)
*/
#include "upmt_user.h"
#include "upmt_stamp.h"



#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <linux/netlink.h>
#include <linux/genetlink.h>

#include <errno.h>

/*JNI*/#ifdef CREATELIB
/*JNI*/#define printf(...) do{ sprintf(buffer,__VA_ARGS__); __len = strnlen(buffer, 10000); if (__len > 0) { aretlen = aretlen + __len; _ret = realloc(_ret, aretlen); strncat(_ret, buffer, __len);} } while(0)
/*JNI*/#define exit(x) do{} while(0)
/*JNI*/#endif

int 	nl_sd; /*the socket*/
int 	upmt_fam_id;
struct 	genl_msg req, ans;
struct 	nlattr *nl_attr[UPMT_A_MSG_MAX+1];
int first_print = 0;

void asd(int index){
	printf("\n ---> Punto %d\n", index);
	fflush(stdout);
}

/**********************************************/

static int create_nl_socket(const int groups){
	socklen_t addr_len;
	int fd;
	struct sockaddr_nl local;

	fd = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
	if (fd < 0){
		perror("Unable to create netlink socket");
		return -1;
	}

/* XXX MARCO: this seems to be useless, and sometimes it gives error...
	memset(&local, 0, sizeof(local));
	local.nl_family = AF_NETLINK;
	local.nl_groups = groups;
	local.nl_pid 	= getpid();

	if (bind(fd, (struct sockaddr *) &local, sizeof(local)) < 0){
		close(fd);
		perror("Unable to bind netlink socket");
		return -1;
	}
*/
	nl_sd = fd;

/*JNI*/#ifdef CREATELIB
/*JNI*/		socket_desc = fd;
/*JNI*/#endif

	return 1;
}

static int sendto_fd(int s, const char *buf, int bufLen){
	struct sockaddr_nl nladdr;
	int r;

	memset(&nladdr, 0, sizeof(nladdr));
	nladdr.nl_family = AF_NETLINK;

	while ((r = sendto(s, buf, bufLen, 0, (struct sockaddr *) &nladdr, sizeof(nladdr))) < bufLen){
		if (r > 0) {
			buf += r;
			bufLen -= r;
		} else if (errno != EAGAIN) return -1;
	}
	return 0;
}

static void set_nl_attr(struct nlattr *na, const unsigned int type, const void *data, const unsigned int len){
	int length = len + 2;
	na->nla_type = type;
	na->nla_len = length + NLA_HDRLEN; //message length
	memcpy(GENLMSG_NLA_DATA(na), data, length);
}

static int get_family_id(){
	struct nlattr *na;
	int id;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type	= GENL_ID_CTRL;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 0;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= CTRL_CMD_GETFAMILY;
	req.g.version 		= 0x1;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, CTRL_ATTR_FAMILY_NAME, UPMT_GNL_FAMILY_NAME, strlen(UPMT_GNL_FAMILY_NAME));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;

	receive_response();

	na = (struct nlattr *) GENLMSG_DATA(&ans);
	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	if (na->nla_type == CTRL_ATTR_FAMILY_ID) {
		id = *(__u16 *) GENLMSG_NLA_DATA(na);
	}

	upmt_fam_id = id;
	return 0;
}

static void reset_nl_attrs(void){
	int i;
	for(i=0; i<=UPMT_A_MSG_MAX; i++){
		nl_attr[i] = NULL;
	}
}

void parse_nl_attrs(){
	reset_nl_attrs();

	unsigned int n_attrs = 0;
	struct nlattr *na;
	unsigned int data_len = GENLMSG_DATALEN(&ans.n);

	na = (struct nlattr *) GENLMSG_DATA(&ans);
	nl_attr[na->nla_type] = na;
	n_attrs++;
	data_len = data_len - NLA_ALIGN(na->nla_len);

	while(data_len > 0){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		nl_attr[na->nla_type] = na;
		n_attrs++;
		data_len = data_len - NLA_ALIGN(na->nla_len);
	}
	if(n_attrs > UPMT_A_MSG_MAX) printf("\n parse_nl_attrs - too much attributes");
}

static void print_nl_attr(struct nlattr *na){
	if(na == NULL){
		printf("\nAttr NULL");
		return;
	}
	/*printf("\nAttr type: %u", na->nla_type);
	printf("\nAttr len: %u", na->nla_len);
	printf("\nContentLen: %d", GENLMSG_NLA_DATALEN(na));*/
	void *data = GENLMSG_NLA_DATA(na);
	if(na->nla_type == UPMT_A_MSG_TYPE){
		printf(" %s", (char *)data);
	}

	else if(na->nla_type == UPMT_A_PAFT_KEY) 	print_upmt_key((struct upmt_key *)data);
	else if(na->nla_type == UPMT_A_TUN_TID) 	printf("\n\t TID: %u", *((unsigned int *)data));
	else if(na->nla_type == UPMT_A_TUN_PARAM) 	print_upmt_tun_param((struct tun_param *)data);
	else if(na->nla_type == UPMT_A_TUN_LOCAL) 	print_upmt_tun_local((struct tun_local *)data);
	else if(na->nla_type == UPMT_A_UNSPEC) 		printf("\nContent: %s", (char *)data);
	else printf("\n Content - unknown format");
}

static void print_nl_attrs(void){
	int i;
	for(i=0; i<=UPMT_A_MSG_MAX; i++){
		if(nl_attr[i] != NULL) print_nl_attr(nl_attr[i]);
	}
}

static int isError(char *resp){
	if(
		(strcmp(resp, GET_NOT_FOUND_RESPONSE_MSG) == 0)
		||
		(strcmp(resp, SET_ERROR_RESPONSE_MSG) == 0)
		||
		(strcmp(resp, DEL_NOT_FOUND_RESPONSE_MSG) == 0)
		||
		(strcmp(resp, HAN_ERROR_RESPONSE_MSG) == 0)
	)
		return 1;
	else return 0;
}

static void * get_nl_data(const unsigned int type){
	if(nl_attr[type] == NULL) return NULL;
	void *data = GENLMSG_NLA_DATA(nl_attr[type]);
	return data;
}

static void print_table_tun_param(struct tun_param *tp, char *iname){
	char *separator = "\t\t";
	static int count = 0;

	if(count == 0){
		printf("\n");
		printf("TID");
		printf("%s", separator);
		printf("IFIN");
		printf("%s", separator);
		printf("LPORT");
		printf("%s", separator);
		printf("RADDR");
		printf("%s", separator);
		//printf("%s", separator);
		printf("RPORT");

		printf("%s", separator);
		printf("LCL_NAT");
		printf("%s", separator);
		printf("RMT_NAT");

		printf("%s", separator);
		printf("DEVICE");
	}
	count++;

	if(tp != NULL){
		printf("\n");
		printf("%d", tp->tid);
		printf("%s", separator);
		printf("%d", tp->tl.ifindex);
		printf("%s", separator);
		printf("%u", tp->tl.port);
		printf("%s", separator);
		print_ip_address(tp->tr.addr);
		printf("%s", separator);
		printf("%u", tp->tr.port);
	}

	if(iname != NULL){
		printf("%s", separator);
		printf("%s", iname);
	}

	if(tp != NULL){
		printf("%s", separator);
		print_ip_address(tp->in.local);
		printf("%s", separator);
		print_ip_address(tp->in.remote);
	}
}

static void print_table_rule(int *rid, struct upmt_key *key, int *tid, char staticrule){
	char *separator = "\t\t";
	static int count = 0;

	if(count == 0){
		printf("\n");
		printf("RID");
		printf("%s", separator);
		printf("PROTO");
		printf("%s", separator);
		printf("SADDR");
		printf("%s", separator);
		printf("SPORT");
		printf("%s", separator);
		printf("DADDR");
		printf("%s", separator);
		printf("DPORT");
		printf("%s", separator);
		printf("tunnel");
		printf("%s", separator);
		printf("static");
	}
	count++;

	if(rid != NULL){
		printf("\n");
		printf("%d", *rid);
		printf("%s", separator);
	}

	if(key != NULL){
		if(key->proto == 6) printf("tcp");
		else if(key->proto == 17) printf("udp");
		else printf("%u", key->proto);
		printf("%s", separator);
		print_ip_address(key->saddr);
		printf("%s", separator);
		printf("%u", key->sport);
		printf("%s", separator);
		print_ip_address(key->daddr);
		printf("%s", separator);
		printf("%u", key->dport);
		printf("%s", separator);
	}

	if(tid != NULL){
		printf("%d", *tid);
		printf("%s", separator);
	}
	
	if (staticrule != -1) {
		if (staticrule == 0) printf("no");
		else if (staticrule == 1) printf("yes");
		else printf("?");
	}
}

static void print_table_tsa(struct tun_local *tl){
	char *separator = "\t\t";
	static int count = 0;

	if(count == 0){
		printf("\n");
		printf("IFINDEX");
		printf("%s", separator);
		printf("RPORT");
	}
	count++;

	printf("\n");
	//print_ip_address(tr->addr);
	printf("%d", tl->ifindex);
	printf("%s", separator);
	printf("%u", tl->port);
}

static void print_table_mark_dev(struct mark_dev *md){
	char *separator = "\t\t";
	static int count = 0;

	if(count == 0){
		printf("\n");
		printf("DEVICE");
		printf("%s", separator);
		printf("MARK");
	}
	count++;

	printf("\n");
	printf("%s", md->iname);
	printf("%s", separator);
	printf("%u", md->mark);
}

static void print_vpn_rule(unsigned int *ip, int *tid){
	char *separator = "\t\t";
	static int count = 0;

	if(count == 0){
		printf("\n");
		printf("ADDRESS");
		printf("%s", separator);
		printf("tunnel");
	}
	count++;

	if(ip != NULL){
		printf("\n");
		print_ip_address(*ip);
		printf("%s", separator);
	}

	if(tid != NULL){
		printf("%d", *tid);
	}
}

/**********************************************/

int upmt_genl_client_init(){
	create_nl_socket(0);
	get_family_id();
}

int send_echo_command(){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= UPMT_C_ECHO;

	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_tunt_command(const int command, const char *iface, const struct tun_param *tp){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= command;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if(iface != NULL){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_DEV, iface, strlen(iface));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}

	/***************************/

	if(tp != NULL){
	//if(1){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_PARAM, tp, sizeof(struct tun_param));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_paft_command(const int command, const int tid, const int rid, const struct upmt_key *key, char staticrule){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= command;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	//if(tid > 0){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_TID, &tid, sizeof(tid));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	//}

	/***************************/

	//if(rid > 0){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_PAFT_RID, &rid, sizeof(rid));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	//}

	/***************************/

	if(key != NULL){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_PAFT_KEY, key, sizeof(struct upmt_key));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}
	
	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_PAFT_STATIC, &staticrule, sizeof(char));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_tsa_command(const int command, const struct tun_local *tl, char *iface){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= command;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if(iface != NULL){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_DEV, iface, strlen(iface));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}

	/***************************/

	if(tl != NULL){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_LOCAL, tl, sizeof(struct tun_local));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_handover_command(int rid, int tid){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= UPMT_C_HANDOVER;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_PAFT_RID, &rid, sizeof(rid));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_TUN_TID, &tid, sizeof(tid));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_an_command(int mark){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= UPMT_C_AN;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_AN_MARK, &mark, sizeof(mark));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_verbose_command(int verbose){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= UPMT_C_VERBOSE;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_VERBOSE, &verbose, sizeof(verbose));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_flush_command(char *table){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= UPMT_C_FLUSH;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_MSG_MESSAGE, table, strlen(table));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_mdl_command(const int command, char *iface){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= command;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if(iface != NULL){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		set_nl_attr(na, UPMT_A_TUN_DEV, iface, strlen(iface));
		req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);
	}

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int send_pdft_command(const int command, const unsigned int address, const int tid){
	struct nlattr *na;
	struct sockaddr_nl nladdr;
	char *message;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_fam_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= 60;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= command;

	/***************************/
	message = REQUEST_MSG;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, UPMT_A_MSG_TYPE, message, strlen(message));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_IP_ADDR, &address, sizeof(address));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
	set_nl_attr(na, UPMT_A_TUN_TID, &tid, sizeof(tid));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	/***************************/

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;
}

int receive_response() {
	first_print = 1;
	
	while (do_receive_response());
	return 0;
}

int do_receive_response(){
	memset(ans.buf, 0, MAX_BUF_LEN);
	int rep_len = recv(nl_sd, &ans, sizeof(ans), 0);

	if (ans.n.nlmsg_type == NLMSG_ERROR) { /* error */
		printf("\nUSER SPACE - error received NACK - leaving \n");
		exit(-1);
		return -1;
	}
	if (rep_len < 0) {
		printf("USER SPACE - error receiving reply message via Netlink \n");
		exit(-1);
		return -1;
	}
	if (!NLMSG_OK((&ans.n), rep_len)) {
		printf("USER SPACE - invalid reply message received via Netlink\n");
		exit(-1);
		return -1;
	}

	//if(ans.g.cmd == CTRL_CMD_GETFAMILY) return 0;
	if(ans.g.cmd == 1) return 0;

	if ( (ans.g.cmd == UPMT_C_LST_TUNNEL) || (ans.g.cmd == UPMT_C_LST_RULE) || (ans.g.cmd == UPMT_C_LST_TSA) || (ans.g.cmd == UPMT_C_LST_PDFT)) {
		if (first_print == 1) {
			printf("\n ---> Response from kernel:");
			first_print = 0;
		}
		return parse_lst_nl_attrs();
	}
	else {
		parse_nl_attrs();
		printResponse(ans.g.cmd);
	}
	return 0;
}

// returns 0 if last msg received, 1 otherwise
int parse_lst_nl_attrs(){
	reset_nl_attrs();

	unsigned int n_attrs = 0;
	struct nlattr *na;
	unsigned int data_len = GENLMSG_DATALEN(&ans.n);

	na = (struct nlattr *) GENLMSG_DATA(&ans);
	//print_nl_attr(na);
	n_attrs++;
	data_len = data_len - NLA_ALIGN(na->nla_len);

	void *data;
	while(data_len > 0){

		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		data = GENLMSG_NLA_DATA(na);
		if(na->nla_type == UPMT_A_MSG_MESSAGE) 	printf(" %s", (char *) data);

		if(na->nla_type == UPMT_A_TUN_PARAM) 	print_table_tun_param((struct tun_param *)data, NULL);
		if(na->nla_type == UPMT_A_TUN_DEV) 		print_table_tun_param(NULL, (char *)data);

		if(na->nla_type == UPMT_A_PAFT_RID) 	print_table_rule((int *)data, NULL, NULL, -1);
		if(na->nla_type == UPMT_A_PAFT_KEY) 	print_table_rule(NULL, (struct upmt_key *)data, NULL, -1);
		if(na->nla_type == UPMT_A_PAFT_STATIC)	print_table_rule(NULL, NULL, NULL, *(char*) data);

		if(na->nla_type == UPMT_A_TUN_TID){
			if(ans.g.cmd == UPMT_C_LST_RULE) 	print_table_rule(NULL, NULL, (int *)data, -1);
			if(ans.g.cmd == UPMT_C_LST_PDFT) 	print_vpn_rule(NULL, (int *)data);
		}
		if(na->nla_type == UPMT_A_IP_ADDR)	 	print_vpn_rule((unsigned int *)data, NULL);

		if(na->nla_type == UPMT_A_TUN_LOCAL) 	print_table_tsa((struct tun_local *)data);
		if(na->nla_type == UPMT_A_MARK_DEV) 	print_table_mark_dev((struct mark_dev *)data);
		
		if(na->nla_type == UPMT_A_LAST_LST_MSG)	return 0;

		n_attrs++;
		data_len = data_len - NLA_ALIGN(na->nla_len);
	}
	//if(n_attrs > UPMT_A_MSG_MAX) printf("\n parse_nl_attrs - too much attributes");
	return 0;
}


void printResponse(const unsigned int type){
	char *response 			= (char *) 				get_nl_data(UPMT_A_MSG_TYPE);
	char *err_message		= (char *) 				get_nl_data(UPMT_A_MSG_MESSAGE);
	char *iname 			= (char *) 				get_nl_data(UPMT_A_TUN_DEV);
	struct upmt_key *key 	= (struct upmt_key *) 	get_nl_data(UPMT_A_PAFT_KEY);
	struct tun_param *tp 	= (struct tun_param *) 	get_nl_data(UPMT_A_TUN_PARAM);
	unsigned int *mark		= (int *) 				get_nl_data(UPMT_A_AN_MARK);
	struct mark_dev *md 	= (struct mark_dev *) 	get_nl_data(UPMT_A_MARK_DEV);
	char *staticrule 		= (char *) 				get_nl_data(UPMT_A_PAFT_STATIC);

	printf("\n ---> Response from kernel:");
	if(err_message) printf(" %s", err_message);

	char *separator = "\n --------------------------------------";

	if(type == UPMT_C_SET_MDL){
		if(md == NULL) return;
		printf("%s", separator);
		printf("\n   Device:	%s", md->iname);
		printf("\n     Mark:	%u", md->mark);
		printf("%s", separator);
	}

	if((type == UPMT_C_SET_TUNNEL)||(type == UPMT_C_GET_TUNNEL)){
		if(tp == NULL) return;
		printf("%s", separator);
		printf("\n - Tunnel parameters - ");
		printf("\n   Device: %s", iname);
		print_upmt_tun_param(tp);
		printf("%s", separator);
	}

	if((type == UPMT_C_SET_RULE)||(type == UPMT_C_GET_RULE)){
		if(key == NULL) return;
		printf("%s", separator);
		printf("\n - Rule parameters - ");
		print_upmt_key(key);
		printf("\n\tstatic:  %s", ((*staticrule) == 1) ? "yes" : "no");

		if(tp == NULL) return;
		printf("\n - Tunnel parameters - ");
		printf("\n   Device: %s", iname);
		print_upmt_tun_param(tp);
		printf("%s", separator);
	}
}
