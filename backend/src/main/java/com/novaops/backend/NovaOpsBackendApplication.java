package com.novaops.backend;

import com.novaops.backend.common.config.SecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan(basePackages = {
    "com.novaops.backend.auth.mapper",
    "com.novaops.backend.ticket.mapper",
    "com.novaops.backend.kb.mapper",
    "com.novaops.backend.agent.mapper"
})
@EnableConfigurationProperties(SecurityProperties.class)
@EnableAsync
public class NovaOpsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(NovaOpsBackendApplication.class, args);
  }
}
