package com.shashi.service;

import com.shashi.beans.SeatLockBean;
import com.shashi.beans.TrainException;

/**
 * SeatLockService manages temporary seat reservations during booking
 * 
 * Features:
 * - Lock seats for 5 minutes when user initiates booking
 * - Prevent double booking by holding seats
 * - Auto-release expired locks
 * - Release locks on booking confirmation or timeout
 */
public interface SeatLockService {

    /**
     * Create a temporary seat lock for a user on a specific train
     * Reserves seats for 5 minutes during checkout process
     * 
     * @param trainNo Train number to lock
     * @param mailId User email ID
     * @param seatsToLock Number of seats to lock
     * @return SeatLockBean with lock information
     * @throws TrainException if insufficient seats or DB error
     */
    public SeatLockBean acquireSeatLock(long trainNo, String mailId, int seatsToLock) throws TrainException;
    
    /**
     * Release a seat lock when booking is confirmed
     * Converts reserved seats to confirmed bookings
     * 
     * @param lockId Seat lock ID to release
     * @return true if lock was successfully released
     * @throws TrainException if lock not found or DB error
     */
    public boolean releaseSeatLock(int lockId) throws TrainException;
    
    /**
     * Remove all expired seat locks
     * Called periodically to clean up stale reservations
     * Automatically triggered by scheduled task or manually
     * 
     * @return Number of locks removed
     * @throws TrainException if DB error occurs
     */
    public int cleanupExpiredLocks() throws TrainException;
    
    /**
     * Get total locked seats for a specific train
     * Used to calculate actual available seats (seats - locked_seats)
     * 
     * @param trainNo Train number
     * @return Total number of currently locked seats
     * @throws TrainException if DB error
     */
    public int getLockedSeatsForTrain(long trainNo) throws TrainException;
    
    /**
     * Check if user has an active lock on a train
     * Prevents multiple concurrent locks by same user
     * 
     * @param trainNo Train number
     * @param mailId User email ID
     * @return true if active lock exists, false otherwise
     * @throws TrainException if DB error
     */
    public boolean hasActiveLock(long trainNo, String mailId) throws TrainException;
}
