package com.novaops.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.novaops.backend.auth.controller.AuthController;
import com.novaops.backend.auth.dto.RegisterRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class SystemControllerContractTest {

  @Test
  void exposesPlatformAdminTenantAndInvitationEndpoints() {
    assertThatCode(() -> {
      Class<?> controller = Class.forName("com.novaops.backend.auth.controller.SystemController");
      assertThat(controller.getAnnotation(RequestMapping.class).value()).containsExactly("/api/system");
      assertMapping(controller.getMethod("tenants"), GetMapping.class, "/tenants");
      assertMapping(controller.getMethod("createTenant", com.novaops.backend.auth.dto.CreateTenantRequest.class), PostMapping.class, "/tenants");
      assertMapping(controller.getMethod("invitations"), GetMapping.class, "/invitations");
      assertMapping(controller.getMethod("createInvitation", com.novaops.backend.auth.dto.CreateInvitationRequest.class), PostMapping.class, "/invitations");
    }).doesNotThrowAnyException();
  }

  @Test
  void exposesPublicInvitationRegistrationEndpoint() {
    assertThatCode(() -> assertMapping(
        AuthController.class.getMethod("register", RegisterRequest.class), PostMapping.class, "/register"))
        .doesNotThrowAnyException();
  }

  private void assertMapping(Method method, Class<? extends java.lang.annotation.Annotation> annotationType, String path) {
    String[] values;
    if (annotationType == GetMapping.class) {
      values = method.getAnnotation(GetMapping.class).value();
    } else {
      values = method.getAnnotation(PostMapping.class).value();
    }
    assertThat(Arrays.asList(values)).containsExactly(path);
  }
}
