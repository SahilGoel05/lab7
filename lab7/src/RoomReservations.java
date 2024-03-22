import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Scanner;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RoomReservations {
    private int currCode = 7;
    public static void main(String[] args) {
        try {
            RoomReservations rr = new RoomReservations();
            int demoNum = Integer.parseInt(args[0]);

            switch (demoNum) {
                case 1:
                    rr.fetchRoomsAndRates();
                    break;
                case 2:
                    rr.createReservation();
                    break;
                case 3:
                    rr.cancelReservation();
                    break;
                case 4:
                    rr.searchReservations();
                    break;
                case 5:
                    rr.generateRevenueSummary();
                    break;
            }
        } catch (SQLException e) {
            System.err.println("SQLException: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println("Exception: " + e2.getMessage());
        }
    }

    private void fetchRoomsAndRates() throws SQLException {
        System.out.println("Fetching Rooms and Rates...");

        String roomsQuery = "SELECT roomcode, roomname, beds, bedtype, maxocc, baseprice, decor FROM lab7_rooms";
        String availabilityQuery =
                "SELECT room, MIN(checkin) AS nextAvailableCheckin " +
                "FROM lab7_reservations " +
                "WHERE checkin > CURRENT_DATE " +
                "GROUP BY room";

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            Map<String, LocalDate> nextAvailableCheckinMap = new HashMap<>();
            Map<String, String> roomDetailsMap = new HashMap<>();

            // Fetch next available check-in dates for rooms
            try (PreparedStatement pstmt = conn.prepareStatement(availabilityQuery)) {
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    nextAvailableCheckinMap.put(rs.getString("room"), rs.getDate("nextAvailableCheckin").toLocalDate());
                }
            }

            // Fetch room details
            try (PreparedStatement pstmt = conn.prepareStatement(roomsQuery)) {
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String roomCode = rs.getString("roomcode");
                    String roomDetails = String.format("%s, %s, %d, %s, %d, %.2f, %s, Next available check-in: %s",
                            rs.getString("roomname"), roomCode, rs.getInt("beds"),
                            rs.getString("bedtype"), rs.getInt("maxocc"),
                            rs.getDouble("baseprice"), rs.getString("decor"),
                            nextAvailableCheckinMap.getOrDefault(roomCode, LocalDate.now()));
                    roomDetailsMap.put(roomCode, roomDetails);
                }

                // Display room details
                roomDetailsMap.values().forEach(System.out::println);
            }
        }
    }

    private void createReservation() throws SQLException {
        System.out.println("Enter reservation information:");

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            Scanner scanner = new Scanner(System.in);

            System.out.print("\nFirst name: ");
            String firstName = scanner.nextLine();

            System.out.print("Last name: ");
            String lastName = scanner.nextLine();

            System.out.print("Room code (or 'Any' for no preference): ");
            String roomCode = scanner.nextLine();

            System.out.print("Check-in date (YYYY-MM-DD): ");
            LocalDate checkInDate = LocalDate.parse(scanner.nextLine());

            System.out.print("Check-out date (YYYY-MM-DD): ");
            LocalDate checkOutDate = LocalDate.parse(scanner.nextLine());

            System.out.print("Number of children: ");
            int childrenCount = Integer.parseInt(scanner.nextLine());

            System.out.print("Number of adults: ");
            int adultCount = Integer.parseInt(scanner.nextLine());

            // Check for date conflicts
            String dateConflictQuery = "SELECT 1 FROM lab7_reservations WHERE room = ? AND (checkin <= ?) AND (checkout >= ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(dateConflictQuery)) {
                pstmt.setString(1, roomCode);
                pstmt.setDate(2, java.sql.Date.valueOf(checkOutDate));
                pstmt.setDate(3, java.sql.Date.valueOf(checkInDate));
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    System.out.println("Date conflict detected. Reservation cannot be made.");
                    return;
                }
            }

            // Validate room occupancy
            String roomQuery = "SELECT roomname, bedtype, maxocc, baseprice FROM lab7_rooms WHERE RoomCode = ?";
            float basePrice;
            String roomName, bedType;
            try (PreparedStatement pstmt = conn.prepareStatement(roomQuery)) {
                pstmt.setString(1, roomCode);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    System.out.println("Room does not exist.");
                    return;
                }
                int maxOcc = rs.getInt("maxocc");
                if (adultCount + childrenCount > maxOcc) {
                    System.out.println("Occupancy limit exceeded. Reservation cannot be made.");
                    return;
                }
                basePrice = rs.getFloat("baseprice");
                roomName = rs.getString("roomname");
                bedType = rs.getString("bedtype");
            }

            // Insert new reservation
            String insertSql = "INSERT INTO lab7_reservations (Room, Checkin, Checkout, Rate, LastName, FirstName, Adults, Kids) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, roomCode);
                pstmt.setDate(2, java.sql.Date.valueOf(checkInDate));
                pstmt.setDate(3, java.sql.Date.valueOf(checkOutDate));
                pstmt.setFloat(4, basePrice); // Consider dynamic pricing here
                pstmt.setString(5, lastName);
                pstmt.setString(6, firstName);
                pstmt.setInt(7, adultCount);
                pstmt.setInt(8, childrenCount);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating reservation failed, no rows affected.");
                }
                System.out.println("Reservation successfully created.");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            // Additional error handling as needed
        }
    }

    private void cancelReservation() throws SQLException {
        System.out.println("Reservation Cancellation:");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the reservation code to cancel: ");

            int reservationCode;
            try {
                reservationCode = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Input formatted incorrectly. Reservation cancellation aborted.");
                return;
            }

            // Check if the reservation exists
            String checkExistenceQuery = "SELECT COUNT(*) AS count FROM lab7_reservations WHERE code = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkExistenceQuery)) {
                pstmt.setInt(1, reservationCode);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next() && rs.getInt("count") == 0) {
                    System.out.println("No reservation found with code: " + reservationCode);
                    return;
                }
            }

            // Confirm cancellation
            System.out.print("Confirm cancellation (y/n): ");
            String confirmation = scanner.nextLine();
            if (!confirmation.equalsIgnoreCase("y")) {
                System.out.println("Cancellation aborted.");
                return;
            }

            // Perform cancellation
            String deleteQuery = "DELETE FROM lab7_reservations WHERE code = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, reservationCode);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Reservation with code " + reservationCode + " successfully cancelled.");
                    conn.commit();
                } else {
                    System.out.println("Failed to cancel the reservation. Please try again.");
                    conn.rollback();
                }
            } catch (SQLException e) {
                System.out.println("An error occurred during cancellation: " + e.getMessage());
                conn.rollback();
            }
        } catch (SQLException e) {
            System.out.println("Failed to connect to the database: " + e.getMessage());
        }
    }

    private void searchReservations() throws SQLException {
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            Scanner scanner = new Scanner(System.in);

            System.out.println("Search Reservations");
            System.out.print("Enter first name (partial allowed, leave empty for any): ");
            String firstName = scanner.nextLine();

            System.out.print("Enter last name (partial allowed, leave empty for any): ");
            String lastName = scanner.nextLine();

            System.out.print("Enter room code (leave empty for any): ");
            String roomCode = scanner.nextLine();

            System.out.print("Enter reservation code (leave empty for any): ");
            String reservationCode = scanner.nextLine();

            System.out.print("Enter check-in date range start (YYYY-MM-DD, leave empty for any): ");
            String startDate = scanner.nextLine();

            System.out.print("Enter check-in date range end (YYYY-MM-DD, leave empty for any): ");
            String endDate = scanner.nextLine();

            String query = "SELECT * FROM lab7_reservations WHERE " +
                    "FirstName LIKE ? AND " +
                    "LastName LIKE ? " +
                    (roomCode.isEmpty() ? "" : "AND Room = ? ") +
                    (reservationCode.isEmpty() ? "" : "AND CODE = ? ") +
                    (startDate.isEmpty() ? "" : "AND CheckIn >= ? ") +
                    (endDate.isEmpty() ? "" : "AND CheckIn <= ? ");

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                int paramIndex = 1;
                pstmt.setString(paramIndex++, "%" + firstName + "%");
                pstmt.setString(paramIndex++, "%" + lastName + "%");

                if (!roomCode.isEmpty()) {
                    pstmt.setString(paramIndex++, roomCode);
                }
                if (!reservationCode.isEmpty()) {
                    pstmt.setString(paramIndex++, reservationCode);
                }
                if (!startDate.isEmpty()) {
                    pstmt.setDate(paramIndex++, java.sql.Date.valueOf(startDate));
                }
                if (!endDate.isEmpty()) {
                    pstmt.setDate(paramIndex++, java.sql.Date.valueOf(endDate));
                }

                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    // Assuming you want to print some details here
                    System.out.format("Reservation Code: %s, Room: %s, Name: %s %s, Check-In: %s, Check-Out: %s\n",
                            rs.getString("CODE"), rs.getString("Room"), rs.getString("FirstName"), rs.getString("LastName"),
                            rs.getDate("CheckIn").toString(), rs.getDate("CheckOut").toString());
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }



    private void generateRevenueSummary() throws SQLException {
        System.out.println("Revenue Summary:");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            Map<String, Double[]> roomRevenues = new HashMap<>();

            String query = "SELECT room, MONTH(checkout) AS month, " +
                    "SUM(ROUND(rate * DATEDIFF(checkout, checkin), 2)) AS revenue " +
                    "FROM lab7_reservations " +
                    "GROUP BY room, MONTH(checkout) " +
                    "ORDER BY room, MONTH(checkout)";

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    String room = rs.getString("room");
                    int monthIndex = rs.getInt("month") - 1; // Array index adjustment
                    double revenue = rs.getDouble("revenue");

                    roomRevenues.computeIfAbsent(room, k -> new Double[13]);
                    Double[] revenues = roomRevenues.get(room);

                    revenues[monthIndex] = (revenues[monthIndex] == null ? 0 : revenues[monthIndex]) + revenue;
                    revenues[12] = (revenues[12] == null ? 0 : revenues[12]) + revenue; // Total revenue
                }
            }

            Double[] totalRevenues = new Double[13];
            Arrays.fill(totalRevenues, 0.0);

            roomRevenues.forEach((room, revenues) -> {
                System.out.format("%s", room);
                for (int i = 0; i < revenues.length; i++) {
                    System.out.format(" $%.2f", revenues[i] == null ? 0 : revenues[i]);
                    totalRevenues[i] += (revenues[i] == null ? 0 : revenues[i]);
                }
                System.out.println();
            });

            System.out.print("Totals");
            for (double total : totalRevenues) {
                System.out.format(" $%.2f", total);
            }
            System.out.println();
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }

}
