package com.novaops.backend.kb.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public class UpdateDocumentTitleRequest { @NotBlank @Size(max=255) private String title; public String getTitle(){return title;} public void setTitle(String v){title=v;} }
