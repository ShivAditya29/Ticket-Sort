package com.shashi.beans;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SeatLockBean represents a temporary seat lock/reservation
 * Seats are locked for 5 minutes during the booking process
 * Prevents double booking and ensures seat availability
 */
@SuppressWarnings("serial")
public class SeatLockBean implements Serializable {
    
    private int lockId;
    private long trainNo;
    private String mailId;
    private int seatsLocked;
    private LocalDateTime lockTime;
    private LocalDateTime expiresAt;
    private String status; // ACTIVE, EXPIRED, RELEASED
    
    public SeatLockBean() {
    }
    
    public SeatLockBean(long trainNo, String mailId, int seatsLocked) {
        this.trainNo = trainNo;
        this.mailId = mailId;
        this.seatsLocked = seatsLocked;
        this.lockTime = LocalDateTime.now();
        this.expiresAt = this.lockTime.plusMinutes(5); // 5-minute expiry
        this.status = "ACTIVE";
    }
    
    // Getters and Setters
    public int getLockId() {
        return lockId;
    }
    
    public void setLockId(int lockId) {
        this.lockId = lockId;
    }
    
    public long getTrainNo() {
        return trainNo;
    }
    
    public void setTrainNo(long trainNo) {
        this.trainNo = trainNo;
    }
    
    public String getMailId() {
        return mailId;
    }
    
    public void setMailId(String mailId) {
        this.mailId = mailId;
    }
    
    public int getSeatsLocked() {
        return seatsLocked;
    }
    
    public void setSeatsLocked(int seatsLocked) {
        this.seatsLocked = seatsLocked;
    }
    
    public LocalDateTime getLockTime() {
        return lockTime;
    }
    
    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Check if lock has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if lock is still active
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status) && !isExpired();
    }
    
    @Override
    public String toString() {
        return "SeatLockBean{" +
                "lockId=" + lockId +
                ", trainNo=" + trainNo +
                ", mailId='" + mailId + '\'' +
                ", seatsLocked=" + seatsLocked +
                ", lockTime=" + lockTime +
                ", expiresAt=" + expiresAt +
                ", status='" + status + '\'' +
                '}';
    }
}
