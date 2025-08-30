package auto.parto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource(value = "classpath:application.yml", ignoreResourceNotFound = true)
public class AutoPartoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutoPartoApplication.class, args);
	}

}
