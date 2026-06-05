package com.api.bizplay_conversational.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Trip Plan Drafts", description = "Manage business trip plan drafts")
@RestController
@RequestMapping("/api/v1/trip-plan-drafts")
@RequiredArgsConstructor
public class TripPlanDraftController {
}
