package com.shashi.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.shashi.beans.SeatLockBean;
import com.shashi.beans.TrainException;
import com.shashi.constant.ResponseCode;
import com.shashi.service.SeatLockService;
import com.shashi.utility.DBUtil;

/**
 * SeatLockServiceImpl implements temporary seat locking for booking process
 * 
 * Workflow:
 * 1. User clicks "Book Now" → acquireSeatLock() creates 5-min reservation
 * 2. During checkout → available_seats = total_seats - locked_seats
 * 3. Booking confirmed → releaseSeatLock() finalizes as history record
 * 4. Lock expires → cleanupExpiredLocks() auto-frees locks
 * 
 * Benefits:
 * - Prevents race conditions during booking
 * - Shows accurate available seats after locks
 * - Auto-recovery from abandoned bookings
 */
public class SeatLockServiceImpl implements SeatLockService {

    @Override
    public SeatLockBean acquireSeatLock(long trainNo, String mailId, int seatsToLock) throws TrainException {
        SeatLockBean seatLock = null;
        Connection con = null;
        PreparedStatement lockCheckStmt = null;
        PreparedStatement insertLockStmt = null;
        ResultSet rs = null;

        try {
            con = DBUtil.getConnection();
            
            // First, check if user already has an active lock on this train
            String checkQuery = "SELECT * FROM SEAT_LOCK WHERE TR_NO = ? AND MAILID = ? AND STATUS = 'ACTIVE' AND EXPIRES_AT > CURRENT_TIMESTAMP";
            lockCheckStmt = con.prepareStatement(checkQuery);
            lockCheckStmt.setLong(1, trainNo);
            lockCheckStmt.setString(2, mailId);
            rs = lockCheckStmt.executeQuery();
            
            if (rs.next()) {
                rs.close();
                lockCheckStmt.close();
                throw new TrainException("User already has an active lock on this train. Please complete previous booking.");
            }
            
            rs.close();
            lockCheckStmt.close();
            
            // Create new seat lock
            String insertQuery = "INSERT INTO SEAT_LOCK (TR_NO, MAILID, SEATS_LOCKED, LOCK_TIME, EXPIRES_AT, STATUS) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 minutes', 'ACTIVE')";
            insertLockStmt = con.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
            insertLockStmt.setLong(1, trainNo);
            insertLockStmt.setString(2, mailId);
            insertLockStmt.setInt(3, seatsToLock);
            
            int insertCount = insertLockStmt.executeUpdate();
            
            if (insertCount > 0) {
                rs = insertLockStmt.getGeneratedKeys();
                if (rs.next()) {
                    int lockId = rs.getInt(1);
                    seatLock = new SeatLockBean(trainNo, mailId, seatsToLock);
                    seatLock.setLockId(lockId);
                    seatLock.setStatus("ACTIVE");
                    System.out.println("Seat lock created: " + seatLock);
                }
            } else {
                throw new TrainException(ResponseCode.FAILURE.toString() + " : Failed to create seat lock");
            }
            
        } catch (SQLException e) {
            System.err.println("Database error while acquiring seat lock: " + e.getMessage());
            throw new TrainException("Failed to acquire seat lock: " + e.getMessage());
        } finally {
            try {
                if (rs != null && !rs.isClosed()) rs.close();
                if (lockCheckStmt != null && !lockCheckStmt.isClosed()) lockCheckStmt.close();
                if (insertLockStmt != null && !insertLockStmt.isClosed()) insertLockStmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
        
        return seatLock;
    }

    @Override
    public boolean releaseSeatLock(int lockId) throws TrainException {
        Connection con = null;
        PreparedStatement updateStmt = null;
        
        try {
            con = DBUtil.getConnection();
            
            // Update lock status to RELEASED
            String updateQuery = "UPDATE SEAT_LOCK SET STATUS = 'RELEASED' WHERE LOCK_ID = ? AND STATUS = 'ACTIVE'";
            updateStmt = con.prepareStatement(updateQuery);
            updateStmt.setInt(1, lockId);
            
            int updateCount = updateStmt.executeUpdate();
            
            if (updateCount > 0) {
                System.out.println("Seat lock released: Lock ID " + lockId);
                return true;
            } else {
                System.err.println("Failed to release lock " + lockId + " (may already be expired or released)");
                return false;
            }
            
        } catch (SQLException e) {
            System.err.println("Database error while releasing seat lock: " + e.getMessage());
            throw new TrainException("Failed to release seat lock: " + e.getMessage());
        } finally {
            try {
                if (updateStmt != null && !updateStmt.isClosed()) updateStmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    @Override
    public int cleanupExpiredLocks() throws TrainException {
        Connection con = null;
        PreparedStatement cleanupStmt = null;
        
        try {
            con = DBUtil.getConnection();
            
            // Mark expired locks as EXPIRED
            String cleanupQuery = "UPDATE SEAT_LOCK SET STATUS = 'EXPIRED' WHERE STATUS = 'ACTIVE' AND EXPIRES_AT <= CURRENT_TIMESTAMP";
            cleanupStmt = con.prepareStatement(cleanupQuery);
            
            int expiredCount = cleanupStmt.executeUpdate();
            
            if (expiredCount > 0) {
                System.out.println("Cleanup: Marked " + expiredCount + " expired locks as EXPIRED");
            }
            
            return expiredCount;
            
        } catch (SQLException e) {
            System.err.println("Database error during lock cleanup: " + e.getMessage());
            throw new TrainException("Failed to cleanup expired locks: " + e.getMessage());
        } finally {
            try {
                if (cleanupStmt != null && !cleanupStmt.isClosed()) cleanupStmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    @Override
    public int getLockedSeatsForTrain(long trainNo) throws TrainException {
        Connection con = null;
        PreparedStatement queryStmt = null;
        ResultSet rs = null;
        int totalLockedSeats = 0;
        
        try {
            con = DBUtil.getConnection();
            
            // Sum all active and non-expired locked seats for this train
            String query = "SELECT COALESCE(SUM(SEATS_LOCKED), 0) as TOTAL_LOCKED " +
                    "FROM SEAT_LOCK " +
                    "WHERE TR_NO = ? AND STATUS = 'ACTIVE' AND EXPIRES_AT > CURRENT_TIMESTAMP";
            queryStmt = con.prepareStatement(query);
            queryStmt.setLong(1, trainNo);
            rs = queryStmt.executeQuery();
            
            if (rs.next()) {
                totalLockedSeats = rs.getInt("TOTAL_LOCKED");
                System.out.println("Total locked seats for train " + trainNo + ": " + totalLockedSeats);
            }
            
        } catch (SQLException e) {
            System.err.println("Database error while getting locked seats: " + e.getMessage());
            throw new TrainException("Failed to get locked seats: " + e.getMessage());
        } finally {
            try {
                if (rs != null && !rs.isClosed()) rs.close();
                if (queryStmt != null && !queryStmt.isClosed()) queryStmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
        
        return totalLockedSeats;
    }

    @Override
    public boolean hasActiveLock(long trainNo, String mailId) throws TrainException {
        Connection con = null;
        PreparedStatement queryStmt = null;
        ResultSet rs = null;
        
        try {
            con = DBUtil.getConnection();
            
            String query = "SELECT COUNT(*) as LOCK_COUNT FROM SEAT_LOCK " +
                    "WHERE TR_NO = ? AND MAILID = ? AND STATUS = 'ACTIVE' AND EXPIRES_AT > CURRENT_TIMESTAMP";
            queryStmt = con.prepareStatement(query);
            queryStmt.setLong(1, trainNo);
            queryStmt.setString(2, mailId);
            rs = queryStmt.executeQuery();
            
            if (rs.next()) {
                int lockCount = rs.getInt("LOCK_COUNT");
                boolean hasLock = lockCount > 0;
                System.out.println("User " + mailId + " has active lock on train " + trainNo + ": " + hasLock);
                return hasLock;
            }
            
        } catch (SQLException e) {
            System.err.println("Database error while checking active lock: " + e.getMessage());
            throw new TrainException("Failed to check active lock: " + e.getMessage());
        } finally {
            try {
                if (rs != null && !rs.isClosed()) rs.close();
                if (queryStmt != null && !queryStmt.isClosed()) queryStmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
        
        return false;
    }
}
