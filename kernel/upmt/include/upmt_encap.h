/*
 * upmt_encap.h
 *
 *  Created on: 18/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_ENCAP_H_
#define UPMT_ENCAP_H_

#include "upmt.h"
#include "upmt_ka.h"
#include <linux/skbuff.h>

int isPiggy(struct sk_buff *);

void inat_packet(struct sk_buff *, const struct tun_param *);
void denat_packet(struct sk_buff *);

int encap(struct sk_buff *, struct tun_param *, unsigned int, struct ka_payload *);
int decap(struct sk_buff *, struct tunt_entry *);

void mark_packet(struct sk_buff *);

#endif /* UPMT_ENCAP_H_ */
