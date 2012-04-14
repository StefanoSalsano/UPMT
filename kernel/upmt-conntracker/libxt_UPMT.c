#include <stdio.h>
#include <xtables.h>

static void upmt_tg_help() {
	printf("UPMT takes no options\n\n");
}

static int upmt_tg_parse(int c, char **argv, int invert, unsigned int *flags, const void *entry, struct xt_entry_target **target) {
	return 0;
}

static void upmt_tg_check(unsigned int flags){
}

static struct xtables_target upmt_tg_reg = {
	.version       = XTABLES_VERSION,
	.name          = "UPMT",
	.family        = AF_UNSPEC,
	.help          = upmt_tg_help,
	.parse         = upmt_tg_parse,
	.final_check   = upmt_tg_check,
};

static __attribute__((constructor)) void upmt_tg_ldr(void) {
	xtables_register_target(&upmt_tg_reg);
}
