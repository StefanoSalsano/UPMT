/*
 * upmt_encap.h
 *
 *  Created on: 18/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_ENCAP_H_
#define UPMT_ENCAP_H_

#include "upmt.h"
#include <linux/skbuff.h>

void inat_packet(struct sk_buff *, const struct tun_param *);
void denat_packet(struct sk_buff *);

int encap(struct sk_buff *, const struct tun_param *);
int decap(struct sk_buff *);

void mark_packet(struct sk_buff *);

#endif /* UPMT_ENCAP_H_ */
