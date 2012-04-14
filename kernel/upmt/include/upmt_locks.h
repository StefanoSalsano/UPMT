/*
 * upmt_locks.h
 *
 *  Created on: 13/apr/2010
 *      Author: fabbox
 */

#ifndef UPMT_LOCKS_H_
#define UPMT_LOCKS_H_

#include <linux/spinlock.h>

extern rwlock_t bul_mutex;
void bul_read_lock(void);
void bul_read_unlock(void);
void bul_write_lock(void);
void bul_write_unlock(void);
void bul_read_lock_bh(void);
void bul_read_unlock_bh(void);
void bul_write_lock_bh(void);
void bul_write_unlock_bh(void);
void bul_write_lock_irqsave(unsigned long fl);
void bul_write_unlock_irqrestore(unsigned long fl);

extern spinlock_t tunt_lock;
extern spinlock_t paft_lock;
extern spinlock_t tsa_lock;
extern spinlock_t mark_lock;
extern spinlock_t mdl_lock;

void lock_paft(void);
void lock_tunt(void);
void lock_tsa(void);
void lock_mdl(void);
void lock_an_mark(void);

void lock_paft_tunt(void);
void lock_tunt_tsa(void);
void lock_mdl_tsa_tunt(void);
void lock_mdl_tsa(void);
void lock_paft_tunt_tsa(void);
void lock_mdl_tunt_tsa(void);
void lock_mdl_tsa_tunt_paft(void);


void unlock_paft(void);
void unlock_tunt(void);
void unlock_tsa(void);
void unlock_mdl(void);
void unlock_an_mark(void);

void unlock_paft_tunt(void);
void unlock_tunt_tsa(void);
void unlock_mdl_tsa_tunt(void);
void unlock_mdl_tsa(void);
void unlock_paft_tunt_tsa(void);
void unlock_mdl_tunt_tsa(void);
void unlock_mdl_tsa_tunt_paft(void);

#endif /* UPMT_LOCKS_H_ */
