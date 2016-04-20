/*
 * upmt_user.h
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_USER_H_
#define UPMT_USER_H_

#include "upmt_genl_config.h"

#include "upmt.h"

#include <sys/socket.h>
#include <linux/netlink.h>
#include <linux/genetlink.h>
#include <sys/types.h>

#define MAX_BUF_LEN 4096

struct genl_msg{
	struct nlmsghdr n;		//128 bit = 16 bytes
	struct genlmsghdr g;	//32  bit = 8  bytes

	//25 ---> 60
	char buf[MAX_BUF_LEN];
};

#define GENLMSG_DATA(glh) 			((void *)(NLMSG_DATA(glh) + GENL_HDRLEN))
#define GENLMSG_DATALEN(glh) 		(NLMSG_PAYLOAD(glh, 0) - GENL_HDRLEN)
#define GENLMSG_NLA_NEXT(na) 		(((void *)(na)) + NLA_ALIGN(na->nla_len))
#define GENLMSG_NLA_DATA(na) 		((void *)((char*)(na) + NLA_HDRLEN))
#define GENLMSG_NLA_DATALEN(na) 	(na->nla_len - NLA_HDRLEN - 1)

void init_data(void);

int upmt_genl_client_init();

int send_tunt_command(const int, const char *, const struct tun_param *);
int send_paft_command(const int, const int, const int, const struct upmt_key *, char);
int send_tsa_command(const int, const struct tun_local *, char *);
int send_handover_command(int, int);
int send_an_command(int);
int send_verbose_command(int);
int send_flush_command(char *);
int send_mdl_command(const int, char *);
int send_pdft_command(const int, const unsigned int, const int);
int send_keepAlive_command(int, int, unsigned int, unsigned int);

int receive_response();
int parse_lst_nl_attrs();
void parse_nl_attrs();
void printResponse(const unsigned int);

/*JNI*/#ifdef CREATELIB
/*JNI*/int socket_desc;
/*JNI*/#endif

#endif /* UPMT_USER_H_ */
