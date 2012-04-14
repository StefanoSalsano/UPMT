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

inline void bul_write_lock_irqsave(unsigned long fl) {
	write_lock_irqsave(&bul_mutex, fl);
}

inline void bul_write_unlock_irqrestore(unsigned long fl) {
	write_unlock_irqrestore(&bul_mutex, fl);
}

/*
 * OLD LOCKS
 */

spinlock_t tunt_lock 	= SPIN_LOCK_UNLOCKED;
spinlock_t paft_lock 	= SPIN_LOCK_UNLOCKED;
spinlock_t tsa_lock 	= SPIN_LOCK_UNLOCKED;
spinlock_t mark_lock 	= SPIN_LOCK_UNLOCKED;
spinlock_t mdl_lock 	= SPIN_LOCK_UNLOCKED;

void lock_paft(void){
	spin_lock(&paft_lock);
}

void lock_tunt(void){
	spin_lock(&tunt_lock);
}

void lock_tsa(void){
	spin_lock(&tsa_lock);
}

void lock_mdl(void){
	spin_lock(&mdl_lock);
}

void lock_an_mark(void){
	spin_lock(&mark_lock);
}

void lock_paft_tunt(void){
	lock_paft();
	lock_tunt();
}

void lock_tunt_tsa(void){
	lock_tunt();
	lock_tsa();
}

void lock_mdl_tsa_tunt(void){
	lock_mdl();
	lock_tsa();
	lock_tunt();
}

void lock_mdl_tsa(void){
	lock_mdl();
	lock_tsa();
}

void lock_paft_tunt_tsa(void){
	lock_paft();
	lock_tunt();
	lock_tsa();
}

void lock_mdl_tsa_tunt_paft(void){
	spin_lock(&mdl_lock);
	spin_lock(&tsa_lock);
	spin_lock(&tunt_lock);
	spin_lock(&paft_lock);
}

/*
 * UNLOCK
 */

void unlock_paft(void){
	spin_unlock(&paft_lock);
}

void unlock_tunt(void){
	spin_unlock(&tunt_lock);
}

void unlock_tsa(void){
	spin_unlock(&tsa_lock);
}

void unlock_mdl(void){
	spin_unlock(&mdl_lock);
}

void unlock_an_mark(void){
	spin_unlock(&mark_lock);
}

void unlock_paft_tunt(void){
	unlock_tunt();
	unlock_paft();
}

void unlock_tunt_tsa(void){
	unlock_tsa();
	unlock_tunt();
}

void unlock_mdl_tsa(void){
	unlock_mdl();
	unlock_tsa();
}

void unlock_mdl_tsa_tunt(void){
	unlock_tunt();
	unlock_tsa();
	unlock_mdl();
}

void unlock_paft_tunt_tsa(void){
	unlock_tsa();
	unlock_tunt();
	unlock_paft();
}

void unlock_mdl_tsa_tunt_paft(void){
	unlock_paft();
	unlock_tunt();
	unlock_tsa();
	unlock_mdl();
}
