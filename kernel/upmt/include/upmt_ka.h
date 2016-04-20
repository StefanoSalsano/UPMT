/*
 * upmt_ka.h
 *
 *  Created on: Jul 18, 2012
 *      Author: upmt
 */

#ifndef UPMT_KA_H_
#define UPMT_KA_H_

#include "upmt.h"
#include "upmt_tunt.h"
//#include "upmt_conntracker_appmon.h"

#include <linux/skbuff.h>

#define KEEP_ALIVE_ON	10
#define KEEP_ALIVE_OFF	0

#define KEEP_ALIVE_REQUEST	10
#define KEEP_ALIVE_RESPONSE	20

#define KEEP_ALIVE_SRC_PORT	60000
#define KEEP_ALIVE_DST_PORT	60001

#define KEEP_ALIVE_CLIENT_MODE	1
#define KEEP_ALIVE_SERVER_MODE	2

#define KP_BYTES 3

/*milliseconds*/
extern unsigned long KEEP_ALIVE_COUNTER;

/*milliseconds*/

extern unsigned long T_KA;

extern unsigned long kc;
extern unsigned long K;
extern unsigned long T_TO;

extern unsigned long N;
extern unsigned long T_L;

struct ka_payload{
	int tid:32;

	unsigned long id:32;
	unsigned long type:32;

	struct keep_alive_info info;

	/***/

	/*unsigned long timestamp:32;
	unsigned long rtt:32;
	unsigned long loss:32;*/
};

/*
 * upmt_ka.c
 */

void	manage_piggy_keep_alive(struct tunt_entry *, struct ka_payload *);
int		manage_keep_alive(struct tunt_entry *, struct sk_buff *);
int		ka_timer_register(void);
int		ka_timer_unregister(void);

/*
 * upmt_ka_util.c
 */

void	to24bit(unsigned char *, u64);
u32		to32bit(unsigned char *);

void			set_ka_values(void);
void			print_ka_info(struct keep_alive_info);
void			print_tun_loss_info(struct tunt_entry *, int);
unsigned long	getMSecJiffies(void);
u64				getMSecJiffies2(void);
unsigned long	getJiffiesMSec(unsigned long);
int 			is_ka_pkt(struct sk_buff *skb, int);
int				is_ka_request(struct upmt_key *);
int				is_ka_response(struct upmt_key *);
int				isTimeout(struct tunt_entry *);

unsigned long	compute_LOSS_client(struct tunt_entry *);
unsigned long	compute_LOSS_server(struct tunt_entry *);

unsigned long	compute_RTT(struct tunt_entry *, int);
unsigned long	compute_LOSS(struct tunt_entry *, struct ka_payload *, int);

int				send_keep_alive_message(struct sk_buff *);
struct sk_buff*	create_keep_alive_request(struct tunt_entry *);
struct sk_buff*	create_keep_alive_response(struct tunt_entry *);
void			create_keep_alive_payload(struct tunt_entry *, struct ka_payload *, int);

/*
 * upmt_ka_net.c
 */

#endif /* UPMT_KA_H_ */
