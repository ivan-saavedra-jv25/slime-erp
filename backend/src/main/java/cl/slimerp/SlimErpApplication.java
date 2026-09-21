package cl.slimerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlimErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlimErpApplication.class, args);
    }
}
