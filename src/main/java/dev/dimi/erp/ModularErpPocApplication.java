package dev.dimi.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ModularErpPocApplication {

  public static void main(String[] args) {
    SpringApplication.run(ModularErpPocApplication.class, args);
  }
}
