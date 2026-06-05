package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseSection {
    private UUID id;
    private Plan plan;
    private String sectionCode;
    private String type;
    private List<ExpenseSectionAttachment> attachments = new ArrayList<>();
    private List<ExpenseDetail> details = new ArrayList<>();
    private LocalDateTime createdAt;
}
