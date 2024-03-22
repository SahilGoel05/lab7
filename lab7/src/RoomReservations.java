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

/*
Introductory JDBC examples based loosely on the BAKERY dataset from CSC 365 labs.

-- MySQL setup:
drop table if exists hp_goods, hp_customers, hp_items, hp_receipts;
create table hp_goods as select * from BAKERY.goods;
create table hp_customers as select * from BAKERY.customers;
create table hp_items as select * from BAKERY.items;
create table hp_receipts as select * from BAKERY.receipts;

grant all on amigler.hp_goods to hasty@'%';
grant all on amigler.hp_customers to hasty@'%';
grant all on amigler.hp_items to hasty@'%';
grant all on amigler.hp_receipts to hasty@'%';
 */
public class RoomReservations {
    private int currCode = 7;
    public static void main(String[] args) {
        try {
            RoomReservations rr = new RoomReservations();
            int demoNum = Integer.parseInt(args[0]);

            switch (demoNum) {
                case 1:
                    rr.RoomsAndRates();
                    break;
                case 2:
                    rr.Reservations();
                    break;
                case 3:
                    rr.ReservationCancellation();
                    break;
                case 4:
                   // rr.demo4();
                    break;
                case 5:
                    rr.RevenueSummary();
                    break;
            }

        } catch (SQLException e) {
            System.err.println("SQLException: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println("Exception: " + e2.getMessage());
        }
    }

	private void RoomsAndRates() throws SQLException {
		System.out.println("in RoomsAndRates");
		try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
				System.getenv("HP_JDBC_USER"),
				System.getenv("HP_JDBC_PW"))) {
			HashMap<String, String> roomInfoMap = new HashMap<>();
			HashMap<String, LocalDate> nextAvailCheckinMap = new HashMap<>();

			try (Statement nextAvailCheckinStmt = conn.createStatement()) {
				ResultSet rs = nextAvailCheckinStmt.executeQuery(
                        "select roomcode, min(checkin) as checkin " +
						"from sgoel05.lab7_rooms rooms " +
						"join sgoel05.lab7_reservations reservations on reservations.room = rooms.roomcode " +
						"where checkin > CURRENT_DATE " +
						"group by roomcode");
				while (rs.next()) {
					nextAvailCheckinMap.put(rs.getString("roomcode"), rs.getDate("checkin").toLocalDate());
				}
			}

			try (Statement roomInfoStmt = conn.createStatement()) {
				ResultSet rs = roomInfoStmt.executeQuery(
                        "select roomcode, roomname, beds, bedtype, maxocc, baseprice, decor " +
						"from sgoel05.lab7_rooms");
				while (rs.next()) {
					String room = rs.getString("roomcode");
					String roominfo = rs.getString("roomname") + ", " + room + ", " + rs.getInt("beds") +
							", " + rs.getString("bedtype") + ", " + rs.getInt("maxOcc") + ", " + rs.getDouble("baseprice") +
							", " + rs.getString("decor") + ", " + (nextAvailCheckinMap.containsKey(room) ? nextAvailCheckinMap.get(room) : "None");
					roomInfoMap.put(room, roominfo);
				}

				for(Map.Entry<String, String> e: roomInfoMap.entrySet()){
					System.out.println(e.getValue());
				}
			}
		}
	}

    private void Reservations() throws SQLException {
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {

            String firstname = "";
            String lastname = "";
            String roomcode = "";
            LocalDate newCheckin = LocalDate.parse("2000-01-01");
            LocalDate newCheckout = LocalDate.parse("2000-01-01");
            int children = 0;
            int adults = 0;

            try {
                Scanner scanner = new Scanner(System.in);
                System.out.println("Enter reservation info: ");
                System.out.println("\tFirst name: ");
                firstname = scanner.nextLine();
                System.out.println("\tLast name: ");
                lastname = scanner.nextLine();
                System.out.println("\tRoom code: ");
                roomcode = scanner.nextLine();
                System.out.println("\tCheckin date: ");
                newCheckin = LocalDate.parse(scanner.nextLine());
                System.out.println("\tCheckout date: ");
                newCheckout = LocalDate.parse(scanner.nextLine());
                System.out.println("\tNumber of children: ");
                children = Integer.parseInt(scanner.nextLine());
                System.out.println("\tNumber of adults: ");
                adults = Integer.parseInt(scanner.nextLine());
            }
            catch (Exception e){
                System.out.println("Input formatted incorrectly. Reservation cancelled.");
                return;
            }

            float basePrice;
            String roomname;
            String bedtype;

            try (Statement dateConflictStmt = conn.createStatement()) {
                ResultSet rs1 = dateConflictStmt.executeQuery(
                        "select *" +
                            "from sgoel05.lab7_reservations" +
                            "where room = '" + roomcode + "' and (checkin <= '" + newCheckout + "') and (checkout >= '" + newCheckin + "')");
                boolean notEmpty = rs1.next();
                if(notEmpty) {
                    System.out.println("\nThere is a date conflict with an existing reservation. Reservation cannot be completed.");
                    return;
                }
            }

            try (Statement maxOccStmt = conn.createStatement()) {
                ResultSet rs2 = maxOccStmt.executeQuery(
                        "select * " +
                            "from sgoel05.lab7_rooms where RoomCode = '" + roomcode + "'");
                rs2.next();
                int maxOcc = rs2.getInt("maxOcc");

                basePrice = rs2.getFloat("baseprice");
                roomname = rs2.getString("roomname");
                bedtype = rs2.getString("bedtype");

                if(maxOcc < adults + children) {
                    System.out.println("\nThere are too many occupants. Reservation cannot be completed.");
                    return;
                }
            }

            String insertSql = "insert into sgoel05.lab7_reservations(Code, Room, Checkin, Checkout, Rate, Lastname, Firstname, Adults, Kids)" +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
//
//			// Step 3: Start transaction
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

                // Step 4: Send SQL statement to DBMS
                pstmt.setInt(1, currCode);
                currCode++;
                pstmt.setString(2,roomcode);
                pstmt.setDate(3, java.sql.Date.valueOf(newCheckin));
                pstmt.setDate(4, java.sql.Date.valueOf(newCheckout));
                pstmt.setFloat(5, basePrice);
                pstmt.setString(6, lastname);
                pstmt.setString(7, firstname);
                pstmt.setInt(8, adults);
                pstmt.setInt(9, children);
                int rowCount = pstmt.executeUpdate();

                double totalCost = 0;
                for(LocalDate d:getDatesBetween(newCheckin, newCheckout)){
                    if(d.getDayOfWeek().getValue() == 6 || d.getDayOfWeek().getValue() == 7)
                        totalCost += basePrice * 1.1;
                    else
                        totalCost += basePrice;
                }

                // Step 5: Handle results
                System.out.println("The reservation has been made.\nReservation Summary:");
                System.out.format("\t%s, %s\n", firstname, lastname);
                System.out.format("\t%s, %s, %s\n", roomcode, roomname, bedtype);
                System.out.format("\t%s to %s\n", newCheckin.toString(), newCheckout.toString());
                System.out.format("\t%d adults\n", adults);
                System.out.format("\t%d children\n", children);
                System.out.format("\tTotal cost: $%f\n", totalCost);


                // Step 6: Commit or rollback transaction
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
            }

        }
    }

    public static List<LocalDate> getDatesBetween(
            LocalDate startDate, LocalDate endDate) {

        long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        return IntStream.iterate(0, i -> i + 1)
                .limit(numOfDaysBetween)
                .mapToObj(i -> startDate.plusDays(i))
                .collect(Collectors.toList());
    }

    private void ReservationCancellation() throws SQLException {
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            // Step 2: Construct SQL statement
            Scanner scanner = new Scanner(System.in);
            System.out.println("You are now trying to cancel a reservation.");
            System.out.println("Enter the reservation code of the reservation you wish to delete: ");
            int rescode;
            try {
                rescode = Integer.parseInt(scanner.nextLine());
            }
            catch (Exception e){
                System.out.println("Input formatted incorrectly. Reservation change cancelled.");
                return;
            }

            System.out.println("Are you sure you want to delete this reservation? (y/n)");
            String response = scanner.nextLine();
            if(response.equals("n")){
                System.out.println("Reservation not deleted. Exiting Reservation Cancellation.\n");
                return;
            }
            else if(!response.equals("y")){
                System.out.println("Invalid input. Returning to main menu.\n");
                return;
            }


            try (Statement resExsistsStmt = conn.createStatement()) {
                ResultSet rs1 = resExsistsStmt.executeQuery(
                        "select * " +
                        "from sgoel05.lab7_reservations " +
                        "where code = '" + rescode + "'");
                boolean notEmpty = rs1.next();
                if(!notEmpty) {
                    System.out.println("\nThere is no reservation with that code. Exiting reservation cancellation.");
                    return;
                }
            }

            String updateSql = "delete from lab7_reservations where code = ?";

            //Step 3: Start transaction
            conn.setAutoCommit(false);
//
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

                // Step 4: Send SQL statement to DBMS
                pstmt.setInt(1, rescode);
                int rowCount = pstmt.executeUpdate();

                // Step 5: Handle results
                System.out.println("The reservation with code " + rescode + " has been deleted.");

                // Step 6: Commit or rollback transaction
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
            }
        }
    }

    private void RevenueSummary() throws SQLException {
        try (Connection conn = DriverManager.getConnection(System.getenv("HP_JDBC_URL"),
                System.getenv("HP_JDBC_USER"),
                System.getenv("HP_JDBC_PW"))) {
            System.out.println("Revenue Summary:");

            HashMap<String, Double[]> revenues = new HashMap<>();

            try (Statement revenueStmt = conn.createStatement()) {
                ResultSet rs = revenueStmt.executeQuery(
                        "select room, month(checkout) as month, sum(round(rate * datediff(checkin, checkout), 2)) as revenue " +
                        "from sgoel05.lab7_reservations " +
                        "group by room, month(checkout) " +
                        "order by room, month(checkout)");
                while (rs.next()) {
                    String room = rs.getString("room");
                    if(!revenues.containsKey(room)) {
                        Double[] revs = new Double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
                        revenues.put(room, revs);
                    }

                    Double[] revs = revenues.get(room);
                    Double monthRev = rs.getDouble("revenue");
                    revs[rs.getInt("month") - 1] += monthRev;
                    revs[12] += monthRev;
                    revenues.put(room, revs);
                }

                Double[] totals = new Double[]{0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
                for(Map.Entry<String, Double[]> e: revenues.entrySet()){
                    System.out.format("%s $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f\n", e.getKey(), e.getValue()[0],
                            e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4], e.getValue()[5], e.getValue()[6], e.getValue()[7],
                            e.getValue()[8], e.getValue()[9], e.getValue()[10], e.getValue()[11], e.getValue()[12]);

                    for(int i = 0; i < 13; i++){
                        totals[i] += e.getValue()[i];
                    }
                }
                System.out.format("Totals $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f $%.2f\n", totals[0],
                        totals[1], totals[2], totals[3], totals[4], totals[5], totals[6], totals[7],
                        totals[8], totals[9], totals[10], totals[11], totals[12]);
            }
        }

    }


}
