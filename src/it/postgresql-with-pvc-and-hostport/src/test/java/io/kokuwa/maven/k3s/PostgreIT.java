package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

public class PostgreIT {

	@Test
	void test() throws SQLException {

		var url = "jdbc:postgresql://" + System.getenv().getOrDefault("DOCKER_HOST_IP", "127.0.0.1") + ":5432/data";
		var username = "data_owner";
		var password = "changeMe";
		var connection = DriverManager.getConnection(url, username, password);

		var resultSet = connection.createStatement().executeQuery("SELECT 1");
		assertTrue(resultSet.next(), "next");
		assertEquals(1, resultSet.getInt(1), "value");
	}
}
