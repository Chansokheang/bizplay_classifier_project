package com.api.bizplay_conversational.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Staff {
    private UUID id;
    private Department department;
    private String name;
    private String position;
    private LocalDateTime createdDate;
}
