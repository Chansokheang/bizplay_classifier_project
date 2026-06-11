package com.api.bizplay_conversational.service.departmentService;

import com.api.bizplay_conversational.model.request.DepartmentRequest;
import com.api.bizplay_conversational.model.response.DepartmentResponse;

import java.util.List;

/** CRUD management for conversational_department (corp-scoped). */
public interface DepartmentService {

    DepartmentResponse create(DepartmentRequest request);

    List<DepartmentResponse> getByCorpNo(String corpNo);

    DepartmentResponse getById(String id);

    DepartmentResponse update(String id, DepartmentRequest request);

    void deleteById(String id);
}
