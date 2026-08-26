package com.novaops.backend.kb.service;
import com.novaops.backend.kb.dto.RetrievalResult;
public interface KbRetrievalService { RetrievalResult retrieve(String query,int topK,double minScore); }
