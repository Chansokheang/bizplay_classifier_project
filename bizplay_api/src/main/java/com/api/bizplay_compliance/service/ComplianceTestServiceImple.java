package com.api.bizplay_compliance.service;

import org.springframework.stereotype.Service;

@Service
public class ComplianceTestServiceImple implements ComplianceTestService {
    @Override
    public String ping() {
        return "bizplay_compliance service is reachable";
    }
}
