package dev.dimi.erp;

import org.springframework.boot.SpringApplication;

public class TestModularErpPocApplication {

  public static void main(String[] args) {
    SpringApplication.from(ModularErpPocApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
