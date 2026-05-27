package com.api.bizplay_conversational.service.planService;

import com.api.bizplay_conversational.model.entity.Plan;
import com.api.bizplay_conversational.model.entity.PlanAttachment;
import com.api.bizplay_conversational.model.entity.PlanTraveler;
import com.api.bizplay_conversational.model.request.PlanAttachmentRequest;
import com.api.bizplay_conversational.model.request.PlanCreateRequest;
import com.api.bizplay_conversational.model.request.PlanTravelerRequest;
import com.api.bizplay_conversational.model.request.TripInformationRequest;
import com.api.bizplay_conversational.model.response.PlanAttachmentResponse;
import com.api.bizplay_conversational.model.response.PlanResponse;
import com.api.bizplay_conversational.model.response.PlanTravelerResponse;
import com.api.bizplay_conversational.repository.PlanRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanServiceImple implements PlanService {

    private final PlanRepo planRepo;

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
            PlanTraveler traveler = toTraveler(travelerRequest);
            traveler.setPlan(plan);
            plan.getTravelers().add(traveler);
        }

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

    private PlanTraveler toTraveler(PlanTravelerRequest request) {
        PlanTraveler traveler = new PlanTraveler();
        traveler.setName(request.getName());
        traveler.setDepartment(request.getDepartment());
        traveler.setPosition(request.getPosition());
        traveler.setOrigin(request.getOrigin());
        traveler.setDestination(request.getDestination());
        traveler.setReturnPoint(request.getReturnPoint());
        return traveler;
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
                        .name(traveler.getName())
                        .department(traveler.getDepartment())
                        .position(traveler.getPosition())
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
}
