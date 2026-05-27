package com.api.bizplay_conversational.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversational_trip_plan_expense_details")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExpenseDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ExpenseSection section;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "detail_type", length = 100)
    private String type;

    @Column(name = "use_purpose", length = 100)
    private String usePurpose;

    @Column(length = 100)
    private String account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_department_id")
    private Department budgetDepartment;

    @Column(name = "transportation_method", length = 100)
    private String transportationMethod;

    @Column(name = "origin_location", length = 255)
    private String origin;

    @Column(name = "destination_location", length = 255)
    private String destination;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "proof_date")
    private LocalDate proofDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String vendor;

    @Column(name = "supply_price", precision = 15, scale = 2)
    private BigDecimal supplyPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal tax;

    @Column(name = "amount_used", precision = 15, scale = 2)
    private BigDecimal amountUsed;

    @Column(name = "regulated_amount", precision = 15, scale = 2)
    private BigDecimal regulatedAmount;

    @Column(name = "excess_reason", columnDefinition = "TEXT")
    private String applicationAmountReasonForExcess;

    @Column(columnDefinition = "TEXT")
    private String briefs;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "approval_number", length = 100)
    private String approvalNumber;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
