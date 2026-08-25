package com.novaops.backend.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegisterRequestValidationTest {

  private static jakarta.validation.ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void rejectsFiveCharacterPassword() {
    assertThat(passwordViolations(5)).isEqualTo(1);
  }

  @Test
  void acceptsSixCharacterPassword() {
    assertThat(passwordViolations(6)).isZero();
  }

  @Test
  void acceptsSeventyTwoCharacterPassword() {
    assertThat(passwordViolations(72)).isZero();
  }

  @Test
  void rejectsSeventyThreeCharacterPassword() {
    assertThat(passwordViolations(73)).isEqualTo(1);
  }

  private long passwordViolations(int length) {
    RegisterRequest request = new RegisterRequest();
    request.setInvitationToken("token");
    request.setUsername("newbie");
    request.setDisplayName("New User");
    request.setPassword("x".repeat(length));
    return validator.validate(request).stream()
        .filter(violation -> "password".equals(violation.getPropertyPath().toString()))
        .count();
  }
}
