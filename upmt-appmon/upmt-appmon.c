#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <errno.h>
#include <linux/netlink.h>
#include <linux/genetlink.h>
#include <sys/utsname.h>
#include <poll.h>
#include <netdb.h>
#include <sys/un.h>

#include "include/upmt.h"
#include "include/upmt_conntracker_appmon.h"

#include "cJSON.h"

#define SOCKETS_TO_POLL 2

#define LOCALHOST_ADDR "127.0.0.1"

#define MAX_BUF_LEN 4096

int appmgr_socket_port_tomgr;
int appmgr_socket_port_frommgr;

int seqno = 0;

struct genl_msg {
	struct nlmsghdr n;
	struct genlmsghdr g;
	char buf[MAX_BUF_LEN];
};

#define GENLMSG_DATA(glh) 			((void *)(NLMSG_DATA(glh) + GENL_HDRLEN))
#define GENLMSG_DATALEN(glh) 		(NLMSG_PAYLOAD(glh, 0) - GENL_HDRLEN)
#define GENLMSG_NLA_NEXT(na) 		(((void *)(na)) + NLA_ALIGN(na->nla_len))
#define GENLMSG_NLA_DATA(na) 		((void *)((char*)(na) + NLA_HDRLEN))
#define GENLMSG_NLA_DATALEN(na) 	(na->nla_len - NLA_HDRLEN - 1)

int nl_sd;						// the socket used to listen from netlink
int frommgr_sd;					// the socket used to listen from application manager
int tomgr_sd;					// the socket used to send to the application manager
struct sockaddr_in tomgr_addr;	// used to send to the application manager

struct 	genl_msg req;
int upmt_family_id;

struct 	nlattr *nl_attr[UPMT_APPMON_A_MAX+1];

int create_nl_socket() {
	int fd;

	fd = socket(AF_NETLINK, SOCK_RAW, NETLINK_GENERIC);
	if (fd < 0) {
		perror("Unable to create netlink socket");
		return -1;
	}

	#if 0
	memset(&local, 0, sizeof(local));
	local.nl_family = AF_NETLINK;
	local.nl_groups = 0; /* no multicast */
	local.nl_pid 	= getpid();

	if (bind(fd, (struct sockaddr *) &local, sizeof(local)) < 0) {
		close(fd);
		perror("Unable to bind netlink socket");
		return -2;
	}
	#endif
	nl_sd = fd;
	return 0;
}

int create_appmgr_listen_socket() {
	int sock;
	struct sockaddr_in addr;

	if ( (sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
		perror("create_appmgr_listen_socket: socket");
		return -1;
	}
	memset((void *) &addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_port = htons(appmgr_socket_port_frommgr);
	inet_pton(AF_INET, LOCALHOST_ADDR, &addr.sin_addr);
	if (bind(sock, (struct sockaddr *)& addr, sizeof(addr)) < 0) {
		perror("create_appmgr_listen_socket: bind");
		return -2;
	}
	frommgr_sd = sock;
	return 0;
}

int create_appmgr_send_socket() {
	if ((tomgr_sd = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
		perror("create_appmgr_send_socket: socket");
		return -1;
	}

	memset(&tomgr_addr, 0, sizeof(tomgr_addr));
	tomgr_addr.sin_family = AF_INET;
	
	if (inet_pton(AF_INET, LOCALHOST_ADDR, & tomgr_addr.sin_addr.s_addr) <= 0) {
		printf("create_appmgr_send_socket: inet_pton");
		return 1;
	}
	tomgr_addr.sin_port = htons(appmgr_socket_port_tomgr);
	
	return 0;
}

int sendto_fd(int s, const char *buf, int bufLen) {
	struct sockaddr_nl nladdr;
	int r;

	memset(&nladdr, 0, sizeof(nladdr));
	nladdr.nl_family = AF_NETLINK;

	while ((r = sendto(s, buf, bufLen, 0, (struct sockaddr *) &nladdr, sizeof(nladdr))) < bufLen) {
		if (r > 0) {
			buf += r;
			bufLen -= r;
		} else if (errno != EAGAIN) return -1;
	}
	return 0;
}

void set_nl_attr(struct nlattr *na, const unsigned int type, const void *data, const unsigned int len) {
	int length = len + 2;
	na->nla_type = type;
	na->nla_len = length + NLA_HDRLEN; //message length
	memcpy(GENLMSG_NLA_DATA(na), data, length);
}

void reset_nl_attrs(void) {
	int i;
	for(i=0; i<=UPMT_APPMON_A_MAX; i++){
		nl_attr[i] = NULL;
	}
}

void parse_nl_attrs(struct genl_msg *ans) {
	reset_nl_attrs();

	unsigned int n_attrs = 0;
	struct nlattr *na;
	unsigned int data_len = GENLMSG_DATALEN(&ans->n);

	na = (struct nlattr *) GENLMSG_DATA(ans);
	nl_attr[na->nla_type] = na;
	n_attrs++;
	data_len = data_len - NLA_ALIGN(na->nla_len);

	while(data_len > 0){
		na = (struct nlattr *) GENLMSG_NLA_NEXT(na);
		nl_attr[na->nla_type] = na;
		n_attrs++;
		data_len = data_len - NLA_ALIGN(na->nla_len);
	}
	if(n_attrs > UPMT_APPMON_A_MAX) printf("parse_nl_attrs - too many attributes");
}

int handle_netlink_msg(struct genl_msg *ans) {
	memset(ans->buf, 0, MAX_BUF_LEN);
	int rep_len = recv(nl_sd, ans, sizeof(struct genl_msg), 0);
	
	if (ans->n.nlmsg_type == NLMSG_ERROR) {
		printf("Netlink: received NACK -- is module xt_UPMT inserted?\n");
		return -1;
	}
	if (rep_len < 0) {
		printf("Netlink: error receiving reply message\n");
		return -2;
	}
	if (!NLMSG_OK((&ans->n), rep_len)) {
		printf("Netlink: invalid reply message received\n");
		return -3;
	}
	return 0;
}

void newconn_handler(struct nlattr *na) {
	void *payload = GENLMSG_NLA_DATA(na);		
	struct upmt_new_conn_data *nd = (struct upmt_new_conn_data*) payload;
	
	cJSON *root, *key;
	root = cJSON_CreateObject();
	key = cJSON_CreateObject();
	cJSON_AddItemToObject(root, UPMT_CONN_NOTIF_JSON_KEY, key);
	
#ifndef ANDROID
	struct protoent *proto = getprotobynumber(nd->key.proto);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_PROTO, proto->p_name);
#else
	char* proto;
	if (nd->key.proto == 1)
		proto = "icmp";
	else if (nd->key.proto == 6)
		proto = "tcp";
	else if (nd->key.proto == 17)
		proto = "udp";
	else
		proto = "other";
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_PROTO, proto);
#endif

	char ipbuf[INET_ADDRSTRLEN];
	struct in_addr ip;
	
	ip.s_addr = nd->key.daddr;
	inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_DADDR, ipbuf);

	ip.s_addr = nd->key.saddr;
	inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_SADDR, ipbuf);
	
	cJSON_AddNumberToObject(key, UPMT_CONN_NOTIF_JSON_KEY_DPORT, nd->key.dport);
	cJSON_AddNumberToObject(key, UPMT_CONN_NOTIF_JSON_KEY_SPORT, nd->key.sport);
	
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_APPNAME, nd->appname);
	
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_TID, nd->tid);
	
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_COMMAND, UPMT_APP_JSON_NEWCONN_COMMAND);

	char *jsonmsg = cJSON_PrintUnformatted(root);
	printf("msg json: [%s]\n", jsonmsg);
	
	if (sendto(tomgr_sd, jsonmsg, strlen(jsonmsg), 0, (struct sockaddr *) &tomgr_addr, sizeof(tomgr_addr)) < 0)
		perror("newconn_handler: sendto");
}

//delete connections handler (bonus)
void deleteconn_handler(struct nlattr *na){
	void *payload = GENLMSG_NLA_DATA(na);
        struct upmt_del_conn_data *nd = (struct upmt_del_conn_data*) payload;

	cJSON *root, *key;
        root = cJSON_CreateObject();
        key = cJSON_CreateObject();
        cJSON_AddItemToObject(root, UPMT_CONN_NOTIF_JSON_KEY, key);

#ifndef ANDROID
        struct protoent *proto = getprotobynumber(nd->key.proto);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_PROTO, proto->p_name);
#else
        char* proto;
        if (nd->key.proto == 1)
                proto = "icmp";
        else if (nd->key.proto == 6)
                proto = "tcp";
        else if (nd->key.proto == 17)
                proto = "udp";
        else
                proto = "other";
        cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_PROTO, proto);
#endif
	char ipbuf[INET_ADDRSTRLEN];
        struct in_addr ip;

        ip.s_addr = nd->key.daddr;
        inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_DADDR, ipbuf);
	
	ip.s_addr = nd->key.saddr;
	inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(key, UPMT_CONN_NOTIF_JSON_KEY_SADDR, ipbuf);

	cJSON_AddNumberToObject(key, UPMT_CONN_NOTIF_JSON_KEY_DPORT, nd->key.dport);
	cJSON_AddNumberToObject(key, UPMT_CONN_NOTIF_JSON_KEY_SPORT, nd->key.sport);

	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_COMMAND, UPMT_APP_JSON_DELCONN_COMMAND);

	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_TID, nd->tid);

        char *jsonmsg = cJSON_PrintUnformatted(root);
        printf("[deleteconn_handler]\tmsg json: [%s]\n", jsonmsg);

	if (sendto(tomgr_sd, jsonmsg, strlen(jsonmsg), 0, (struct sockaddr *) &tomgr_addr, sizeof(tomgr_addr)) < 0)
                perror("deleteconn_handler: sendto");
}

//delete connections handler (bonus)
void deletetun_handler(struct nlattr *na){
	char ipbuf[INET_ADDRSTRLEN];
	struct in_addr ip;

	void *payload = GENLMSG_NLA_DATA(na);
	struct upmt_del_tun_data *nd = (struct upmt_del_tun_data*) payload;

	cJSON *root;
	root = cJSON_CreateObject();

	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_COMMAND, UPMT_APP_JSON_DELTUN_COMMAND);
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_TID, nd->tid);
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_IFNAME, nd->ifname);

	ip.s_addr = nd->daddr;
	inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_DADDR, ipbuf);

	char *jsonmsg = cJSON_PrintUnformatted(root);
	printf("[deletetun_handler]\tmsg json: [%s]\n", jsonmsg);

	if (sendto(tomgr_sd, jsonmsg, strlen(jsonmsg), 0, (struct sockaddr *) &tomgr_addr, sizeof(tomgr_addr)) < 0)
		perror("deletetun_handler: sendto");
}

void infotun_handler(struct nlattr *na){
	char ipbuf[INET_ADDRSTRLEN];
	struct in_addr ip;

	void *payload = GENLMSG_NLA_DATA(na);
	struct upmt_info_tun_data *nd = (struct upmt_info_tun_data*) payload;

	cJSON *root;
	root = cJSON_CreateObject();

	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_COMMAND, UPMT_APP_JSON_INFOTUN_COMMAND);
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_TID, nd->tid);
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_IFNAME, nd->ifname);

	ip.s_addr = nd->daddr;
	inet_ntop(AF_INET, &ip, ipbuf, INET_ADDRSTRLEN);
	cJSON_AddStringToObject(root, UPMT_APP_MSG_JSON_DADDR, ipbuf);

	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_DELAY, nd->rtt);
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_LOSS, nd->loss);
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_EWMADELAY, nd->ewmartt);
	cJSON_AddNumberToObject(root, UPMT_APP_MSG_JSON_EWMALOSS, nd->ewmaloss);

	char *jsonmsg = cJSON_PrintUnformatted(root);
	printf("[infotun_handler]\tmsg json: [%s]\n", jsonmsg);

	if (sendto(tomgr_sd, jsonmsg, strlen(jsonmsg), 0, (struct sockaddr *) &tomgr_addr, sizeof(tomgr_addr)) < 0)
		perror("infotun_handler: sendto");
}

void receive_and_dispatch_netlink_msg() {
	struct genl_msg ans;
	if (handle_netlink_msg(&ans) < 0) return;
	
	parse_nl_attrs(&ans);

	if (ans.g.cmd == UPMT_APPMON_C_NEW_CONN) {
		newconn_handler(nl_attr[UPMT_APPMON_A_NEW_CONN]);
	}
	//(bonus)
	if(ans.g.cmd == UPMT_APPMON_C_DEL_CONN) {
		deleteconn_handler(nl_attr[UPMT_APPMON_A_DEL_CONN]);
	}
	if(ans.g.cmd == UPMT_APPMON_C_DEL_TUN) {
		deletetun_handler(nl_attr[UPMT_APPMON_A_DEL_TUN]);
	}
	if(ans.g.cmd == UPMT_APPMON_C_INFO_TUN) {
		infotun_handler(nl_attr[UPMT_APPMON_A_INFO_TUN]);
	}
}

int netlink_send(int msgtype, void *msg);
int netlink_send_int(int cmd, int attr, int val);

void receive_and_dispatch_appmgr_msg() {
	char recmsg[UPMT_MSG_WIRELEN];
	int msgtype;
	cJSON *elem;
	
	memset(&recmsg, 0, UPMT_MSG_WIRELEN);
	recv(frommgr_sd, &recmsg, UPMT_MSG_WIRELEN, 0);
	printf("recmsg = [%s]\n", recmsg);
	
	cJSON *parsedmsg = cJSON_Parse(recmsg);
	if (parsedmsg == NULL) goto malformed;
	
	elem = cJSON_GetObjectItem(parsedmsg, UPMT_JSON_DEBUG);
	if (elem != NULL) goto debug;
	
	elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_MSGTYPE);
	if (elem == NULL) goto malformed;
	msgtype = elem->valueint;
	
	switch (msgtype) {
		case MSG_TYPE_APP: {
			struct upmt_app_msg msg;
			memset(&msg, 0, sizeof(struct upmt_app_msg));
	
			elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_COMMAND);
			if (elem == NULL) goto malformed;
			msg.command = elem->valueint;
			
			switch (msg.command) {
				case CMD_ADD:
					/**/ //Fabio Patriarca adds this lines
					elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_VIPA);
					if (elem == NULL) goto malformed;
					struct in_addr vipa;
					inet_pton(AF_INET, elem->valuestring, &vipa);
					//printf("VIPA: %s --> %d\n", elem->valuestring, vipa.s_addr);
					msg.vipa = vipa.s_addr;
					/**/

					elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_APPNAME);
					if (elem == NULL) goto malformed;
					strncpy(msg.appname, elem->valuestring, MAX_APPNAME_LENGTH);
					
					elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_TID);
					if (elem == NULL) goto malformed;
					msg.tid = elem->valueint;
					break;
					
				case CMD_RM:
					elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_APPNAME);
					if (elem == NULL) goto malformed;
					strncpy(msg.appname, elem->valuestring, MAX_APPNAME_LENGTH);
					break;
					
				case CMD_FLUSH_LIST:
					break;
				
				case CMD_SET_DEFAULT_TID:
					elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_TID);
					if (elem == NULL) goto malformed;
					msg.tid = elem->valueint;
					break;
			}
			
			if (netlink_send(msgtype, &msg)) goto netlinkerr;
		}
		break;
		
		case MSG_TYPE_NO_UPMT: {
			struct upmt_no_upmt_msg msg;
			memset(&msg, 0, sizeof(struct upmt_no_upmt_msg));
			
			elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_COMMAND);
			if (elem == NULL) goto malformed;
			msg.command = elem->valueint;
			
			if (msg.command == CMD_ADD || msg.command == CMD_RM) {
				elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_APPNAME);
				if (elem == NULL) goto malformed;
				strncpy(msg.appname, elem->valuestring, MAX_APPNAME_LENGTH);
			}
			else if (msg.command == CMD_FLUSH_LIST) {
				// no extra fields required
			}
			else goto malformed;
			
			if (netlink_send(msgtype, &msg)) goto netlinkerr;
		}
		break;
		
		case MSG_TYPE_APP_FLOW: {
			struct upmt_app_flow_msg msg;
			memset(&msg, 0, sizeof(struct upmt_app_flow_msg));
			
			elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_COMMAND);
			if (elem == NULL) goto malformed;
			msg.command = elem->valueint;
			
			if (msg.command == CMD_ADD || msg.command == CMD_RM) {
				elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_DADDR);
				if (elem == NULL) goto malformed;
				struct in_addr daddr;
				inet_pton(AF_INET, elem->valuestring, &daddr);
				printf("MSG_TYPE_APP_FLOW: %s --> %d\n", elem->valuestring, daddr.s_addr);
				msg.daddr = daddr.s_addr;
				
				elem = cJSON_GetObjectItem(parsedmsg, UPMT_APP_MSG_JSON_TID);
				if (elem == NULL) goto malformed;
				msg.tid = elem->valueint;
			}
			else if (msg.command == CMD_FLUSH_LIST) {
				// no extra fields required
			}
			else goto malformed;
			
			if (netlink_send(msgtype, &msg)) goto netlinkerr;
		}
		break;
		
		default:
			goto malformed;
	}
	
	return;
	
debug:
	netlink_send_int(UPMT_APPMON_C_DUMP_LIST, UPMT_APPMON_A_DUMP_LIST_ID, elem->valueint);
	return;

malformed:
	printf("appmgr message [%s] malformed\n", recmsg);
	return;

netlinkerr:
	printf("error while sending msg to netlink\n");
	return;
}

// Probe the controller in genetlink to find the family id for the UPMT_GNL_FAMILY_NAME family
int init_family_id() {
	struct genl_msg family_req, ans;
	int id = -10;
	struct nlattr *na;
	struct utsname utstemp;
	uname(&utstemp);

	// the netlink family name is now concatenated with the hostname of the namespace from where upmtconf is executed (Sander)
	char family_name[100];
	strcpy(family_name, UPMT_GNL_FAMILY_NAME);
	strcat(family_name, utstemp.nodename);

	// the family can't be too long (why 13? should be 16) (Sander)
	if (strlen(family_name)>13){
		printf("hostname too long");
		return -1;
	}

	family_req.n.nlmsg_type = GENL_ID_CTRL;
	family_req.n.nlmsg_flags = NLM_F_REQUEST;
	family_req.n.nlmsg_seq = ++seqno;
	family_req.n.nlmsg_pid = getpid();
	family_req.n.nlmsg_len = NLMSG_LENGTH(GENL_HDRLEN);
	family_req.g.cmd = CTRL_CMD_GETFAMILY;
	family_req.g.version = 0x1;

	printf("Appmon - trying to find family_name : %s\n",family_name);

	na = (struct nlattr *) GENLMSG_DATA(&family_req);
	set_nl_attr(na, CTRL_ATTR_FAMILY_NAME, family_name, strlen(family_name)); // Using family_name instead of UPMT_GNL_FAMILY_NAME (Sander)
	family_req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	if (sendto_fd(nl_sd, (char *) &family_req, family_req.n.nlmsg_len) < 0)
		return -2;

	int ret = handle_netlink_msg(&ans);
	if (ret < 0) return ret;
	na = (struct nlattr *) GENLMSG_DATA(&ans);
	na = (struct nlattr *) ((char *) na + NLA_ALIGN(na->nla_len));
	
	if (na->nla_type == CTRL_ATTR_FAMILY_ID) {
		id = *(__u16 *) GENLMSG_NLA_DATA(na);
		upmt_family_id = id;
		printf("Appmon - family id found for family_name : %s\n",family_name);
		return 0;
	}
	return -1;	
}

int associate() {
	return netlink_send_int(UPMT_APPMON_C_ASSOCIATE, UPMT_APPMON_A_ASSOCIATE, UPMT_ASSOCIATE_MAGIC_NUMBER);
}

int netlink_send_int(int cmd, int attr, int val) {
	struct nlattr *na;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_family_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= ++seqno;
	req.n.nlmsg_pid 	= getpid();
	req.g.cmd 			= cmd;

	na = (struct nlattr *) GENLMSG_DATA(&req);
	set_nl_attr(na, attr, &val, sizeof(int));
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;

	return 0;
}

int netlink_send(int msgtype, void *msg) {
	struct nlattr *na;

	req.n.nlmsg_len 	= NLMSG_LENGTH(GENL_HDRLEN);
	req.n.nlmsg_type 	= upmt_family_id;
	req.n.nlmsg_flags 	= NLM_F_REQUEST;
	req.n.nlmsg_seq 	= ++seqno;
	req.n.nlmsg_pid 	= getpid();
	switch (msgtype) {
		case MSG_TYPE_APP:
			req.g.cmd = UPMT_APPMON_C_APP;
			na = (struct nlattr *) GENLMSG_DATA(&req);
			set_nl_attr(na, UPMT_APPMON_A_APP, msg, sizeof(struct upmt_app_msg));
			break;
		case MSG_TYPE_NO_UPMT:
			req.g.cmd = UPMT_APPMON_C_NO_UPMT;
			na = (struct nlattr *) GENLMSG_DATA(&req);
			set_nl_attr(na, UPMT_APPMON_A_NO_UPMT, msg, sizeof(struct upmt_no_upmt_msg));
			break;
		case MSG_TYPE_APP_FLOW:
			req.g.cmd = UPMT_APPMON_C_APP_FLOW;
			na = (struct nlattr *) GENLMSG_DATA(&req);
			set_nl_attr(na, UPMT_APPMON_A_APP_FLOW, msg, sizeof(struct upmt_app_flow_msg));
			break;
	}
	req.n.nlmsg_len += NLMSG_ALIGN(na->nla_len);

	if (sendto_fd(nl_sd, (char *) &req, req.n.nlmsg_len) < 0) return -1;

	return 0;
}

int main(int argc, char *argv[]) {
	if (argc != 3) {
		printf("Usage:\n%s <local port> <remote port>\n", argv[0]);
		exit(255);
	}
	appmgr_socket_port_frommgr = atoi(argv[1]);
	appmgr_socket_port_tomgr = atoi(argv[2]);
	
	if (create_nl_socket()) {
		printf("Unable to create Netlink socket\n");
		exit(1);
	}
	if (create_appmgr_listen_socket()) {
		printf("Unable to create App-Mgr listen socket\n");
		exit(2);
	}
	if (create_appmgr_send_socket()) {
		printf("Unable to create App-Mgr send socket\n");
		exit(2);
	}
	if (init_family_id()) {
		printf("Unable to initialize Netlink family id\n");
		exit(3);
	}
	if (associate()) {
		printf("Unable to associate with Netlink peer\n");
		exit(4);
	}
	
	struct pollfd fds[SOCKETS_TO_POLL];
	fds[0].fd = nl_sd;
	fds[0].events = POLLIN;
	fds[1].fd = frommgr_sd;
	fds[1].events = POLLIN;

	fflush(stdout);

	while (poll(fds, SOCKETS_TO_POLL, -1) > 0) {
		
		if (fds[0].revents & POLLIN) {			//received netlink message
			receive_and_dispatch_netlink_msg();
		}
		else if (fds[1].revents & POLLIN) {		//received appmgr message
			receive_and_dispatch_appmgr_msg();		
		}

		
	}

	return 0;
}
