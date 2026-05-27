package com.api.bizplay_conversational.service.planService;

import com.api.bizplay_conversational.model.entity.Plan;
import com.api.bizplay_conversational.model.entity.PlanAttachment;
import com.api.bizplay_conversational.model.entity.PlanTraveler;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.ExpenseDetail;
import com.api.bizplay_conversational.model.entity.ExpenseSection;
import com.api.bizplay_conversational.model.entity.ExpenseSectionAttachment;
import com.api.bizplay_conversational.model.entity.Staff;
import com.api.bizplay_conversational.model.request.ExpenseDetailRequest;
import com.api.bizplay_conversational.model.request.ExpenseSectionRequest;
import com.api.bizplay_conversational.model.request.PlanAttachmentRequest;
import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.request.PlanTravelerRequest;
import com.api.bizplay_conversational.model.request.TripInformationRequest;
import com.api.bizplay_conversational.model.response.PlanAttachmentResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.api.bizplay_conversational.repository.DepartmentRepo;
import com.api.bizplay_conversational.repository.PlanRepo;
import com.api.bizplay_conversational.repository.StaffRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanServiceImple implements PlanService {

    private static final String DEFAULT_DEPARTMENT_NAME = "Unassigned";
    private static final String DEFAULT_STAFF_NAME = "Unknown Staff";
    private static final String SECTION_COST = "COST";
    private static final String SECTION_TRANSPORTATION = "TRANSPORTATION";
    private static final String SECTION_ETC = "ETC";

    private final PlanRepo planRepo;
    private final DepartmentRepo departmentRepo;
    private final StaffRepo staffRepo;

    @Override
    @Transactional
    public PlanResponse create(PlanCreateRequest request) {
        TripInformationRequest trip = request.getTripInformation();

        Plan plan = new Plan();
        plan.setCorpNo(request.getCorpNo());
        plan.setPlanType(request.getPlanType());
        plan.setPurpose(trip.getPurpose());
        plan.setBusinessPeriod(trip.getBusinessPeriod());
        plan.setDestination(trip.getDestination());
        plan.setTitle(trip.getTitle());
        plan.setContent(trip.getContent());
        plan.setBusinessTripClassification(trip.getBusinessTripClassification());

        LocalDate[] dates = parseBusinessPeriod(trip.getBusinessPeriod());
        plan.setBusinessStartDate(dates[0]);
        plan.setBusinessEndDate(dates[1]);

        if (request.getAttachments() != null) {
            for (PlanAttachmentRequest attachmentRequest : request.getAttachments()) {
                PlanAttachment attachment = toAttachment(attachmentRequest);
                attachment.setPlan(plan);
                plan.getAttachments().add(attachment);
            }
        }

        for (PlanTravelerRequest travelerRequest : trip.getTravelers()) {
            PlanTraveler traveler = toTraveler(request.getCorpNo(), travelerRequest);
            traveler.setPlan(plan);
            plan.getTravelers().add(traveler);
        }

        addExpenseSection(plan, SECTION_COST, request.getCostInformation());
        addExpenseSection(plan, SECTION_TRANSPORTATION, request.getTransportationInformation());
        addExpenseSection(plan, SECTION_ETC, request.getEtc());

        return toResponse(planRepo.save(plan));
    }

    private PlanAttachment toAttachment(PlanAttachmentRequest request) {
        String type = requireNormalizedType(request.getType());

        PlanAttachment attachment = new PlanAttachment();
        attachment.setType(type);

        if ("File".equals(type)) {
            if (request.getFileId() == null || request.getFileId().isBlank()) {
                throw new IllegalArgumentException("FileID is required when attachment Type is File.");
            }
            attachment.setFileId(request.getFileId());
            return attachment;
        }

        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("URL is required when attachment Type is URL.");
        }
        attachment.setUrl(request.getUrl());
        return attachment;
    }

    private String requireNormalizedType(String type) {
        if ("file".equalsIgnoreCase(type)) {
            return "File";
        }
        if ("url".equalsIgnoreCase(type)) {
            return "URL";
        }
        throw new IllegalArgumentException("Attachment Type must be File or URL.");
    }

    private ExpenseSectionAttachment toExpenseSectionAttachment(PlanAttachmentRequest request) {
        String type = requireNormalizedType(request.getType());

        ExpenseSectionAttachment attachment = new ExpenseSectionAttachment();
        attachment.setType(type);

        if ("File".equals(type)) {
            if (request.getFileId() == null || request.getFileId().isBlank()) {
                throw new IllegalArgumentException("FileID is required when attachment Type is File.");
            }
            attachment.setFileId(request.getFileId());
            return attachment;
        }

        if (request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("URL is required when attachment Type is URL.");
        }
        attachment.setUrl(request.getUrl());
        return attachment;
    }

    private PlanTraveler toTraveler(String corpNo, PlanTravelerRequest request) {
        Department department = findOrCreateDepartment(corpNo, request.getDepartment());
        Staff staff = findOrCreateStaff(corpNo, request, department);

        PlanTraveler traveler = new PlanTraveler();
        traveler.setStaff(staff);
        traveler.setOrigin(request.getOrigin());
        traveler.setDestination(request.getDestination());
        traveler.setReturnPoint(request.getReturnPoint());
        return traveler;
    }

    private Department findOrCreateDepartment(String corpNo, String departmentName) {
        String normalizedName = normalizeBlank(departmentName);
        if (normalizedName == null) {
            normalizedName = DEFAULT_DEPARTMENT_NAME;
        }

        return departmentRepo.findByCorpNoAndName(corpNo, normalizedName)
                .orElseGet(() -> {
                    Department department = new Department();
                    department.setCorpNo(corpNo);
                    department.setName(normalizedName);
                    return departmentRepo.save(department);
                });
    }

    private Staff findOrCreateStaff(String corpNo, PlanTravelerRequest request, Department department) {
        String name = normalizeBlank(request.getName());
        if (name == null) {
            name = DEFAULT_STAFF_NAME;
        }
        String position = normalizeBlank(request.getPosition());

        return staffRepo.findByCorpNoAndNameAndDepartmentAndPosition(corpNo, name, department, position)
                .orElseGet(() -> {
                    Staff staff = new Staff();
                    staff.setCorpNo(corpNo);
                    staff.setName(name);
                    staff.setDepartment(department);
                    staff.setPosition(position);
                    return staffRepo.save(staff);
                });
    }

    private void addExpenseSection(Plan plan, String sectionCode, ExpenseSectionRequest request) {
        if (request == null) {
            return;
        }

        ExpenseSection section = new ExpenseSection();
        section.setPlan(plan);
        section.setSectionCode(sectionCode);
        section.setType(normalizeBlank(request.getType()));

        if (request.getAttachments() != null) {
            for (PlanAttachmentRequest attachmentRequest : request.getAttachments()) {
                ExpenseSectionAttachment attachment = toExpenseSectionAttachment(attachmentRequest);
                attachment.setSection(section);
                section.getAttachments().add(attachment);
            }
        }

        if (request.getDetails() != null) {
            for (ExpenseDetailRequest detailRequest : request.getDetails()) {
                ExpenseDetail detail = toExpenseDetail(plan.getCorpNo(), detailRequest);
                detail.setSection(section);
                section.getDetails().add(detail);
            }
        }

        plan.getExpenseSections().add(section);
    }

    private ExpenseDetail toExpenseDetail(String corpNo, ExpenseDetailRequest request) {
        ExpenseDetail detail = new ExpenseDetail();
        detail.setSequenceNo(request.getSequenceNo());
        detail.setTaxCode(normalizeBlank(request.getTaxCode()));
        detail.setType(normalizeBlank(request.getType()));
        detail.setUsePurpose(normalizeBlank(request.getUsePurpose()));
        detail.setAccount(normalizeBlank(request.getAccount()));
        detail.setBudgetDepartment(findOrCreateDepartment(corpNo, request.getBudgetDepartment()));
        detail.setTransportationMethod(normalizeBlank(request.getTransportationMethod()));
        detail.setOrigin(normalizeBlank(request.getOrigin()));
        detail.setDestination(normalizeBlank(request.getDestination()));
        detail.setStartDate(request.getStartDate());
        detail.setEndDate(request.getEndDate());
        detail.setUsageDate(request.getUsageDate());
        detail.setProofDate(request.getProofDate());
        detail.setDescription(normalizeBlank(request.getDescription()));
        detail.setVendor(normalizeBlank(request.getVendor()));
        detail.setSupplyPrice(request.getSupplyPrice());
        detail.setTax(request.getTax());
        detail.setAmountUsed(request.getAmountUsed());
        detail.setRegulatedAmount(request.getRegulatedAmount());
        detail.setApplicationAmountReasonForExcess(normalizeBlank(request.getApplicationAmountReasonForExcess()));
        detail.setBriefs(normalizeBlank(request.getBriefs()));
        detail.setNote(normalizeBlank(request.getNote()));
        detail.setApprovalNumber(normalizeBlank(request.getApprovalNumber()));
        return detail;
    }

    private LocalDate[] parseBusinessPeriod(String businessPeriod) {
        String[] parts = businessPeriod.split("(?i)\\s+to\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("BusinessPeriod must use format 'yyyy-MM-dd to yyyy-MM-dd'.");
        }

        LocalDate start = LocalDate.parse(parts[0].trim());
        LocalDate end = LocalDate.parse(parts[1].trim());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("BusinessPeriod end date must be on or after start date.");
        }
        return new LocalDate[]{start, end};
    }

    private PlanResponse toResponse(Plan plan) {
        List<PlanAttachmentResponse> attachments = plan.getAttachments().stream()
                .map(attachment -> PlanAttachmentResponse.builder()
                        .id(attachment.getId())
                        .type(attachment.getType())
                        .fileId(attachment.getFileId())
                        .url(attachment.getUrl())
                        .build())
                .toList();

        List<PlanTravelerResponse> travelers = plan.getTravelers().stream()
                .map(traveler -> PlanTravelerResponse.builder()
                        .id(traveler.getId())
                        .name(traveler.getStaff().getName())
                        .department(traveler.getStaff().getDepartment() != null
                                ? traveler.getStaff().getDepartment().getName()
                                : null)
                        .position(traveler.getStaff().getPosition())
                        .origin(traveler.getOrigin())
                        .destination(traveler.getDestination())
                        .returnPoint(traveler.getReturnPoint())
                        .build())
                .toList();

        return PlanResponse.builder()
                .id(plan.getId())
                .corpNo(plan.getCorpNo())
                .planType(plan.getPlanType())
                .purpose(plan.getPurpose())
                .businessPeriod(plan.getBusinessPeriod())
                .businessStartDate(plan.getBusinessStartDate())
                .businessEndDate(plan.getBusinessEndDate())
                .destination(plan.getDestination())
                .title(plan.getTitle())
                .content(plan.getContent())
                .businessTripClassification(plan.getBusinessTripClassification())
                .attachments(attachments)
                .travelers(travelers)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
