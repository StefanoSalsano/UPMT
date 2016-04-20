/*
 * upmt_locks.c
 *
 *  Created on: 30/apr/2010
 *      Author: fabbox
 */

#include "include/upmt_locks.h"

/*
 * BIG UPMT LOCK
 */

DEFINE_RWLOCK(bul_mutex);

inline void bul_read_lock() {
	read_lock(&bul_mutex);
}

inline void bul_read_unlock() {
	read_unlock(&bul_mutex);
}

inline void bul_write_lock() {
	write_lock(&bul_mutex);
}

inline void bul_write_unlock() {
	write_unlock(&bul_mutex);
}

inline void bul_read_lock_bh() {
	read_lock_bh(&bul_mutex);
}

inline void bul_read_unlock_bh() {
	read_unlock_bh(&bul_mutex);
}

inline void bul_write_lock_bh() {
	write_lock_bh(&bul_mutex);
}

inline void bul_write_unlock_bh() {
	write_unlock_bh(&bul_mutex);
}

inline void bul_write_lock_irqsave(unsigned long flag) {
	write_lock_irqsave(&bul_mutex, flag);
}

inline void bul_write_unlock_irqrestore(unsigned long flag) {
	write_unlock_irqrestore(&bul_mutex, flag);
}
// OLD locks removed (Sander)
