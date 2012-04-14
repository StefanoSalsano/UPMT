/*
 * upmt_stamp.h
 *
 *  Created on: 15/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_STAMP_H_
#define UPMT_STAMP_H_

#include "upmt.h"

void 	print_ip_address(unsigned int);
void 	print_upmt_key(const struct upmt_key *);

void 	print_upmt_tun_id(unsigned int);
void 	print_upmt_tun_local(const struct tun_local *);
void 	print_upmt_tun_remote(const struct tun_remote *);
void 	print_upmt_tun_param(const struct tun_param *);

#endif /* UPMT_STAMP_H_ */
