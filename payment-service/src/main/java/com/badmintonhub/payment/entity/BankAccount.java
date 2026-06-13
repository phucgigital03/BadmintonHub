package com.badmintonhub.payment.entity;

import com.badmintonhub.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

/**
 * A bank account the venue receives transfers into. Its {@code qrImageUrl} (a pre-generated bank QR)
 * and details are shown on the payment screen. Single-club model: typically one {@code isActive} row;
 * {@code initiate} attaches the active account to each new payment.
 */
@Entity
@Table(
        name = "bank_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_bank_accounts_number", columnNames = "account_number")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class BankAccount extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "qr_image_url")
    private String qrImageUrl; // Cloudinary URL of the bank QR image

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // only active accounts are shown on the payment screen
}
