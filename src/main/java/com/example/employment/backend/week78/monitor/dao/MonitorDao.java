package com.example.employment.backend.week78.monitor.dao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MonitorDao {
    private final DataSource dataSource;
    private final ConnectionFactory rabbit;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final URI receiverHealth;

    public MonitorDao(DataSource dataSource, ConnectionFactory rabbit,
            @Value("${app.monitor.receiver-health-url:http://127.0.0.1:18080/api/v1/health}")
            String receiverHealthUrl) {
        this.dataSource = dataSource;
        this.rabbit = rabbit;
        this.receiverHealth = URI.create(receiverHealthUrl);
    }

    public boolean mysql() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (var result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    public boolean rabbitmq() {
        var connection = rabbit.createConnection();
        return connection.isOpen();
    }

    public boolean receiver() throws Exception {
        var request = HttpRequest.newBuilder(receiverHealth)
                .timeout(Duration.ofSeconds(2)).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 && response.body().contains("\"UP\"");
    }
}
