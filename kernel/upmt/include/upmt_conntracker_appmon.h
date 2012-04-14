
#ifndef UPMT_CONNTRACKER_APPMON_H_
#define UPMT_CONNTRACKER_APPMON_H_

#define MAX_APPNAME_LENGTH				64

// maximum total length for a json message, used as buffer size
#define UPMT_MSG_WIRELEN				512

// netlink family
#define UPMT_GNL_FAMILY_NAME			"UPMT_APPMON"
#define UPMT_GNL_FAMILY_VERSION			1

#define UPMT_ASSOCIATE_MAGIC_NUMBER		0x0b0e

enum upmt_cmd {
	CMD_ADD,
	CMD_RM,
	CMD_FLUSH_LIST,
	CMD_SET_DEFAULT_TID
};

enum upmt_msgtype {
	MSG_TYPE_APP,
	MSG_TYPE_NO_UPMT,
	MSG_TYPE_APP_FLOW,
};

enum UPMT_GNL_APPMON_ATTRIBUTES {
	UPMT_APPMON_A_UNSPEC,	
	UPMT_APPMON_A_ASSOCIATE,

	UPMT_APPMON_A_APP,
	UPMT_APPMON_A_NO_UPMT,
	UPMT_APPMON_A_APP_FLOW,
	
	UPMT_APPMON_A_NEW_CONN,
	
	UPMT_APPMON_A_DUMP_LIST_ID,

	__UPMT_APPMON_A_MAX
};
#define UPMT_APPMON_A_MAX (__UPMT_APPMON_A_MAX - 1)

enum UPMT_GNL_APPMON_COMMANDS {
	UPMT_APPMON_C_UNSPEC,
	UPMT_APPMON_C_ASSOCIATE,
	
	UPMT_APPMON_C_APP,
	UPMT_APPMON_C_NO_UPMT,
	UPMT_APPMON_C_APP_FLOW,
	
	UPMT_APPMON_C_NEW_CONN,
	
	UPMT_APPMON_C_DUMP_LIST,
	
	__UPMT_APPMON_C_MAX
};
#define UPMT_APPMON_C_MAX (__UPMT_APPMON_C_MAX - 1)

struct upmt_app_msg {
	enum upmt_cmd command;
	char appname[MAX_APPNAME_LENGTH];
	int tid;
};

struct upmt_no_upmt_msg {
	enum upmt_cmd command;
	char appname[MAX_APPNAME_LENGTH];
};

struct upmt_app_flow_msg {
	enum upmt_cmd command;
	unsigned int daddr;
	int tid;
};

#define UPMT_APP_MSG_JSON_COMMAND		"command"
#define UPMT_APP_MSG_JSON_MSGTYPE		"msgtype"
#define UPMT_APP_MSG_JSON_APPNAME		"appname"
#define UPMT_APP_MSG_JSON_TID			"tid"
#define UPMT_APP_MSG_JSON_DADDR			"daddr"

#define UPMT_JSON_DEBUG					"debug"


struct upmt_new_conn_data {
	struct upmt_key key;
	char appname[MAX_APPNAME_LENGTH];
	int tid;
};

#define UPMT_CONN_NOTIF_JSON_KEY		"key"
#define UPMT_CONN_NOTIF_JSON_APPNAME	"appname"
#define UPMT_CONN_NOTIF_JSON_TID		"tid"

#define UPMT_CONN_NOTIF_JSON_KEY_PROTO	"proto"
#define UPMT_CONN_NOTIF_JSON_KEY_SADDR	"vipa"
#define UPMT_CONN_NOTIF_JSON_KEY_DADDR	"dstIp"
#define UPMT_CONN_NOTIF_JSON_KEY_DPORT	"dstPort"
#define UPMT_CONN_NOTIF_JSON_KEY_SPORT	"srcPort"

#endif /* UPMT_CONNTRACKER_APPMON_H_ */
