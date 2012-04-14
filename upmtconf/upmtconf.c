/*
 * upmtconf.c
 *
 *  Created on: 02/apr/2010
 *      Author: fabbox
 */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <arpa/inet.h>

#include "upmt_user.h"

#define C_GET 1
#define C_ADD 2
#define C_DEL 3
#define C_LST 4
#define C_HAN 5
#define C_ECH 6
#define C_VRB 7
#define C_FSH 8

/*JNI*/#ifdef CREATELIB
/*JNI*/#include "upmt_os_Module.h"
/*JNI*/#include <string.h>
/*JNI*/#define printf(...) do{ sprintf(buffer,__VA_ARGS__); __len = strnlen(buffer, 10000); if (__len > 0) { aretlen = aretlen + __len; _ret = realloc(_ret, aretlen); strncat(_ret, buffer, __len);} } while(0)
/*JNI*/#define exit(x) do{} while(0)
/*JNI*/JNIEXPORT jstring JNICALL Java_upmt_os_Module_upmtconf(JNIEnv * env, jobject obj, jobjectArray param)
/*JNI*/{
/*JNI*/		_ret = (char*) malloc(1);
/*JNI*/		_ret[0] = '\0';
/*JNI*/		aretlen = 1;
/*JNI*/#ifndef ANDROID
/*JNI*/		jclass strCls = (*env)->FindClass(env, "Ljava/lang/String;");
/*JNI*/#else
/*JNI*/		jclass strCls = (*env)->FindClass(env, "[Ljava/lang/String;");
/*JNI*/#endif
/*JNI*/		int length = (*env)->GetArrayLength(env, param);
/*JNI*/	
/*JNI*/		const char ** args = malloc((length+1) * sizeof(char*));
/*JNI*/		args[0]="execute";
/*JNI*/	
/*JNI*/		int index;
/*JNI*/		for(index = 0; index < length; index++)
/*JNI*/		{
/*JNI*/			jobject par = (*env)->GetObjectArrayElement(env, param, index);
/*JNI*/			jboolean iscopy;
/*JNI*/			const char* strpar = (*env)->GetStringUTFChars(env, par, &iscopy);
/*JNI*/			args[index+1] = strpar;
/*JNI*/		}
/*JNI*/	
/*JNI*/		int mainret = execute(length+1, args);
/*JNI*/		free(args);
/*JNI*/	
/*JNI*/		printf("return_code:%d\n", mainret);
/*JNI*/		jstring retstring = (*env)->NewStringUTF(env,_ret);
/*JNI*/		free(_ret);
/*JNI*/		return retstring;
/*JNI*/}
/*JNI*/
/*JNI*/int op;
/*JNI*/#else
int op = 0;
/*JNI*/#endif

void usage(){
	printf("\n USAGE\n");
	exit(-1);
}

void set_op(int operation){
	if(op == 0) op = operation;
	else usage();
}

void parse_number(int *n, char *string){
	*n = atoi(string);
	if(*n == 0) usage();
}

/*JNI*/#ifdef CREATELIB
/*JNI*/int execute(int argc, char **argv){
/*JNI*/#else
int main(int argc, char **argv){
/*JNI*/#endif
/*JNI*/#ifdef CREATELIB
/*JNI*/		optind = 0;
/*JNI*/		op=0;
/*JNI*/#endif

	char *tun 			= NULL;
	char *iname			= NULL;
	char *rule 			= NULL;
	char *tsa 			= NULL;
	char *dev 			= NULL;
	char *an 			= NULL;
	char *flush			= NULL;
	char *vpn			= NULL;

	unsigned int local_address 		= 0;
	unsigned int local_port 		= 0;
	unsigned int remote_address 	= 0;
	unsigned int remote_port 		= 0;
	unsigned int inat_local_address 	= 0;
	unsigned int inat_remote_address	= 0;
	int tid 						= -1;
	int rid 						= -1;
	int ifindex 					= -1;
	int proto 						= -1;
	int mark 						= -1;
	int verbose						= -1;

	int command 					= -1;
	char staticrule					= 2; // 2 = not static if new rule, don't change if modifying existing rule

	int c;

	while((c = getopt(argc, argv, "a:g:x:i:d:l:r:n:p:s:f:h:m:M:eV:S:D:t"))!= -1) {
		switch (c) {

			case 'e':
				set_op(C_ECH);
				break;

			case 'V':
				set_op(C_VRB);
				if(strcmp(optarg, "off") == 0) verbose = -1;
				else parse_number(&verbose, optarg);
				break;

			case 'a':
				if(strcmp(optarg, "tun") == 0) 			tun	 = optarg;
				else if(strcmp(optarg, "rule") == 0) 	rule = optarg;
				else if(strcmp(optarg, "tsa") == 0) 	tsa = optarg;
				else if(strcmp(optarg, "dev") == 0) 	dev = optarg;
				else if(strcmp(optarg, "vpn") == 0) 	vpn = optarg;
				else usage();
				set_op(C_ADD);
				break;

			case 'f':
				if(strcmp(optarg, "tun") == 0) 			flush = optarg;
				else if(strcmp(optarg, "rule") == 0) 	flush = optarg;
				else if(strcmp(optarg, "tsa") == 0) 	flush = optarg;
				else if(strcmp(optarg, "all") == 0) 	flush = optarg;
				else usage();
				set_op(C_FSH);
				break;

			case 'g':
				if(strcmp(optarg, "tun") == 0) 			tun	 = optarg;
				else if(strcmp(optarg, "rule") == 0) 	rule = optarg;
				else usage();
				set_op(C_GET);
				break;

			case 'x':
				if(strcmp(optarg, "tun") == 0) 			tun	 = optarg;
				else if(strcmp(optarg, "rule") == 0) 	rule = optarg;
				else if(strcmp(optarg, "tsa") == 0) 	tsa = optarg;
				else if(strcmp(optarg, "dev") == 0) 	dev = optarg;
				else if(strcmp(optarg, "vpn") == 0) 	vpn = optarg;
				else usage();
				set_op(C_DEL);
				break;

			case 'm':
				if(strcmp(optarg, "an") == 0) 			an	 = optarg;
				else usage();
				break;

			case 'h':
				parse_number(&rid, optarg);
				set_op(C_HAN);
				break;

			case 'l':
				if(strcmp(optarg, "tun") == 0)			{ tun = optarg; 	set_op(C_LST); }
				else if(strcmp(optarg, "rule") == 0)	{ rule = optarg; 	set_op(C_LST); }
				else if(strcmp(optarg, "tsa") == 0)		{ tsa = optarg; 	set_op(C_LST); }
				else if(strcmp(optarg, "dev") == 0)		{ dev = optarg; 	set_op(C_LST); }
				else if(strcmp(optarg, "vpn") == 0)		{ vpn = optarg; 	set_op(C_LST); }
				else{
					local_port = atoi(optarg);
					if(local_port == 0) usage();
				}
				break;

			case 'i':
				iname = optarg;
				break;

			case 'd':
				if(inet_pton(AF_INET, optarg, &remote_address) != 1) usage();
				break;

			case 'S':
				if(strcmp(optarg, "off") == 0) break;
				if(inet_pton(AF_INET, optarg, &inat_local_address) != 1) usage();
				break;

			case 'D':
				if(strcmp(optarg, "off") == 0) break;
				if(inet_pton(AF_INET, optarg, &inat_remote_address) != 1) usage();
				break;

			case 'r':
				parse_number(&remote_port, optarg);
				break;

			case 'n':
				if(strcmp(optarg, "default") == 0) ifindex = 1111;
				else parse_number(&tid, optarg);
				break;

			case 'p':
				if(strcmp(optarg, "tcp") == 0) 			proto = 6;
				else if(strcmp(optarg, "udp") == 0) 	proto = 17;
				else usage();
				break;

			case 's':
				if(inet_pton(AF_INET, optarg, &local_address) != 1) usage();
				break;

			case 'M':
				parse_number(&mark, optarg);
				break;
			
			case 't':
				staticrule = 1;
				break;

			default:
				usage();
				break;
			}
	}

	struct tun_local tl = {
				.ifindex = -1,
				.port 	 = local_port
	};

	struct tun_remote tr = {
			.addr = remote_address,
			.port = remote_port
	};

	struct upmt_key key = {
			.proto = proto,
			.saddr = local_address,
			.daddr = remote_address,
			.sport = local_port,
			.dport = remote_port
	};

	struct tun_param tp = {
			.tid = tid,
			.tl = {
					.ifindex = ifindex,
					.port = local_port
			},
			.tr = {
					.addr = remote_address,
					.port = remote_port
			},
			.in = {
					.local = inat_local_address,
					.remote = inat_remote_address
			}
	};

	if(local_port > 65535) usage();
	if(remote_port > 65535) usage();

	upmt_genl_client_init();

	if(op == C_FSH){
		send_flush_command(flush);
		receive_response();
	}

	if(op == C_VRB){
		send_verbose_command(verbose);
		receive_response();
	}

	if(op == C_ECH){
		send_echo_command();
		receive_response();
	}

	if(op == C_HAN){
		if(rid <= 0) usage();
		if(tid <= 0) usage();
		//printf("\n\t TID: %d", tid);
		//printf("\n\t RID: %d", rid);
		send_handover_command(rid, tid);
		receive_response();
	}

	if(dev != NULL){
		if(op == C_ADD){
			if(iname == NULL) usage();
			command = UPMT_C_SET_MDL;
			send_mdl_command(command, iname);
			printf("\n Sending set-dev request...");
		}
		if(op == C_DEL){
			if(iname == NULL) usage();
			command = UPMT_C_DEL_MDL;
			send_mdl_command(command, iname);
			printf("\n Sending del-dev request...");
		}
		if(op == C_LST){
			command = UPMT_C_LST_MDL;
			send_mdl_command(command, NULL);
			printf("\n Sending lst-dev request...");
		}
		receive_response();
	}

	if(tsa != NULL){
		if(op == C_ADD){
			if((iname == NULL)||(local_port == 0)) usage();
			command = UPMT_C_SET_TSA;
			send_tsa_command(command, &tl, iname);
			printf("\n Sending set-tsa request...");
		}
		if(op == C_DEL){
			if((iname == NULL)||(local_port == 0)) usage();
			command = UPMT_C_DEL_TSA;
			send_tsa_command(command, &tl, iname);
			printf("\n Sending del-tsa request...");
		}
		if(op == C_LST){
			command = UPMT_C_LST_TSA;
			send_tsa_command(command, NULL, NULL);
			printf("\n Sending lst-tsa request...");
		}
		receive_response();
	}

	if(tun != NULL){
		if(op == C_ADD){
			if((iname == NULL)||(local_port == 0)||(remote_address == 0)||(remote_port == 0)) usage();
			command = UPMT_C_SET_TUNNEL;
			send_tunt_command(command, iname, &tp);
			printf("\n Sending set-tun request...");
		}

		if(op == C_GET){
			if(tid <= 0) usage();
			command = UPMT_C_GET_TUNNEL;
			send_tunt_command(command, NULL, &tp);
			printf("\n Sending get-tun request...");
		}

		if(op == C_DEL){
			if(tid <= 0) usage();
			command = UPMT_C_DEL_TUNNEL;
			send_tunt_command(command, iname, &tp);
			printf("\n Sending del-tun request...");
		}

		if(op == C_LST){
			command = UPMT_C_LST_TUNNEL;
			send_tunt_command(command, NULL, NULL);
			printf("\n Sending lst-tun request...");
		}

		receive_response();
	}

	if(rule != NULL){
		if(op == C_ADD){
			if((proto < 0)||(local_address == 0)||(local_port == 0)||(remote_address == 0)||(remote_port == 0)) usage();
			command = UPMT_C_SET_RULE;
			send_paft_command(command, tid, rid, &key, staticrule);
			printf("\n Sending set-rule request...");
		}

		if(op == C_GET){
			rid = tid;
			if(rid <= 0) usage();
			command = UPMT_C_GET_RULE;
			send_paft_command(command, -1, rid, &key, -1);
			printf("\n Sending get-rule request...");
		}

		if(op == C_DEL){
			rid = tid;
			if(rid <= 0) usage();
			command = UPMT_C_DEL_RULE;
			send_paft_command(command, -1, rid, &key, -1);
			printf("\n Sending del-rule request...");
		}

		if(op == C_LST){
			command = UPMT_C_LST_RULE;
			send_paft_command(command, -1, -1, NULL, -1);
			printf("\n Sending lst-rule request...");
		}

		receive_response();
	}

	if(an != NULL){
		if(mark < 0) usage();
		send_an_command(mark);
		receive_response();
	}

	if(vpn != NULL){
		if(op == C_ADD){
			if((remote_address == 0) || (tid < 0)) usage();
			command = UPMT_C_SET_PDFT;
			send_pdft_command(command, remote_address, tid);
			printf("\n Sending set-pdft request...");
		}
		if(op == C_DEL){
			if((remote_address == 0)) usage();
			command = UPMT_C_DEL_PDFT;
			send_pdft_command(command, remote_address, tid);
			printf("\n Sending del-pdft request...");
		}
		if(op == C_LST){
			command = UPMT_C_LST_PDFT;
			send_pdft_command(command, -1, -1);
			printf("\n Sending lst-pdft request...");
		}
		receive_response();
	}


	printf("\n\n");

/*JNI*/#ifdef CREATELIB
/*JNI*/		close(socket_desc);
/*JNI*/#endif

	return 0;
}
