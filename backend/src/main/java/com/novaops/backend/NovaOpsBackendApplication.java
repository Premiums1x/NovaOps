package com.novaops.backend;

import com.novaops.backend.common.config.SecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan(basePackages = {
    "com.novaops.backend.auth.mapper",
    "com.novaops.backend.ticket.mapper"
})
@EnableConfigurationProperties(SecurityProperties.class)
public class NovaOpsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(NovaOpsBackendApplication.class, args);
  }
}
