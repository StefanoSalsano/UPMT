/*
 * upmt_dev.h
 *
 *  Created on: 01/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_DEV_H_
#define UPMT_DEV_H_

//#include "upmt.h"
//#include <linux/kernel.h> 		//this is for the u16 and u32 type

extern struct net_device *upmt_dev;

int 	upmt_dev_register(void);
void 	upmt_dev_unregister(void);

#endif /* UPMT_DEV_H_ */
