/*
 * upmt_genl.h
 *
 *  Created on: 15/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_GENL_H_
#define UPMT_GENL_H_

#define MAX_RULES_PER_MSG 150

struct RESP_MSG_DATA{
	int atype;
	void *data;
	int len;
};

int upmt_genl_register(void);
int upmt_genl_unregister(void);

#endif /* UPMT_GENL_H_ */
