package com.shashi.service;

import java.util.List;

import com.shashi.beans.HistoryBean;
import com.shashi.beans.TrainException;

public interface BookingService {

	public List<HistoryBean> getAllBookingsByCustomerId(String customerEmailId) throws TrainException;

	public HistoryBean createHistory(HistoryBean bookingDetails) throws TrainException;

	/**
	 * Atomic transactional booking with row-level locking.
	 * 
	 * Implements ACID properties:
	 * - Disables auto-commit
	 * - Locks train row using SELECT FOR UPDATE
	 * - Validates seat availability
	 * - Updates train seats and inserts booking history atomically
	 * - Commits on success, rolls back on failure
	 * 
	 * @param trainNo The train number to book
	 * @param requestedSeats Number of seats to book
	 * @param bookingDetails Booking information (user, fare, etc.)
	 * @return HistoryBean with transaction ID if successful
	 * @throws TrainException if booking fails (insufficient seats or DB error)
	 */
	public HistoryBean createTransactionalBooking(String trainNo, int requestedSeats, HistoryBean bookingDetails) throws TrainException;

}
