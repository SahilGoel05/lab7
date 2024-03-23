import java.sql.*;
import java.time.*;
import java.util.*;
import java.time.format.TextStyle;

public class RoomReservations {
    private String pw;
    public static void main(String[] args) {
        try {
            RoomReservations rr = new RoomReservations();
            rr.getPassword();
            rr.initializeDatabase();
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

    private void getPassword() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("\nPlease enter the password to your database: ");
        pw = scanner.nextLine();
        System.out.println();
    }

    private void fetchRoomsAndRates() throws SQLException {
        System.out.println("Fetching Rooms and Rates...");

        String roomDetailsQuery = "SELECT r.roomcode, r.roomname, r.beds, r.bedtype, r.maxocc, r.baseprice, r.decor, " +
                "(SELECT COUNT(*) FROM sgoel05.lab7_reservations WHERE Room = r.RoomCode AND CheckIn >= CURDATE() - INTERVAL 180 DAY) / 180.0 AS popularity_score, " +
                "(SELECT MIN(CheckIn) FROM sgoel05.lab7_reservations WHERE Room = r.RoomCode AND CheckIn > CURDATE()) AS nextAvailableCheckin, " +
                "(SELECT DATEDIFF(CheckOut, CheckIn) FROM sgoel05.lab7_reservations WHERE Room = r.RoomCode AND Checkout <= CURDATE() ORDER BY CheckOut DESC LIMIT 1) AS last_stay_length, " +
                "(SELECT CheckOut FROM sgoel05.lab7_reservations WHERE Room = r.RoomCode AND Checkout <= CURDATE() ORDER BY CheckOut DESC LIMIT 1) AS last_stay_checkout " +
                "FROM sgoel05.lab7_rooms r " +
                "ORDER BY popularity_score DESC, r.roomcode";

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                pw);
             PreparedStatement pstmt = conn.prepareStatement(roomDetailsQuery)) {

            System.out.format("%-10s %-30s %-5s %-10s %-7s %-10s %-15s %-20s %-16s %-20s %-20s%n", "RoomCode", "RoomName", "Beds", "BedType", "MaxOcc", "BasePrice", "Decor", "Popularity Score", "Next Check-in", "Last Stay Length", "Last Stay Checkout");
            System.out.println(String.join("", Collections.nCopies(160, "-")));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                System.out.format("%-10s %-30s %-5d %-10s %-7d $%-10.2f %-15s %-20.2f %-20s %-20d %-20s%n",
                        rs.getString("roomcode"),
                        rs.getString("roomname"),
                        rs.getInt("beds"),
                        rs.getString("bedtype"),
                        rs.getInt("maxocc"),
                        rs.getDouble("baseprice"),
                        rs.getString("decor"),
                        rs.getDouble("popularity_score"),
                        rs.getDate("nextAvailableCheckin") != null ? rs.getDate("nextAvailableCheckin").toString() : "N/A",
                        rs.wasNull() ? 0 : rs.getInt("last_stay_length"),
                        rs.getDate("last_stay_checkout") != null ? rs.getDate("last_stay_checkout").toString() : "N/A");
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }

    private void createReservation() throws SQLException {
        System.out.println("Enter reservation information:");

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                pw)) {
            Scanner scanner = new Scanner(System.in);

            System.out.print("\nFirst name: ");
            String firstName = scanner.nextLine();

            System.out.print("Last name: ");
            String lastName = scanner.nextLine();

            System.out.print("Room code (or 'Any' for no preference): ");
            String desiredRoomCode = scanner.nextLine().toUpperCase();

            System.out.print("Desired bed type (or 'Any' for no preference): ");
            String desiredBedType = scanner.nextLine();

            System.out.print("Check-in date (YYYY-MM-DD): ");
            LocalDate checkInDate = LocalDate.parse(scanner.nextLine());

            System.out.print("Check-out date (YYYY-MM-DD): ");
            LocalDate checkOutDate = LocalDate.parse(scanner.nextLine());

            System.out.print("Number of children: ");
            int childrenCount = Integer.parseInt(scanner.nextLine());

            System.out.print("Number of adults: ");
            int adultCount = Integer.parseInt(scanner.nextLine());

            List<Map<String, Object>> availableRooms = new ArrayList<>();

            String findAvailableRoomsQuery =
                    "SELECT RoomCode, roomname, bedType, basePrice, maxOcc FROM sgoel05.lab7_rooms r " +
                            "WHERE NOT EXISTS (" +
                            "  SELECT 1 FROM sgoel05.lab7_reservations res " +
                            "  WHERE res.Room = r.RoomCode AND " +
                            "  res.checkout > ? AND res.checkin < ? " +
                            ") " +
                            "AND (? = 'Any' OR bedType = ?) " +
                            "AND (? = 'Any' OR RoomCode = ?) " +
                            "AND maxOcc >= ?";


            try (PreparedStatement pstmt = conn.prepareStatement(findAvailableRoomsQuery)) {
                pstmt.setDate(1, java.sql.Date.valueOf(checkInDate));
                pstmt.setDate(2, java.sql.Date.valueOf(checkOutDate));
                pstmt.setString(3, desiredBedType);
                pstmt.setString(4, desiredBedType);
                pstmt.setString(5, desiredRoomCode);
                pstmt.setString(6, desiredRoomCode);
                pstmt.setInt(7, adultCount + childrenCount);

                ResultSet rs = pstmt.executeQuery();

                int index = 1;
                while (rs.next()) {
                    if (index == 1) {
                        System.out.println("\nRoom Options: ");
                        System.out.printf("%-3s %-10s %-30s %-10s %-15s %-15s %-15s %n",
                                "#", "Room Code", "Room Name", "Bed Type",
                                "Base Price", "Max Occupancy", "Availability Dates");
                    }
                    Map<String, Object> roomDetails = new HashMap<>();
                    roomDetails.put("RoomCode", rs.getString("RoomCode"));
                    roomDetails.put("RoomName", rs.getString("roomname"));
                    roomDetails.put("BedType", rs.getString("bedType"));
                    roomDetails.put("BasePrice", rs.getFloat("basePrice"));
                    roomDetails.put("MaxOcc", rs.getInt("maxOcc"));

                    availableRooms.add(roomDetails);

                    System.out.printf("%-3d %-10s %-30s %-10s $%-14.2f %-15d %s to %s %n",
                            index++,
                            rs.getString("RoomCode"),
                            rs.getString("roomname"),
                            rs.getString("bedType"),
                            rs.getFloat("basePrice"),
                            rs.getInt("maxOcc"),
                            checkInDate,
                            checkOutDate);
                }
            }

            if (availableRooms.isEmpty()) {
                String findSimilarRoomsQuery =
                        "SELECT r.RoomCode, r.roomname, r.bedType, r.basePrice, r.maxOcc, " +
                                "IFNULL((SELECT MIN(res.Checkin) FROM sgoel05.lab7_reservations res WHERE res.Room = r.RoomCode AND res.Checkin > ?), 'No upcoming reservations') AS NextAvailableFrom, " +
                                "IFNULL((SELECT MAX(res.Checkout) FROM sgoel05.lab7_reservations res WHERE res.Room = r.RoomCode AND res.Checkout < ?), 'No previous reservations') AS LastAvailableUntil " +
                                "FROM sgoel05.lab7_rooms r " +
                                "WHERE r.maxOcc >= ? AND NOT EXISTS ( " +
                                "SELECT 1 FROM sgoel05.lab7_reservations res WHERE res.Room = r.RoomCode AND res.Checkout > ? AND res.Checkin < ? " +
                                ") " +
                                "GROUP BY r.RoomCode, r.roomname, r.bedType, r.basePrice, r.maxOcc " +
                                "ORDER BY NextAvailableFrom, LastAvailableUntil " +
                                "LIMIT 5";

                int index = 1;
                try (PreparedStatement pstmt = conn.prepareStatement(findSimilarRoomsQuery)) {
                    pstmt.setDate(1, java.sql.Date.valueOf(checkOutDate));
                    pstmt.setDate(2, java.sql.Date.valueOf(checkInDate));
                    pstmt.setInt(3, adultCount + childrenCount);
                    pstmt.setDate(4, java.sql.Date.valueOf(checkInDate));
                    pstmt.setDate(5, java.sql.Date.valueOf(checkOutDate));
                    ResultSet rs = pstmt.executeQuery();


                    while (rs.next()) {
                        if (index == 1) {
                            System.out.println("\nNo exact matches found. Here are some similar rooms:");
                            System.out.printf("%-3s %-10s %-30s %-10s %-15s %-15s %-15s %n",
                                    "#", "Room Code", "Room Name", "Bed Type",
                                    "Base Price", "Max Occupancy", "Availability Dates");
                        }

                        Map<String, Object> roomDetails = new HashMap<>();
                        roomDetails.put("RoomCode", rs.getString("RoomCode"));
                        roomDetails.put("RoomName", rs.getString("roomname"));
                        roomDetails.put("BedType", rs.getString("bedType"));
                        roomDetails.put("BasePrice", rs.getFloat("basePrice"));
                        roomDetails.put("MaxOcc", rs.getInt("maxOcc"));

                        availableRooms.add(roomDetails);

                        System.out.printf("%-3d %-10s %-30s %-10s $%-14.2f %-15d %s to %s %n",
                                index++,
                                rs.getString("RoomCode"),
                                rs.getString("roomname"),
                                rs.getString("bedType"),
                                rs.getFloat("basePrice"),
                                rs.getInt("maxOcc"),
                                checkInDate,
                                checkOutDate);
                    }
                }
            }

            if(availableRooms.isEmpty()) {
                System.out.println("No matching/similar rooms available matching the criteria.");
                return;
            }

            System.out.print("Please enter the number of the room you wish to book, or '0' to cancel: ");
            int roomSelection = Integer.parseInt(scanner.nextLine());
            if (roomSelection == 0) {
                System.out.println("Reservation cancelled.");
                return;
            }

            if (roomSelection < 1 || roomSelection > availableRooms.size()) {
                System.out.println("Invalid selection. Please try again.");
                return;
            }

            Map<String, Object> selectedRoom = availableRooms.get(roomSelection - 1);
            String actualRoomCode = (String) selectedRoom.get("RoomCode");
            float basePrice = (Float) selectedRoom.get("BasePrice");

            String getMaxCodeSql = "SELECT MAX(CODE) AS maxCode FROM sgoel05.lab7_reservations";
            int maxCode = 0;

            try (PreparedStatement pstmt = conn.prepareStatement(getMaxCodeSql)) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    maxCode = rs.getInt("maxCode") + 1;
                }
            }

            String insertSql = "INSERT INTO sgoel05.lab7_reservations (CODE, Room, Checkin, Checkout, Rate, LastName, FirstName, Adults, Kids) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, maxCode);
                pstmt.setString(2, actualRoomCode);
                pstmt.setDate(3, java.sql.Date.valueOf(checkInDate));
                pstmt.setDate(4, java.sql.Date.valueOf(checkOutDate));
                pstmt.setFloat(5, basePrice);
                pstmt.setString(6, lastName);
                pstmt.setString(7, firstName);
                pstmt.setInt(8, adultCount);
                pstmt.setInt(9, childrenCount);
                pstmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("An error occurred during the reservation process: " + e.getMessage());
            }

            System.out.println("\nThank you, your reservation is confirmed!");
            System.out.println("Reservation Details:");
            System.out.println("First Name: " + firstName);
            System.out.println("Last Name: " + lastName);
            System.out.println("Room Code: " + actualRoomCode);
            System.out.println("Room Name: " + selectedRoom.get("RoomName"));
            System.out.println("Bed Type: " + selectedRoom.get("BedType"));
            System.out.println("Begin Date: " + checkInDate);
            System.out.println("End Date: " + checkOutDate);
            System.out.println("Adults: " + adultCount);
            System.out.println("Children: " + childrenCount);

            long weekdays = 0;
            long weekendDays = 0;

            for (LocalDate date = checkInDate; date.isBefore(checkOutDate); date = date.plusDays(1)) {
                DayOfWeek day = date.getDayOfWeek();
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    weekendDays++;
                } else {
                    weekdays++;
                }
            }

            double totalCost = weekdays * basePrice + weekendDays * basePrice * 1.1;
            System.out.printf("Total Cost: $%.2f\n", totalCost);
        }
    }

    private void cancelReservation() throws SQLException {
        System.out.println("Reservation Cancellation:");
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                pw)) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the reservation code to cancel: ");

            int reservationCode;
            try {
                reservationCode = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Input formatted incorrectly. Reservation cancellation aborted.");
                return;
            }

            String checkExistenceQuery = "SELECT COUNT(*) AS count FROM sgoel05.lab7_reservations WHERE code = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkExistenceQuery)) {
                pstmt.setInt(1, reservationCode);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next() && rs.getInt("count") == 0) {
                    System.out.println("No reservation found with code: " + reservationCode);
                    return;
                }
            }

            System.out.print("Confirm cancellation (y/n): ");
            String confirmation = scanner.nextLine();
            if (!confirmation.equalsIgnoreCase("y")) {
                System.out.println("Cancellation aborted.");
                return;
            }

            String deleteQuery = "DELETE FROM sgoel05.lab7_reservations WHERE code = ?";
            conn.setAutoCommit(false);
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
                pw)) {
            Scanner scanner = new Scanner(System.in);

            System.out.println("Search Reservations");
            System.out.print("Enter first name (partial allowed, leave empty for any): ");
            String firstName = scanner.nextLine();

            System.out.print("Enter last name (partial allowed, leave empty for any): ");
            String lastName = scanner.nextLine();

            System.out.print("Enter room code (partial allowed, leave empty for any): ");
            String roomCode = scanner.nextLine();

            System.out.print("Enter reservation code (partial allowed, leave empty for any): ");
            String reservationCode = scanner.nextLine();

            System.out.print("Enter check-in date range start (YYYY-MM-DD, leave empty for any): ");
            String startDate = scanner.nextLine();

            System.out.print("Enter check-in date range end (YYYY-MM-DD, leave empty for any): ");
            String endDate = scanner.nextLine();

            String query = "SELECT CODE, Room, CheckIn, Checkout, Rate, LastName, FirstName, Adults, Kids FROM sgoel05.lab7_reservations WHERE " +
                    "FirstName LIKE ? AND " +
                    "LastName LIKE ? " +
                    (roomCode.isEmpty() ? "" : "AND Room LIKE ? ") +
                    (reservationCode.isEmpty() ? "" : "AND CODE LIKE ? ") +
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

                System.out.format("%-10s %-10s %-15s %-15s %-12s %-12s %-10s %-10s %-10s%n",
                        "Code", "Room", "First Name", "Last Name", "Check-In", "Check-Out", "Rate", "Adults", "Kids");
                System.out.println(String.join("", Collections.nCopies(100, "-")));

                while (rs.next()) {
                    System.out.format("%-10s %-10s %-15s %-15s %-12s %-12s %-10.2f %-10d %-10d%n",
                            rs.getString("CODE"),
                            rs.getString("Room"),
                            rs.getString("FirstName"),
                            rs.getString("LastName"),
                            rs.getDate("CheckIn").toString(),
                            rs.getDate("CheckOut").toString(),
                            rs.getFloat("Rate"),
                            rs.getInt("Adults"),
                            rs.getInt("Kids"));
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
                pw)) {
            Map<String, Double[]> roomRevenues = new HashMap<>();

            String query = "SELECT room, MONTH(checkout) AS month, " +
                    "SUM(ROUND(rate * DATEDIFF(checkout, checkin), 2)) AS revenue " +
                    "FROM sgoel05.lab7_reservations " +
                    "GROUP BY room, MONTH(checkout) " +
                    "ORDER BY room, MONTH(checkout)";

            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    String room = rs.getString("room");
                    int monthIndex = rs.getInt("month") - 1;
                    double revenue = rs.getDouble("revenue");

                    roomRevenues.computeIfAbsent(room, k -> new Double[12]);
                    Double[] revenues = roomRevenues.get(room);

                    revenues[monthIndex] = (revenues[monthIndex] == null ? 0 : revenues[monthIndex]) + revenue;
                }
            }

            System.out.format("%-10s", "Room");
            for (int i = 1; i <= 12; i++) {
                System.out.format("%-12s", Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            }
            System.out.format("%-12s%n", "Total");

            System.out.println(String.join("", Collections.nCopies(154, "-")));

            Double[] totalRevenues = new Double[12];
            Arrays.fill(totalRevenues, 0.0);

            roomRevenues.forEach((room, revenues) -> {
                double roomTotal = 0;
                System.out.format("%-10s", room);
                for (int i = 0; i < revenues.length; i++) {
                    double monthRevenue = (revenues[i] == null) ? 0 : revenues[i];
                    System.out.format("%-12.2f", monthRevenue);
                    roomTotal += monthRevenue;
                    totalRevenues[i] += monthRevenue;
                }
                System.out.format("%-12.2f%n", roomTotal);
            });

            System.out.format("%-10s", "Total");
            double grandTotal = 0;
            for (double total : totalRevenues) {
                System.out.format("%-12.2f", total);
                grandTotal += total;
            }
            System.out.format("%-12.2f%n", grandTotal);
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }

    private void initializeDatabase() throws SQLException {
        String createRoomsTableSql =
                "CREATE TABLE IF NOT EXISTS sgoel05.lab7_rooms (" +
                        "RoomCode char(5) PRIMARY KEY," +
                        "RoomName varchar(30) NOT NULL," +
                        "Beds int NOT NULL," +
                        "bedType varchar(8) NOT NULL," +
                        "maxOcc int NOT NULL," +
                        "basePrice DECIMAL(6,2) NOT NULL," +
                        "decor varchar(20) NOT NULL," +
                        "UNIQUE (RoomName)" +
                        ");";

        String createReservationsTableSql =
                "CREATE TABLE IF NOT EXISTS sgoel05.lab7_reservations (" +
                        "CODE int PRIMARY KEY," +
                        "Room char(5) NOT NULL," +
                        "CheckIn date NOT NULL," +
                        "Checkout date NOT NULL," +
                        "Rate DECIMAL(6,2) NOT NULL," +
                        "LastName varchar(15) NOT NULL," +
                        "FirstName varchar(15) NOT NULL," +
                        "Adults int NOT NULL," +
                        "Kids int NOT NULL," +
                        "FOREIGN KEY (Room) REFERENCES sgoel05.lab7_rooms (RoomCode)" +
                        ");";

        String[] checkDataSql = {
                "SELECT EXISTS (SELECT 1 FROM sgoel05.lab7_rooms)",
                "SELECT EXISTS (SELECT 1 FROM sgoel05.lab7_reservations)"
        };

        String[] initialDataSql = {
                "INSERT INTO sgoel05.lab7_rooms SELECT * FROM INN.rooms;",
                "INSERT INTO sgoel05.lab7_reservations SELECT CODE, Room, DATE_ADD(CheckIn, INTERVAL 162 MONTH), DATE_ADD(Checkout, INTERVAL 162 MONTH), Rate, LastName, FirstName, Adults, Kids FROM INN.reservations;"
        };

        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                pw)) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createRoomsTableSql);
                stmt.execute(createReservationsTableSql);
            }

            for (int i = 0; i < checkDataSql.length; i++) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(checkDataSql[i]);
                    if (rs.next()) {
                        boolean exists = rs.getInt(1) > 0;
                        if (!exists) {
                            stmt.execute(initialDataSql[i]);
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("Error checking data or inserting initial data: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw e;
        }
    }

}
