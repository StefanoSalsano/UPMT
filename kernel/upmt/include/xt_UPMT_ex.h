/*
 * upmt_dev.h
 *
 *  Created on: 01/apr/2010
 *      Author: fabbox
 */

#ifndef XT_UPMT_EX_H_
#define XT_UPMT_EX_H_

extern unsigned int appmon_pid;
extern struct genl_family upmt_appmgr_gnl_family;
extern unsigned int conn_notif_seqno;

int	xt_UPMT_ex_init(void);
void	xt_UPMT_ex_exit(void);

#endif /* UPMT_DEV_H_ */
