package com.shashi.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shashi.beans.HistoryBean;
import com.shashi.beans.TrainException;
import com.shashi.constant.ResponseCode;
import com.shashi.service.BookingService;
import com.shashi.utility.DBUtil;

//Service Implementaion class for booking details of the ticket
//Creates the booking history and save to database
public class BookingServiceImpl implements BookingService {

	@Override
	public List<HistoryBean> getAllBookingsByCustomerId(String customerEmailId) throws TrainException {
		List<HistoryBean> transactions = null;
		String query = "SELECT * FROM HISTORY WHERE MAILID=?";
		try {
			Connection con = DBUtil.getConnection();
			PreparedStatement ps = con.prepareStatement(query);
			ps.setString(1, customerEmailId);
			ResultSet rs = ps.executeQuery();
			transactions = new ArrayList<HistoryBean>();
			while (rs.next()) {
				HistoryBean transaction = new HistoryBean();
				transaction.setTransId(rs.getString("transid"));
				transaction.setFrom_stn(rs.getString("from_stn"));
				transaction.setTo_stn(rs.getString("to_stn"));
				transaction.setDate(rs.getString("date"));
				transaction.setMailId(rs.getString("mailid"));
				transaction.setSeats(rs.getInt("seats"));
				transaction.setAmount(rs.getDouble("amount"));
				transaction.setTr_no(rs.getString("tr_no"));
				transactions.add(transaction);
			}

			ps.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
			throw new TrainException(e.getMessage());
		}
		return transactions;
	}

	@Override
	public HistoryBean createHistory(HistoryBean details) throws TrainException {
		HistoryBean history = null;
		String query = "INSERT INTO HISTORY VALUES(?,?,?,?,?,?,?,?)";
		try {
			Connection con = DBUtil.getConnection();
			PreparedStatement ps = con.prepareStatement(query);
			String transactionId = UUID.randomUUID().toString();
			ps.setString(1, transactionId);
			ps.setString(2, details.getMailId());
			ps.setString(3, details.getTr_no());
			ps.setString(4, details.getDate());
			ps.setString(5, details.getFrom_stn());
			ps.setString(6, details.getTo_stn());
			ps.setLong(7, details.getSeats());
			ps.setDouble(8, details.getAmount());
			int response = ps.executeUpdate();
			if (response > 0) {
				history = (HistoryBean) details;
				history.setTransId(transactionId);
			} else {
				throw new TrainException(ResponseCode.INTERNAL_SERVER_ERROR);
			}
			ps.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
			throw new TrainException(e.getMessage());
		}
		return history;
	}

	/**
	 * STEP 1-7: Atomic transactional booking with row-level locking
	 * 
	 * This method implements ACID properties to prevent race conditions:
	 * STEP 1: Disable auto-commit for transaction control
	 * STEP 2: Lock train row using SELECT FOR UPDATE (prevents concurrent updates)
	 * STEP 3: Validate seat availability
	 * STEP 4: Decrement train seats
	 * STEP 5: Insert booking history record
	 * STEP 6: Commit transaction on success
	 * STEP 7: Rollback on failure
	 * 
	 * This ensures:
	 * - Atomic booking (all-or-nothing)
	 * - Transaction consistency (data integrity)
	 * - Concurrency control (no overbooking)
	 * - Race-condition prevention (row-level locks)
	 */
	@Override
	public HistoryBean createTransactionalBooking(String trainNo, int requestedSeats, HistoryBean bookingDetails) throws TrainException {
		HistoryBean transaction = null;
		Connection con = null;
		PreparedStatement lockStmt = null;
		PreparedStatement updateStmt = null;
		PreparedStatement historyStmt = null;
		ResultSet rs = null;

		try {
			// STEP 1: Get connection and disable auto-commit for transaction control
			con = DBUtil.getConnection();
			con.setAutoCommit(false);
			System.out.println("Transaction started: autoCommit=false");

			// STEP 2: Lock train row - SELECT FOR UPDATE prevents concurrent modifications
			String lockQuery = "SELECT SEATS FROM TRAIN WHERE TR_NO = ? FOR UPDATE";
			lockStmt = con.prepareStatement(lockQuery);
			lockStmt.setString(1, trainNo);
			rs = lockStmt.executeQuery();

			if (!rs.next()) {
				con.rollback();
				con.setAutoCommit(true);
				throw new TrainException(ResponseCode.FAILURE.toString() + " : Train not found");
			}

			// STEP 3: Validate seat availability
			int availableSeats = rs.getInt("SEATS");
			System.out.println("Train locked. Available seats: " + availableSeats + ", Requested: " + requestedSeats);

			if (availableSeats < requestedSeats) {
				// Rollback if insufficient seats
				con.rollback();
				con.setAutoCommit(true);
				rs.close();
				lockStmt.close();
				throw new TrainException(ResponseCode.FAILURE.toString() + " : Only " + availableSeats + " seats available");
			}

			rs.close();
			lockStmt.close();

			// STEP 4: Decrement train seats within same transaction
			String updateQuery = "UPDATE TRAIN SET SEATS = SEATS - ? WHERE TR_NO = ?";
			updateStmt = con.prepareStatement(updateQuery);
			updateStmt.setInt(1, requestedSeats);
			updateStmt.setString(2, trainNo);
			int updateCount = updateStmt.executeUpdate();

			if (updateCount <= 0) {
				con.rollback();
				con.setAutoCommit(true);
				updateStmt.close();
				throw new TrainException(ResponseCode.FAILURE.toString() + " : Failed to update train seats");
			}

			System.out.println("Train seats updated. Rows affected: " + updateCount);
			updateStmt.close();

			// STEP 5: Insert booking history within same transaction
			String historyQuery = "INSERT INTO HISTORY VALUES(?,?,?,?,?,?,?,?)";
			historyStmt = con.prepareStatement(historyQuery);
			String transactionId = UUID.randomUUID().toString();
			historyStmt.setString(1, transactionId);
			historyStmt.setString(2, bookingDetails.getMailId());
			historyStmt.setString(3, bookingDetails.getTr_no());
			historyStmt.setString(4, bookingDetails.getDate());
			historyStmt.setString(5, bookingDetails.getFrom_stn());
			historyStmt.setString(6, bookingDetails.getTo_stn());
			historyStmt.setLong(7, bookingDetails.getSeats());
			historyStmt.setDouble(8, bookingDetails.getAmount());
			int historyCount = historyStmt.executeUpdate();

			if (historyCount <= 0) {
				con.rollback();
				con.setAutoCommit(true);
				historyStmt.close();
				throw new TrainException(ResponseCode.FAILURE.toString() + " : Failed to create booking record");
			}

			System.out.println("Booking history inserted. Rows affected: " + historyCount);
			historyStmt.close();

			// STEP 6: Commit transaction - makes all changes permanent
			con.commit();
			con.setAutoCommit(true);
			System.out.println("Transaction committed successfully. TransactionID: " + transactionId);

			// Return booking details with transaction ID
			transaction = (HistoryBean) bookingDetails;
			transaction.setTransId(transactionId);

		} catch (SQLException e) {
			// STEP 7: Rollback on any database error
			if (con != null) {
				try {
					System.err.println("Error during booking transaction: " + e.getMessage() + ". Rolling back...");
					con.rollback();
					con.setAutoCommit(true);
				} catch (SQLException rollbackEx) {
					System.err.println("Rollback failed: " + rollbackEx.getMessage());
				}
			}
			throw new TrainException("Booking transaction failed: " + e.getMessage());

		} catch (TrainException te) {
			// Rollback on business logic errors (e.g., insufficient seats)
			if (con != null) {
				try {
					System.err.println("Business logic error: " + te.getMessage() + ". Rolling back...");
					con.rollback();
					con.setAutoCommit(true);
				} catch (SQLException rollbackEx) {
					System.err.println("Rollback failed: " + rollbackEx.getMessage());
				}
			}
			throw te;

		} finally {
			// Cleanup resources
			try {
				if (rs != null && !rs.isClosed())
					rs.close();
				if (lockStmt != null && !lockStmt.isClosed())
					lockStmt.close();
				if (updateStmt != null && !updateStmt.isClosed())
					updateStmt.close();
				if (historyStmt != null && !historyStmt.isClosed())
					historyStmt.close();
			} catch (SQLException e) {
				System.err.println("Error closing resources: " + e.getMessage());
			}
		}

		return transaction;
	}

}
