/*
 * upmt_gnl_config.h
 *
 *  Created on: 15/mar/2010
 *      Author: fabbox
 */

#ifndef UPMT_GENL_CONFIG_H_
#define UPMT_GENL_CONFIG_H_

// Family name is shortened to enable concatenation with the hostname and support for namespaces (Sander)
#define UPMT_GNL_FAMILY_NAME "UC_"
#define UPMT_GNL_FAMILY_VERSION 1

/* attributes */
enum UPMT_GNL_ATTRIBUTES{
	UPMT_A_UNSPEC,

	UPMT_A_MSG_TYPE,
	UPMT_A_MSG_MESSAGE,

	UPMT_A_PAFT_RID,
	UPMT_A_PAFT_KEY,
	UPMT_A_PAFT_STATIC,

	UPMT_A_TUN_TID,
	UPMT_A_TUN_LOCAL,
	UPMT_A_TUN_REMOTE,

	UPMT_A_TUN_DEV,
	UPMT_A_TUN_PARAM,

	UPMT_A_MARK_DEV,

	UPMT_A_AN_MARK,
	UPMT_A_VERBOSE,
	
	UPMT_A_IP_ADDR,

	UPMT_A_KEEP_STATE,
	UPMT_A_KEEP_PERIOD,
	UPMT_A_KEEP_TIMEOUT,

	UPMT_A_LAST_LST_MSG,

	__UPMT_A_MSG_MAX,
};

#define UPMT_A_MSG_MAX (__UPMT_A_MSG_MAX - 1)

/* commands */
enum UPMT_GNL_COMMANDS{
	UPMT_C_UNSPEC1, //do not touch, this is for the commands order
	UPMT_C_UNSPEC2, //do not touch, this is for the commands order

	UPMT_C_ECHO,

	UPMT_C_GET_RULE,
	UPMT_C_SET_RULE,
	UPMT_C_DEL_RULE,
	UPMT_C_LST_RULE,

	UPMT_C_GET_TUNNEL,
	UPMT_C_SET_TUNNEL,
	UPMT_C_DEL_TUNNEL,
	UPMT_C_LST_TUNNEL,

	UPMT_C_SET_TSA,
	UPMT_C_DEL_TSA,
	UPMT_C_LST_TSA,

	UPMT_C_SET_MDL,
	UPMT_C_DEL_MDL,
	UPMT_C_LST_MDL,

	UPMT_C_HANDOVER,
	UPMT_C_AN,

	UPMT_C_VERBOSE,
	UPMT_C_FLUSH,

	UPMT_C_SET_PDFT,
	UPMT_C_DEL_PDFT,
	UPMT_C_LST_PDFT,

	UPMT_C_SET_KEEP,

	__UPMT_C_MAX,
};

#define UPMT_C_MAX (__UPMT_C_MAX - 1)

//#define STRCAT_STRING(S1, S2) (strcat(strcat("", S1), S2))



/***************************/

#define REQUEST_MSG 				"request"
#define RESPONSE_MSG 				"response"


//da cancellare
/*
 * GET response
 */
#define GET_FOUND_RESPONSE_MSG 		"response 200 OK"
#define GET_NOT_FOUND_RESPONSE_MSG 	"response 404 Not Found"
/*
 * SET response
 */
#define SET_RESPONSE_MSG 			"response 202 Accepted"
#define SET_ERROR_RESPONSE_MSG 		"response 500 Error"

#define SET_CHOISE_RESPONSE_MSG 	"response 300 Multiple Choices"
#define SET_OVERWRITE_REQUEST_MSG	"request 100 Continue"
/*
 * DEL response
 */
#define DEL_RESPONSE_MSG	 		"response 202 Accepted"
#define DEL_NOT_FOUND_RESPONSE_MSG	"response 404 Not Found"

#define LST_RESPONSE_MSG	 		"response 207 OK"

#define HAN_RESPONSE_MSG	 		"response 202 Accepted"
#define HAN_ERROR_RESPONSE_MSG 		"response 500 Error"

#endif /* UPMT_GENL_CONFIG_H_ */
